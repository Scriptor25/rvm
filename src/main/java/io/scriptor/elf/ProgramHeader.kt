package io.scriptor.elf

import io.scriptor.util.Log.format
import java.io.InputStream

/**
 * @param type    Identifies the type of the segment.
 * @param flags64 Segment-dependent flags (position for 64-bit structure).
 * @param offset  Offset of the segment in the file image.
 * @param vAddress   Virtual address of the segment in memory.
 * @param pAddress   On systems where physical address is relevant, reserved for segment's physical address.
 * @param fileSize  Size in bytes of the segment in the file image. May be 0.
 * @param memSize   Size in bytes of the segment in memory. May be 0.
 * @param flags32 Segment-dependent flags (position for 32-bit structure).
 * @param align   0 and 1 specify no alignment.
 * Otherwise, should be a positive, integral power of 2, with p_vaddr equating p_offset modulus p_align.
 */
data class ProgramHeader(
    val type: UInt,
    val flags64: UInt,
    val offset: ULong,
    val vAddress: ULong,
    val pAddress: ULong,
    val fileSize: ULong,
    val memSize: ULong,
    val flags32: UInt,
    val align: ULong,
) {
    override fun toString(): String {
        return format(
            "type=%x, flags=%x, offset=%x, vaddr=%x, paddr=%x, filesz=%x, memsz=%x, align=%x",
            type,
            flags64 or flags32,
            offset,
            vAddress,
            pAddress,
            fileSize,
            memSize,
            align,
        )
    }

    companion object {
        fun read(identity: Identity, stream: InputStream): ProgramHeader {
            val type = identity.readInt(stream)
            val flags64 = if (identity.format == ELF.ELF64) identity.readInt(stream) else 0U
            val offset = identity.readOffset(stream)
            val vAddress = identity.readOffset(stream)
            val pAddress = identity.readOffset(stream)
            val fileSize = identity.readOffset(stream)
            val memSize = identity.readOffset(stream)
            val flags32 = if (identity.format == ELF.ELF32) identity.readInt(stream) else 0U
            val align = identity.readOffset(stream)
            return ProgramHeader(type, flags64, offset, vAddress, pAddress, fileSize, memSize, flags32, align)
        }
    }
}
