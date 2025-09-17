package io.scriptor.elf;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @param name      An offset to a string in the .shstrtab section that represents the name of this section.
 * @param type      Identifies the type of this header.
 * @param flags     Identifies the attributes of the section.
 * @param addr      Virtual address of the section in memory, for sections that are loaded.
 * @param offset    Offset of the section in the file image.
 * @param size      Size in bytes of the section. May be 0.
 * @param link      Contains the section index of an associated section.
 *                  This field is used for several purposes, depending on the type of section.
 * @param info      Contains extra information about the section.
 *                  This field is used for several purposes, depending on the type of section.
 * @param addralign Contains the required alignment of the section. This field must be a power of two.
 * @param entsize   Contains the size, in bytes, of each entry, for sections that contain fixed-size entries.
 *                  Otherwise, this field contains zero.
 */
public record ELF_SectionHeader(
        int name,
        int type,
        long flags,
        long addr,
        long offset,
        long size,
        int link,
        int info,
        long addralign,
        long entsize
) {

    public static @NotNull ELF_SectionHeader read(final @NotNull ELF_Identity identity, final @NotNull IOStream stream)
            throws IOException {
        final var name      = identity.readInt(stream);
        final var type      = identity.readInt(stream);
        final var flags     = identity.readOffset(stream);
        final var addr      = identity.readOffset(stream);
        final var offset    = identity.readOffset(stream);
        final var size      = identity.readOffset(stream);
        final var link      = identity.readInt(stream);
        final var info      = identity.readInt(stream);
        final var addralign = identity.readOffset(stream);
        final var entsize   = identity.readOffset(stream);
        return new ELF_SectionHeader(name, type, flags, addr, offset, size, link, info, addralign, entsize);
    }

    @Override
    public @NotNull String toString() {
        return "name=%x, type=%x, flags=%x, addr=%x, offset=%x, size=%x, link=%x, info=%x, addralign=%x, entsize=%x"
                .formatted(name, type, flags, addr, offset, size, link, info, addralign, entsize);
    }
}
