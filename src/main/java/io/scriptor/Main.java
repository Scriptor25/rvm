package io.scriptor;

import io.scriptor.arg.*;
import io.scriptor.elf.Header;
import io.scriptor.elf.Identity;
import io.scriptor.elf.ProgramHeader;
import io.scriptor.elf.SectionHeader;
import io.scriptor.gdb.GDBStub;
import io.scriptor.impl.MachineImpl;
import io.scriptor.isa.Registry;
import io.scriptor.machine.Machine;
import io.scriptor.util.ChannelInputStream;
import io.scriptor.util.ExtendedInputStream;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.scriptor.arg.Template.*;
import static io.scriptor.elf.ELF.readSymbols;
import static io.scriptor.util.ByteUtil.readString;
import static io.scriptor.util.Resource.read;
import static io.scriptor.util.Unit.MiB;
import static java.util.function.Predicate.not;

public final class Main {

    // https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
    // https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
    // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
    // https://wiki.osdev.org/RISC-V_Bare_Bones

    public static void main(final @NotNull String @NotNull [] args) {

        final var logThread = new Thread(Log::handle, "rvm-log");
        logThread.start();

        try {
            final var payloads = Template.parse(args);

            final var loads     = payloads.getAll(TEMPLATE_LOAD, LoadPayload.class);
            final var registers = payloads.getAll(TEMPLATE_REGISTER, RegisterPayload.class);

            final var memory = payloads.get(TEMPLATE_MEMORY, MemoryPayload.class)
                                       .stream()
                                       .mapToInt(MemoryPayload::size)
                                       .findAny()
                                       .orElseGet(() -> MiB(32));

            final var debug = payloads.get(TEMPLATE_DEBUG, FlagPayload.class).isPresent();

            run(loads, registers, memory, debug);
        } catch (final Exception e) {
            Log.error("%s", e);
        }

        logThread.interrupt();
    }

    private static void run(
            final @NotNull List<LoadPayload> loads,
            final @NotNull List<RegisterPayload> registers,
            final int memory,
            final boolean debug
    ) {

        final List<String> isa = new ArrayList<>();
        isa.add("types");

        if (read("index.txt", stream -> {
            final var reader = new BufferedReader(new InputStreamReader(stream));
            reader.lines()
                  .map(String::trim)
                  .filter(not(String::isEmpty))
                  .filter(line -> line.endsWith(".isa"))
                  .forEach(isa::add);
        })) {
            return;
        }

        final var registry = Registry.getInstance();
        for (final var name : isa) {
            if (read(name, registry::parse)) {
                return;
            }
        }

        final Machine machine = new MachineImpl(memory, ByteOrder.LITTLE_ENDIAN, 1);

        for (final var payload : loads) {
            if (load(machine, payload.filename(), payload.offset())) {
                return;
            }
        }

        for (final var payload : registers) {
            machine.register(payload.register(), payload.value());
        }

        machine.reset();

        if (debug) {
            try (final var gdb = new GDBStub(machine, 1234)) {
                while (gdb.step()) {
                    try {
                        machine.step();
                    } catch (final Exception e) {
                        machine.pause();
                        machine.dump(System.err);
                        Log.error("machine exception: %s", e);

                        gdb.stop(-1, 0x00);
                    }
                }
            } catch (final IOException e) {
                Log.error("failed to create gdb stub: %s", e);
            }
            return;
        }

        try {
            machine.spin();
            while (true) {
                machine.step();
            }
        } catch (final Exception e) {
            machine.pause();
            machine.dump(System.err);
            Log.error("machine exception: %s", e);
        }
    }

