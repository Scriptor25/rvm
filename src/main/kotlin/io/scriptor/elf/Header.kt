package io.scriptor.elf

import io.scriptor.util.Log.format
import java.io.InputStream

/**
 * @param type      Identifies object file type.
 * @param machine   Specifies target instruction set architecture.
 * @param version   Set to 1 for the original version of ELF.
 * @param entry     This is the memory address of the entry point from where the process starts executing.
 * This field is either 32 or 64 bits long, depending on the format defined earlier (byte 0x04).
 * If the file doesn't have an associated entry point, then this holds zero.
 * @param phoff     Points to the start of the program header table.
 * It usually follows the file header immediately following this one,
 * making the offset 0x34 or 0x40 for 32- and 64-bit ELF executables, respectively.
 * @param shoff     Points to the start of the section header table.
 * @param flags     Interpretation of this field depends on the target architecture.
 * @param ehsize    Contains the size of this header, normally 64 Bytes for 64-bit and 52 Bytes for 32-bit format.
 * @param phentsize Contains the size of a program header table entry.
 * This will typically be 0x20 (32-bit) or 0x38 (64-bit).
 * @param phnum     Contains the number of entries in the program header table.
 * @param shentsize Contains the size of a section header table entry.
 * This will typically be 0x28 (32-bit) or 0x40 (64-bit).
 * @param shnum     Contains the number of entries in the section header table.
 * @param shstrndx  Contains index of the section header table entry that contains the section names.
 */
data class Header(
    val type: UShort,
    val machine: UShort,
    val version: UInt,
    val entry: ULong,
    val phoff: ULong,
    val shoff: ULong,
    val flags: UInt,
    val ehsize: UShort,
    val phentsize: UShort,
    val phnum: UShort,
    val shentsize: UShort,
    val shnum: UShort,
    val shstrndx: UShort,
) {
    override fun toString(): String {
        return format(
            "type=%x, machine=%x, version=%x, entry=%x, phoff=%x, shoff=%x, flags=%x, ehsize=%x, phentsize=%x, phnum=%x, shentsize=%x, shnum=%x, shstrndx=%x",
            type,
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
            shstrndx,
        )
    }

    companion object {
        fun read(identity: Identity, stream: InputStream): Header {
            val type = identity.readShort(stream)
            val machine = identity.readShort(stream)
            val version = identity.readInt(stream)
            val entry = identity.readOffset(stream)
            val phoff = identity.readOffset(stream)
            val shoff = identity.readOffset(stream)
            val flags = identity.readInt(stream)
            val ehsize = identity.readShort(stream)
            val phentsize = identity.readShort(stream)
            val phnum = identity.readShort(stream)
            val shentsize = identity.readShort(stream)
            val shnum = identity.readShort(stream)
            val shstrndx = identity.readShort(stream)

            return Header(
                type,
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
                shstrndx,
            )
        }
    }
}
