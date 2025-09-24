package io.scriptor;

import io.scriptor.arg.Template;
import io.scriptor.elf.*;
import io.scriptor.impl.Machine64;
import io.scriptor.io.FileStream;
import io.scriptor.io.IOStream;
import io.scriptor.isa.Registry;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static io.scriptor.arg.Template.TEMPLATE_LOAD;
import static io.scriptor.arg.Template.TEMPLATE_MEMORY;
import static io.scriptor.elf.ELF.readSymbols;
import static io.scriptor.util.ByteUtil.readString;
import static io.scriptor.util.Unit.MiB;

public final class Main {

    // https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
    // https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
    // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
    // https://wiki.osdev.org/RISC-V_Bare_Bones

    // rvm
    //  --file, -f=<filename[:base]>
    //  --memory, -m=<size[:unit]>

    public static void main(final @NotNull String @NotNull [] args) {

        final var templates = Template.parse(args);

        final var files = templates.getOrDefault(TEMPLATE_LOAD, List.of())
                                   .stream()
                                   .filter(Symbol.class::isInstance)
                                   .map(Symbol.class::cast)
                                   .toList();

        final var memory = templates.getOrDefault(TEMPLATE_MEMORY, List.of())
                                    .stream()
                                    .filter(Long.class::isInstance)
                                    .map(Long.class::cast)
                                    .mapToLong(Long::longValue)
                                    .findAny()
                                    .orElse(MiB(32));

        final var isa = List.of("types.isa",
                                "priv.isa",
                                "rv32i.isa",
                                "rv32m.isa",
                                "rv32a.isa",
                                "rv32f.isa",
                                "rv32d.isa",
                                "rv32q.isa",
                                "rv64i.isa",
                                "rv64m.isa",
                                "rv64a.isa",
                                "rv64f.isa",
                                "rv64d.isa",
                                "rv64q.isa",
                                "rvc.isa",
                                "zicsr.isa",
                                "zifencei.isa");

        final var registry = Registry.getInstance();

        for (final var name : isa) {
            try (final var stream = ClassLoader.getSystemResourceAsStream(name)) {
                if (stream == null)
                    throw new FileNotFoundException("resource name '%s'".formatted(name));

                registry.parse(stream);
            } catch (final IOException e) {
                Log.warn("failed to read isa definition file '%s': %s", name, e);
            }
        }

        final Machine machine = new Machine64(memory);

        for (final var file : files)
            loadFilename(machine, file.name(), file.addr());

        try {
            for (machine.reset(); machine.step(); )
                ;
        } catch (final Exception e) {
            machine.dump(System.err);
            Log.warn("machine exception: %s", e);
        }
    }

    private static void loadFilename(
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

                machine.setEntry(machine.getDRAM());
                machine.loadDirect(stream.seek(0L), 0L, stream.size(), stream.size());
            }
        } catch (final IOException e) {
            Log.warn("failed to load file '%s' (offset %x): %s", filename, offset, e);
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
