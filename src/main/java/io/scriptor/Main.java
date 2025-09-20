package io.scriptor;

import io.scriptor.elf.ELF_Header;
import io.scriptor.elf.ELF_Identity;
import io.scriptor.elf.ELF_ProgramHeader;
import io.scriptor.elf.ELF_SectionHeader;
import io.scriptor.impl.Machine64;
import io.scriptor.io.FileStream;
import io.scriptor.io.IOStream;
import io.scriptor.isa.Registry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static io.scriptor.ByteUtil.readString;
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

        final var files = List.of("types.isa",
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

        for (final var name : files) {
            try (final var stream = ClassLoader.getSystemResourceAsStream(name)) {
                if (stream == null)
                    throw new FileNotFoundException("resource name '%s'".formatted(name));

                registry.parse(stream);
            } catch (final IOException e) {
                Log.warn("failed to read isa file '%s': %s", name, e);
            }
        }

        final String filename;
        try {
            filename = new File(args[0]).getCanonicalPath();
        } catch (final IOException e) {
            Log.error("failed to get canonical path for filename '%s': %s", args[0], e);
            return;
        }

        final var format = FileFormat.valueOf(args[1].toUpperCase());

        final var     layout  = new MachineLayout(32, 0x1000, 0x80000000L, MiB(4));
        final Machine machine = new Machine64(layout);

        try (final var stream = new FileStream(filename, false)) {
            switch (format) {
                case BIN -> {
                    System.out.println("raw binary:");
                    print(stream, 0, stream.size());

                    machine.setEntry(0x80000000L);
                    machine.loadDirect(stream.seek(0));
                }
                case ELF -> {
                    final var identity = ELF_Identity.read(stream);
                    final var header   = ELF_Header.read(identity, stream);

                    machine.setEntry(header.entry());

                    final var programHeaderTable = new ELF_ProgramHeader[header.phnum()];
                    final var sectionHeaderTable = new ELF_SectionHeader[header.shnum()];

                    for (int i = 0; i < header.phnum(); ++i) {
                        stream.seek(header.phoff() + i * header.phentsize());
                        programHeaderTable[i] = ELF_ProgramHeader.read(identity, stream);
                    }

                    for (int i = 0; i < header.shnum(); ++i) {
                        stream.seek(header.shoff() + i * header.shentsize());
                        sectionHeaderTable[i] = ELF_SectionHeader.read(identity, stream);
                    }

                    final var stringSectionHeader = sectionHeaderTable[header.shstrndx()];

                    System.out.println("program headers:");
                    for (final var programHeader : programHeaderTable) {
                        System.out.println(programHeader);
                        print(stream.seek(programHeader.offset()),
                              programHeader.offset(),
                              programHeader.filesz());

                        if (programHeader.type() == 0x01) {
                            machine.loadSegment(stream.seek(programHeader.offset()),
                                                programHeader.paddr(),
                                                programHeader.filesz());
                        }
                    }

                    System.out.println("section headers:");
                    for (final var sectionHeader : sectionHeaderTable) {
                        System.out.printf("%s: %s%n",
                                          readString(stream.seek(stringSectionHeader.offset() + sectionHeader.name())),
                                          sectionHeader);

                        print(stream.seek(sectionHeader.offset()), sectionHeader.offset(), sectionHeader.size());
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
