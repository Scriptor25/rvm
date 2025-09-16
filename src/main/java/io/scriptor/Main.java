package io.scriptor;

import io.scriptor.elf.ELF_Header;
import io.scriptor.elf.ELF_ProgramHeader;
import io.scriptor.elf.ELF_SectionHeader;
import io.scriptor.impl.Machine64;
import io.scriptor.io.FileStream;
import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;

import static io.scriptor.Bytes.readString;
import static io.scriptor.Unit.MiB;

public final class Main {

    // https://riscv.github.io/riscv-isa-manual/snapshot/privileged/
    // https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/
    // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format
    // https://wiki.osdev.org/RISC-V_Bare_Bones

    private static void print(final @NotNull IOStream stream, final long offset, final long length) throws IOException {
        if (length == 0) {
            System.out.println("<empty>");
            return;
        }

        final var CHUNK = 0x20;

        for (long i = 0; i < length; i += CHUNK) {

            final var bytes = new byte[CHUNK];
            final var count = stream.read(bytes);

            if (count <= 0)
                break;

            System.out.printf("%08x |", offset + i);

            for (int j = 0; j < count; ++j)
                System.out.printf(" %02x", bytes[j]);

            System.out.print(" | ");

            for (int j = 0; j < count; ++j)
                System.out.print(bytes[j] >= 0x20 ? (char) bytes[j] : '.');

            System.out.println();
        }
        System.out.println("(END)");
    }

    public static void main(final @NotNull String[] args) {
        if (args.length != 2) {
            System.err.println("2 arguments required");
            return;
        }

        final String filename;
        try {
            filename = new File(args[0]).getCanonicalPath();
        } catch (final IOException e) {
            e.printStackTrace(System.err);
            return;
        }

        final var format = switch (args[1]) {
            case "bin" -> FileFormat.BIN;
            case "elf" -> FileFormat.ELF;
            default -> throw new NoSuchElementException("format '%s'".formatted(args[1]));
        };

        final var     layout  = new MachineLayout(32, 0x1000, 0x80000000L, MiB(4));
        final Machine machine = new Machine64(layout);

        try (final var stream = new FileStream(filename, false)) {
            switch (format) {
                case BIN -> {
                    machine.setEntry(0x80000000L);
                    machine.loadDirect(stream);
                }
                case ELF -> {
                    final var header = new ELF_Header(stream);

                    machine.setEntry(header.entry);

                    final var programHeaderTable = new ELF_ProgramHeader[header.phnum];
                    final var sectionHeaderTable = new ELF_SectionHeader[header.shnum];

                    for (int i = 0; i < header.phnum; ++i) {
                        stream.seek(header.phoff + i * header.phentsize);
                        programHeaderTable[i] = new ELF_ProgramHeader(stream, header.identity.format);
                    }

                    for (int i = 0; i < header.shnum; ++i) {
                        stream.seek(header.shoff + i * header.shentsize);
                        sectionHeaderTable[i] = new ELF_SectionHeader(stream, header.identity.format);
                    }

                    final var stringSectionHeader = sectionHeaderTable[header.shstrndx];

                    System.out.println("program headers:");
                    for (final var programHeader : programHeaderTable) {
                        System.out.println(programHeader);

                        stream.seek(programHeader.offset);
                        print(stream, programHeader.offset, programHeader.filesz);

                        if (programHeader.type == 0x01) {
                            stream.seek(programHeader.offset);
                            machine.loadSegment(stream,
                                                programHeader.paddr,
                                                programHeader.filesz);
                        }
                    }

                    System.out.println("section headers:");
                    for (final var sectionHeader : sectionHeaderTable) {
                        stream.seek(stringSectionHeader.offset + sectionHeader.name);
                        System.out.printf("%s: %s%n", readString(stream), sectionHeader);

                        stream.seek(sectionHeader.offset);
                        print(stream, sectionHeader.offset, sectionHeader.size);
                    }
                }
            }
        } catch (final IOException e) {
            e.printStackTrace(System.err);
            return;
        }

        try {
            machine.reset();
            while (machine.step())
                ;
        } catch (final Exception e) {
            machine.dump(System.err);
            e.printStackTrace(System.err);
        }
    }

    private Main() {
    }
}