    private static boolean load(
            final @NotNull Machine machine,
            final @NotNull String filename,
            final long offset
    ) {
        try (final ExtendedInputStream stream = new ChannelInputStream(filename)) {
            final var buffer = ByteBuffer.allocateDirect(0x10);
            stream.read(buffer);
            final var identity = Identity.read(buffer.flip());

            final var elf = Arrays.compare(identity.magic(), new byte[] { 0x7F, 0x45, 0x4C, 0x46 }) == 0;

            if (elf) {
                final var header = Header.read(identity, stream);

                // System.out.printf("identity: %s%n", identity);
                // System.out.printf("header:   %s%n", header);

                machine.entry(header.entry() + offset);
                machine.offset(offset);
                machine.order(identity.order());

                final var phtab = new ProgramHeader[header.phnum()];
                final var shtab = new SectionHeader[header.shnum()];

                for (int i = 0; i < header.phnum(); ++i) {
                    stream.seek(header.phoff() + i * header.phentsize());
                    phtab[i] = ProgramHeader.read(identity, stream);
                }

                for (int i = 0; i < header.shnum(); ++i) {
                    stream.seek(header.shoff() + i * header.shentsize());
                    shtab[i] = SectionHeader.read(identity, stream);
                }

                final var shstrtab = shtab[header.shstrndx()];

                SectionHeader symtab = null, dynsym = null, strtab = null, dynstr = null;
                for (final var sh : shtab) {
                    stream.seek(shstrtab.offset() + sh.name());
                    final var name = readString(stream);
                    switch (name) {
                        case ".symtab" -> symtab = sh;
                        case ".dynsym" -> dynsym = sh;
                        case ".strtab" -> strtab = sh;
                        case ".dynstr" -> dynstr = sh;
                    }
                }

                final var symbols = machine.symbols();
                if (symtab != null && strtab != null) {
                    readSymbols(identity, stream, symtab, strtab, symbols, offset);
                }
                if (dynsym != null && dynstr != null) {
                    readSymbols(identity, stream, dynsym, dynstr, symbols, offset);
                }

                // System.out.println("symbols:");
                // for (final var symbol : symbols) {
                //     System.out.println(symbol);
                // }

                for (final var ph : phtab) {
                    // System.out.printf("program header: %s%n", ph);
                    // print(stream.seek(ph.offset()), ph.offset(), ph.filesz());

                    if (ph.type() == 0x01) {
                        stream.seek(ph.offset());
                        machine.segment(stream, ph.paddr() + offset, ph.filesz(), ph.memsz());
                    }
                }

                /*for (final var sh : shtab) {
                    stream.seek(shstrtab.offset() + sh.name());
                    final var name = readString(stream);
                    System.out.printf("section header '%s': %s%n", name, sh);
                    print(stream.seek(sh.offset()), sh.offset(), sh.size());
                }*/
            } else {
                // print(stream.seek(0L), 0, stream.size());

                stream.seek(0L);
                machine.segment(stream, offset, stream.size(), stream.size());
            }
            return false;
        } catch (final IOException e) {
            Log.error("failed to load file '%s' (offset %x): %s", filename, offset, e);
            return true;
        }
    }

    private static void print(final @NotNull InputStream stream, final long offset, final long length)
            throws IOException {
        if (length == 0) {
            System.out.println("<empty>");
            return;
        }

        final var CHUNK = 0x20;

        var allZero      = false;
        var allZeroBegin = 0L;

        for (long i = 0; i < length; i += CHUNK) {

            final var chunk = (int) Math.min(length - i, CHUNK);

            final var bytes = new byte[chunk];
            final var count = stream.read(bytes);

            if (count <= 0)
                break;

            boolean allZeroP = true;
            for (int j = 0; j < count; ++j)
                if (bytes[j] != 0) {
                    allZeroP = false;
                    break;
                }

            if (allZero && !allZeroP) {
                allZero = false;
                System.out.printf("%016x - %016x%n", allZeroBegin, i - 1);
            } else if (!allZero && allZeroP) {
                allZero = true;
                allZeroBegin = i;
                continue;
            } else if (allZero) {
                continue;
            }

            System.out.printf("%016x |", offset + i);

            for (int j = 0; j < count; ++j) {
                System.out.printf(" %02x", bytes[j]);
            }
            for (int j = count; j < CHUNK; ++j) {
                System.out.print(" 00");
            }

            System.out.print(" | ");

            for (int j = 0; j < count; ++j) {
                System.out.print(bytes[j] >= 0x20 ? (char) bytes[j] : '.');
            }
            for (int j = count; j < CHUNK; ++j) {
                System.out.print('.');
            }

            System.out.println();
        }
        System.out.println("(END)");
    }

    private Main() {
    }
}
