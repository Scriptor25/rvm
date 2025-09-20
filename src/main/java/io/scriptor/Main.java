package io.scriptor;

import io.scriptor.elf.*;
import io.scriptor.impl.Machine64;
import io.scriptor.io.FileStream;
import io.scriptor.io.IOStream;
import io.scriptor.isa.Registry;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.scriptor.elf.ELF.readSymbols;
import static io.scriptor.util.ByteUtil.readString;
import static io.scriptor.util.Unit.*;

public final class Main {

    // https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
    // https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
    // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
    // https://wiki.osdev.org/RISC-V_Bare_Bones

    // rvm
    //  --firmware, -f=<filename>
    //  --executable, -x=<filename>
    //  --memory, -m=<size:unit>

    public static void main(final @NotNull String @NotNull [] args) {

        final List<Symbol> filenames = new ArrayList<>();

        long memory = MiB(32);

        for (final var arg : args) {
            final var split = arg.indexOf('=') + 1;

            if (arg.startsWith("--file=") || arg.startsWith("-f=")) {
                final var slice = arg.substring(split);
                final var colon = slice.indexOf(':');

                var filename = slice.substring(0, colon);
                try {
                    filename = new File(filename).getCanonicalPath();
                } catch (final IOException e) {
                    Log.error("failed to get canonical path for filename '%s': %s", filename, e);
                    return;
                }

                final var value = slice.substring(colon + 1);
                final var addr  = Long.parseUnsignedLong(value, 0x10);

                filenames.add(new Symbol(addr, filename));
                continue;
            }

            if (arg.startsWith("--memory=") || arg.startsWith("-m=")) {
                final var slice = arg.substring(split);

                final String value;
                final char   unit;
                if (slice.contains(":")) {
                    final var colon = slice.indexOf(':');
                    value = slice.substring(0, colon);
                    unit = slice.charAt(colon + 1);
                } else {
                    value = slice;
                    unit = 'M';
                }
                final var v = Long.parseUnsignedLong(value);
                memory = switch (unit) {
                    case 'K' -> KiB(v);
                    case 'M' -> MiB(v);
                    case 'G' -> GiB(v);
                    default -> throw new IllegalArgumentException("unit '%c'".formatted(unit));
                };

                continue;
            }

            throw new IllegalArgumentException(arg);
        }

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

        for (final var file : filenames) {
            if (loadFilename(machine, file.name(), file.addr())) {
                return;
            }
        }

        try {
            machine.reset();
            while (machine.step())
                ;
            machine.dump(System.err);
        } catch (final Exception e) {
            machine.dump(System.err);
            Log.warn("machine exception: %s", e);
        }
    }

    private static boolean loadFilename(
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
            return false;
        } catch (final IOException e) {
            e.printStackTrace(System.err);
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
