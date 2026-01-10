package io.scriptor.elf

import io.scriptor.util.Log.format
import java.io.InputStream

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
 * Otherwise, should be a positive, integral power of 2, with p_vaddr equating p_offset modulus p_align.
 */
data class ProgramHeader(
    val type: UInt,
    val flags64: UInt,
    val offset: ULong,
    val vaddr: ULong,
    val paddr: ULong,
    val filesz: ULong,
    val memsz: ULong,
    val flags32: UInt,
    val align: ULong,
) {
    override fun toString(): String {
        return format(
            "type=%x, flags=%x, offset=%x, vaddr=%x, paddr=%x, filesz=%x, memsz=%x, align=%x",
            type,
            flags64 or flags32,
            offset,
            vaddr,
            paddr,
            filesz,
            memsz,
            align,
        )
    }

    companion object {
        fun read(identity: Identity, stream: InputStream): ProgramHeader {
            val type = identity.readInt(stream)
            val flags64 = if (identity.`class` == ELF.ELF64) identity.readInt(stream) else 0U
            val offset = identity.readOffset(stream)
            val vaddr = identity.readOffset(stream)
            val paddr = identity.readOffset(stream)
            val filesz = identity.readOffset(stream)
            val memsz = identity.readOffset(stream)
            val flags32 = if (identity.`class` == ELF.ELF32) identity.readInt(stream) else 0U
            val align = identity.readOffset(stream)
            return ProgramHeader(type, flags64, offset, vaddr, paddr, filesz, memsz, flags32, align)
        }
    }
}
