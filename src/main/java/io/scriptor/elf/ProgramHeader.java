package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

import static io.scriptor.elf.ELF.ELF32;
import static io.scriptor.elf.ELF.ELF64;

/**
 * @param type    Identifies the type of the segment.
 * @param flags64 Segment-dependent flags (position for 64-bit structure).
 * @param offset  Offset of the segment in the file image.
 * @param vaddr   Virtual address of the segment in memory.
 * @param paddr   On systems where physical address is relevant, reserved for segment's physical address.
 * @param filesz  Size in bytes of the segment in the file image. May be 0.
 * @param memsz   Size in bytes of the segment in memory. May be 0.
 * @param flags32 Segment-dependent flags (position for 32-bit structure).
 * @param align   0 and 1 specify no alignment.
 *                Otherwise, should be a positive, integral power of 2, with p_vaddr equating p_offset modulus p_align.
 */
public record ProgramHeader(
        int type,
        int flags64,
        long offset,
        long vaddr,
        long paddr,
        long filesz,
        long memsz,
        int flags32,
        long align
) {

    public static @NotNull ProgramHeader read(final @NotNull Identity identity, final @NotNull InputStream stream)
            throws IOException {
        final var type    = identity.readInt(stream);
        final var flags64 = identity.class_() == ELF64 ? identity.readInt(stream) : 0;
        final var offset  = identity.readOffset(stream);
        final var vaddr   = identity.readOffset(stream);
        final var paddr   = identity.readOffset(stream);
        final var filesz  = identity.readOffset(stream);
        final var memsz   = identity.readOffset(stream);
        final var flags32 = identity.class_() == ELF32 ? identity.readInt(stream) : 0;
        final var align   = identity.readOffset(stream);
        return new ProgramHeader(type, flags64, offset, vaddr, paddr, filesz, memsz, flags32, align);
    }

    @Override
    public @NotNull String toString() {
        return "type=%x, flags=%x, offset=%x, vaddr=%x, paddr=%x, filesz=%x, memsz=%x, align=%x"
                .formatted(type, flags64 | flags32, offset, vaddr, paddr, filesz, memsz, align);
    }
}
