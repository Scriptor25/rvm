package io.scriptor;

import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.ObjectIndexedContainer;
import io.scriptor.arg.FlagPayload;
import io.scriptor.arg.LoadPayload;
import io.scriptor.arg.MemoryPayload;
import io.scriptor.arg.Template;
import io.scriptor.elf.Header;
import io.scriptor.elf.Identity;
import io.scriptor.elf.ProgramHeader;
import io.scriptor.elf.SectionHeader;
import io.scriptor.gdb.GDBStub;
import io.scriptor.impl.MachineImpl;
import io.scriptor.impl.device.Memory;
import io.scriptor.isa.Registry;
import io.scriptor.machine.Machine;
import io.scriptor.util.ChannelInputStream;
import io.scriptor.util.ExtendedInputStream;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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

    public static void main(final @NotNull String @NotNull [] args) throws InterruptedException {

        final var logThread = new Thread(Log::handle, "rvm-log");
        logThread.start();

        try {
            final var payloads = Template.parse(args);

            final var loads = payloads.getAll(TEMPLATE_LOAD, LoadPayload.class);

            final var memory = payloads.get(TEMPLATE_MEMORY, MemoryPayload.class)
                                       .stream()
                                       .mapToInt(MemoryPayload::size)
                                       .findAny()
                                       .orElseGet(() -> MiB(32));

            final var debug = payloads.get(TEMPLATE_DEBUG, FlagPayload.class).isPresent();

            final var machine = init(loads, memory);

            run(machine, debug);
        } catch (final Exception e) {
            Log.error("%s", e);
        }

        Log.shutdown();
        logThread.join();
    }

    private static @NotNull Machine init(
            final LoadPayload @NotNull [] loads,
            final int memory
    ) {
        final ObjectIndexedContainer<String> isa = new ObjectArrayList<>();
        isa.add("types");

        read("index.txt", stream -> {
            final var reader = new BufferedReader(new InputStreamReader(stream));
            reader.lines()
                  .map(String::trim)
                  .filter(not(String::isEmpty))
                  .filter(line -> line.endsWith(".isa"))
                  .forEach(isa::add);
        });

        final var registry = Registry.getInstance();
        for (final var name : isa) {
            read(name.value, registry::parse);
        }

        final Machine machine = new MachineImpl(memory, ByteOrder.LITTLE_ENDIAN, 1);

        for (final var payload : loads) {
            load(machine, payload.filename(), payload.offset());
        }

        return machine;
    }

    private static void run(final @NotNull Machine machine, final boolean debug) {
        machine.reset();

        if (debug) {
            try (final var gdb = new GDBStub(machine, 1234)) {
                while (gdb.step()) {
                    try {
                        machine.step();
                    } catch (final Exception e) {
                        machine.pause();
                        Log.inject(machine::dump);
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
            Log.inject(machine::dump);
            Log.error("machine exception: %s", e);
        }
    }

    private static void load(
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

                machine.entry(header.entry() + offset);
                machine.offset(offset);
                machine.device(Memory.class, memory -> {
                    memory.buffer().order(identity.order());
                });

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

                for (final var ph : phtab) {
                    if (ph.type() == 0x01) {
                        stream.seek(ph.offset());
                        machine.segment(stream, ph.paddr() + offset, (int) ph.filesz(), (int) ph.memsz());
                    }
                }
            } else {
                stream.seek(0L);
                machine.segment(stream, offset, (int) stream.size(), (int) stream.size());
            }
        } catch (final IOException e) {
            Log.error("failed to load file '%s' (offset %x): %s", filename, offset, e);
            throw new RuntimeException(e);
        }
    }

    private static void dump(final @NotNull ByteBuffer buffer) {

        final var CHUNK = 0x10;

        var allZero      = false;
        var allZeroBegin = 0L;

        while (buffer.hasRemaining()) {

            final var begin = buffer.position();
            final var chunk = Math.min(buffer.remaining(), CHUNK);

            final var bytes = new byte[chunk];
            buffer.get(bytes);

            boolean allZeroP = true;
            for (int j = 0; j < chunk; ++j)
                if (bytes[j] != 0) {
                    allZeroP = false;
                    break;
                }

            if (allZero && !allZeroP) {
                allZero = false;
                System.out.printf("%08x - %08x%n", allZeroBegin, begin - 1);
            } else if (!allZero && allZeroP) {
                allZero = true;
                allZeroBegin = begin;
                continue;
            } else if (allZero) {
                continue;
            }

            System.out.printf("%08x |", begin);

            for (int j = 0; j < chunk; ++j) {
                System.out.printf(" %02X", bytes[j]);
            }
            for (int j = chunk; j < CHUNK; ++j) {
                System.out.print(" 00");
            }

            System.out.print(" | ");

            for (int j = 0; j < chunk; ++j) {
                System.out.print(bytes[j] >= 0x20 ? (char) bytes[j] : '.');
            }
            for (int j = chunk; j < CHUNK; ++j) {
                System.out.print('.');
            }

            System.out.println();
        }
        System.out.println("(END)");
    }

    private Main() {
    }
}
