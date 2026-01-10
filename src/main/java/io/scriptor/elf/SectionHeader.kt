package io.scriptor.elf

import io.scriptor.util.Log.format
import java.io.InputStream

/**
 * @param name      An offset to a string in the .shstrtab section that represents the name of this section.
 * @param type      Identifies the type of this header.
 * @param flags     Identifies the attributes of the section.
 * @param addr      Virtual address of the section in memory, for sections that are loaded.
 * @param offset    Offset of the section in the file image.
 * @param size      Size in bytes of the section. May be 0.
 * @param link      Contains the section index of an associated section.
 * This field is used for several purposes, depending on the type of section.
 * @param info      Contains extra information about the section.
 * This field is used for several purposes, depending on the type of section.
 * @param addralign Contains the required alignment of the section. This field must be a power of two.
 * @param entsize   Contains the size, in bytes, of each entry, for sections that contain fixed-size entries.
 * Otherwise, this field contains zero.
 */
data class SectionHeader(
    val name: UInt,
    val type: UInt,
    val flags: ULong,
    val addr: ULong,
    val offset: ULong,
    val size: ULong,
    val link: UInt,
    val info: UInt,
    val addralign: ULong,
    val entsize: ULong,
) {
    override fun toString(): String {
        return format(
            "name=%x, type=%x, flags=%x, addr=%x, offset=%x, size=%x, link=%x, info=%x, addralign=%x, entsize=%x",
            name,
            type,
            flags,
            addr,
            offset,
            size,
            link,
            info,
            addralign,
            entsize,
        )
    }

    companion object {
        fun read(identity: Identity, stream: InputStream): SectionHeader {
            val name = identity.readInt(stream)
            val type = identity.readInt(stream)
            val flags = identity.readOffset(stream)
            val addr = identity.readOffset(stream)
            val offset = identity.readOffset(stream)
            val size = identity.readOffset(stream)
            val link = identity.readInt(stream)
            val info = identity.readInt(stream)
            val addralign = identity.readOffset(stream)
            val entsize = identity.readOffset(stream)
            return SectionHeader(name, type, flags, addr, offset, size, link, info, addralign, entsize)
        }
    }
}
