package io.scriptor.elf;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ELF_Header {

    public final ELF_Identity identity;

    /**
     * Identifies object file type.
     */
    public final ELF_Type type;
    /**
     * Specifies target instruction set architecture.
     */
    public final ELF_ISA machine;
    /**
     * Set to 1 for the original version of ELF.
     */
    public final int version;
    /**
     * This is the memory address of the entry point from where the process starts executing.
     * This field is either 32 or 64 bits long, depending on the format defined earlier (byte 0x04).
     * If the file doesn't have an associated entry point, then this holds zero.
     */
    public final long entry;
    /**
     * Points to the start of the program header table.
     * It usually follows the file header immediately following this one,
     * making the offset 0x34 or 0x40 for 32- and 64-bit ELF executables, respectively.
     */
    public final long phoff;
    /**
     * Points to the start of the section header table.
     */
    public final long shoff;
    /**
     * Interpretation of this field depends on the target architecture.
     */
    public final int flags;
    /**
     * Contains the size of this header, normally 64 Bytes for 64-bit and 52 Bytes for 32-bit format.
     */
    public final short ehsize;
    /**
     * Contains the size of a program header table entry.
     * This will typically be 0x20 (32-bit) or 0x38 (64-bit).
     */
    public final short phentsize;
    /**
     * Contains the number of entries in the program header table.
     */
    public final short phnum;
    /**
     * Contains the size of a section header table entry.
     * This will typically be 0x28 (32-bit) or 0x40 (64-bit).
     */
    public final short shentsize;
    /**
     * Contains the number of entries in the section header table.
     */
    public final short shnum;
    /**
     * Contains index of the section header table entry that contains the section names.
     */
    public final short shstrndx;

    public ELF_Header(final @NotNull IOStream stream) throws IOException {
        identity = new ELF_Identity(stream.read(0x10));

        type = ELF_Type.of(identity.format.readShort(stream));
        machine = ELF_ISA.of(identity.format.readShort(stream));
        version = identity.format.readInt(stream);
        entry = identity.format.readOffset(stream);
        phoff = identity.format.readOffset(stream);
        shoff = identity.format.readOffset(stream);
        flags = identity.format.readInt(stream);
        ehsize = identity.format.readShort(stream);
        phentsize = identity.format.readShort(stream);
        phnum = identity.format.readShort(stream);
        shentsize = identity.format.readShort(stream);
        shnum = identity.format.readShort(stream);
        shstrndx = identity.format.readShort(stream);
    }
}
