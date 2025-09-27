package io.scriptor;

import io.scriptor.arg.LoadPayload;
import io.scriptor.arg.MemoryPayload;
import io.scriptor.arg.RegisterPayload;
import io.scriptor.arg.Template;
import io.scriptor.elf.Header;
import io.scriptor.elf.Identity;
import io.scriptor.elf.ProgramHeader;
import io.scriptor.elf.SectionHeader;
import io.scriptor.impl.Machine64;
import io.scriptor.io.FileStream;
import io.scriptor.io.IOStream;
import io.scriptor.isa.Registry;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    // rvm
    //  --file, -f=<filename[:base]>
    //  --memory, -m=<size[:unit]>

    public static void main(final @NotNull String @NotNull [] args) {

        final var payloadMap = Template.parse(args);

        final var loadPayloads     = payloadMap.getAll(TEMPLATE_LOAD, LoadPayload.class);
        final var registerPayloads = payloadMap.getAll(TEMPLATE_REGISTER, RegisterPayload.class);
        final var memoryPayload = payloadMap.get(TEMPLATE_MEMORY, MemoryPayload.class)
                                            .orElseGet(() -> new MemoryPayload(MiB(32)));

        final List<String> isa = new ArrayList<>();
        isa.add("types");

        if (read("index.txt", stream -> {
            final var reader = new BufferedReader(new InputStreamReader(stream));
            reader.lines()
                  .map(String::trim)
                  .filter(not(String::isEmpty))
                  .filter(line -> line.endsWith(".isa"))
                  .forEach(isa::add);
        }))
            return;

        final var registry = Registry.getInstance();

        for (final var name : isa)
            if (read(name, registry::parse))
                return;

        final Machine machine = new Machine64(memoryPayload.size(), 1);

        for (final var payload : loadPayloads)
            if (load(machine, payload.filename(), payload.offset()))
                return;

        machine.reset();

        for (final var payload : registerPayloads)
            machine.getHarts().forEach(hart -> hart.getGPRFile().putd(payload.register(), payload.value()));

        try {
            while (true)
                machine.step();
        } catch (final Exception e) {
            machine.dump(System.err);
            Log.warn("machine exception: %s", e);
        }
    }

    private static boolean load(
            final @NotNull Machine machine,
            final @NotNull String filename,
            final long offset
    ) {
        try (final var stream = new FileStream(filename, false)) {
            final var identity = Identity.read(stream);

            final var elf = Arrays.compare(identity.magic(), new byte[] { 0x7F, 0x45, 0x4C, 0x46 }) == 0;

            if (elf) {
                final var header = Header.read(identity, stream);

                // System.out.printf("identity: %s%n", identity);
                // System.out.printf("header:   %s%n", header);

                machine.setEntry(header.entry() + offset);

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
                    final var name = readString(stream.seek(shstrtab.offset() + sh.name()));
                    switch (name) {
                        case ".symtab" -> symtab = sh;
                        case ".dynsym" -> dynsym = sh;
                        case ".strtab" -> strtab = sh;
                        case ".dynstr" -> dynstr = sh;
                    }
                }

                final var symbols = machine.getSymbols();
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
                        machine.loadSegment(stream.seek(ph.offset()),
                                            ph.paddr() + offset,
                                            ph.filesz(),
                                            ph.memsz());
                    }
                }

                // for (final var sh : shtab) {
                //     final var name = readString(stream.seek(shstrtab.offset() + sh.name()));
                //     System.out.printf("section header '%s': %s%n", name, sh);
                //     print(stream.seek(sh.offset()), sh.offset(), sh.size());
                // }
            } else {
                // print(stream.seek(0L), 0, stream.size());

                machine.loadSegment(stream.seek(0L), offset, stream.size(), stream.size());
            }
            return false;
        } catch (final IOException e) {
            Log.warn("failed to load file '%s' (offset %x): %s", filename, offset, e);
            return true;
        }
    }

    private static void print(final @NotNull IOStream stream, final long offset, final long length) throws IOException {
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
            final var count = (int) stream.read(bytes);

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
