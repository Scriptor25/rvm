package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * @param type      Identifies object file type.
 * @param machine   Specifies target instruction set architecture.
 * @param version   Set to 1 for the original version of ELF.
 * @param entry     This is the memory address of the entry point from where the process starts executing.
 *                  This field is either 32 or 64 bits long, depending on the format defined earlier (byte 0x04).
 *                  If the file doesn't have an associated entry point, then this holds zero.
 * @param phoff     Points to the start of the program header table.
 *                  It usually follows the file header immediately following this one,
 *                  making the offset 0x34 or 0x40 for 32- and 64-bit ELF executables, respectively.
 * @param shoff     Points to the start of the section header table.
 * @param flags     Interpretation of this field depends on the target architecture.
 * @param ehsize    Contains the size of this header, normally 64 Bytes for 64-bit and 52 Bytes for 32-bit format.
 * @param phentsize Contains the size of a program header table entry.
 *                  This will typically be 0x20 (32-bit) or 0x38 (64-bit).
 * @param phnum     Contains the number of entries in the program header table.
 * @param shentsize Contains the size of a section header table entry.
 *                  This will typically be 0x28 (32-bit) or 0x40 (64-bit).
 * @param shnum     Contains the number of entries in the section header table.
 * @param shstrndx  Contains index of the section header table entry that contains the section names.
 */
public record Header(
        short type,
        short machine,
        int version,
        long entry,
        long phoff,
        long shoff,
        int flags,
        short ehsize,
        short phentsize,
        short phnum,
        short shentsize,
        short shnum,
        short shstrndx
) {

    public static @NotNull Header read(final @NotNull Identity identity, final @NotNull InputStream stream)
            throws IOException {
        final var type      = identity.readShort(stream);
        final var machine   = identity.readShort(stream);
        final var version   = identity.readInt(stream);
        final var entry     = identity.readOffset(stream);
        final var phoff     = identity.readOffset(stream);
        final var shoff     = identity.readOffset(stream);
        final var flags     = identity.readInt(stream);
        final var ehsize    = identity.readShort(stream);
        final var phentsize = identity.readShort(stream);
        final var phnum     = identity.readShort(stream);
        final var shentsize = identity.readShort(stream);
        final var shnum     = identity.readShort(stream);
        final var shstrndx  = identity.readShort(stream);
        return new Header(type,
                          machine,
                          version,
                          entry,
                          phoff,
                          shoff,
                          flags,
                          ehsize,
                          phentsize,
                          phnum,
                          shentsize,
                          shnum,
                          shstrndx);
    }

    @Override
    public @NotNull String toString() {
        return "type=%x, machine=%x, version=%x, entry=%x, phoff=%x, shoff=%x, flags=%x, ehsize=%x, phentsize=%x, phnum=%x, shentsize=%x, shnum=%x, shstrndx=%x"
                .formatted(type,
                           machine,
                           version,
                           entry,
                           phoff,
                           shoff,
                           flags,
                           ehsize,
                           phentsize,
                           phnum,
                           shentsize,
                           shnum,
                           shstrndx);
    }
}
