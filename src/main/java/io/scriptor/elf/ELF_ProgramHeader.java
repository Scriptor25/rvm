package io.scriptor.elf;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ELF_ProgramHeader {

    /**
     * Identifies the type of the segment.
     */
    public final int type;
    /**
     * Segment-dependent flags (position for 64-bit structure).
     */
    public final int flags64;
    /**
     * Offset of the segment in the file image.
     */
    public final long offset;
    /**
     * Virtual address of the segment in memory.
     */
    public final long vaddr;
    /**
     * On systems where physical address is relevant, reserved for segment's physical address.
     */
    public final long paddr;
    /**
     * Size in bytes of the segment in the file image. May be 0.
     */
    public final long filesz;
    /**
     * Size in bytes of the segment in memory. May be 0.
     */
    public final long memsz;
    /**
     * Segment-dependent flags (position for 32-bit structure).
     */
    public final int flags32;
    /**
     * 0 and 1 specify no alignment.
     * Otherwise, should be a positive, integral power of 2, with p_vaddr equating p_offset modulus p_align.
     */
    public final long align;

    public ELF_ProgramHeader(final @NotNull IOStream stream, final @NotNull ELF_Format format) throws IOException {
        type = format.readInt(stream);
        flags64 = format.getBits() == 64 ? format.readInt(stream) : 0;
        offset = format.readOffset(stream);
        vaddr = format.readOffset(stream);
        paddr = format.readOffset(stream);
        filesz = format.readOffset(stream);
        memsz = format.readOffset(stream);
        flags32 = format.getBits() == 32 ? format.readInt(stream) : 0;
        align = format.readOffset(stream);
    }

    @Override
    public @NotNull String toString() {
        return "type=%x, flags=%x, offset=%x, vaddr=%x, paddr=%x, filesz=%x, memsz=%x, align=%x"
                .formatted(type, flags64 | flags32, offset, vaddr, paddr, filesz, memsz, align);
    }
}
