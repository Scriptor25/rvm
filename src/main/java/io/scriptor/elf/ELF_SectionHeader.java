package io.scriptor.elf;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ELF_SectionHeader {

    /**
     * An offset to a string in the .shstrtab section that represents the name of this section.
     */
    public final int name;
    /**
     * Identifies the type of this header.
     */
    public final int type;
    /**
     * Identifies the attributes of the section.
     */
    public final long flags;
    /**
     * Virtual address of the section in memory, for sections that are loaded.
     */
    public final long addr;
    /**
     * Offset of the section in the file image.
     */
    public final long offset;
    /**
     * Size in bytes of the section. May be 0.
     */
    public final long size;
    /**
     * Contains the section index of an associated section.
     * This field is used for several purposes, depending on the type of section.
     */
    public final int link;
    /**
     * Contains extra information about the section.
     * This field is used for several purposes, depending on the type of section.
     */
    public final int info;
    /**
     * Contains the required alignment of the section. This field must be a power of two.
     */
    public final long addralign;
    /**
     * Contains the size, in bytes, of each entry, for sections that contain fixed-size entries.
     * Otherwise, this field contains zero.
     */
    public final long entsize;

    public ELF_SectionHeader(final @NotNull IOStream stream, final @NotNull ELF_Format format) throws IOException {
        name = format.readInt(stream);
        type = format.readInt(stream);
        flags = format.readOffset(stream);
        addr = format.readOffset(stream);
        offset = format.readOffset(stream);
        size = format.readOffset(stream);
        link = format.readInt(stream);
        info = format.readInt(stream);
        addralign = format.readOffset(stream);
        entsize = format.readOffset(stream);
    }

    @Override
    public @NotNull String toString() {
        return "name=%x, type=%x, flags=%x, addr=%x, offset=%x, size=%x, link=%x, info=%x, addralign=%x, entsize=%x"
                .formatted(name, type, flags, addr, offset, size, link, info, addralign, entsize);
    }
}
