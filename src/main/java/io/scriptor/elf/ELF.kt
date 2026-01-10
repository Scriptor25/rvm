package io.scriptor.elf

import io.scriptor.util.ByteUtil
import io.scriptor.util.ExtendedInputStream

interface ELF {
    companion object {
        fun readSymbols(
            identity: Identity,
            stream: ExtendedInputStream,
            symtab: SectionHeader,
            strtab: SectionHeader,
            symbols: SymbolTable,
            offset: ULong,
        ) {
            var i = 0UL
            while (i < symtab.size) {
                stream.seek((symtab.offset + i).toLong())

                val name = identity.readInt(stream)
                val info = stream.read()
                val other = stream.read()
                val shndx = identity.readShort(stream)
                val value = identity.readLong(stream)
                val size = identity.readLong(stream)

                stream.seek((strtab.offset + name).toLong())

                val string = ByteUtil.readString(stream)
                if (string.isBlank()) {
                    i += symtab.entsize
                    continue
                }

                symbols.put(value + offset, string)
                i += symtab.entsize
            }
        }

        const val ELF32: UByte = 0x1U
        const val ELF64: UByte = 0x2U

        const val LE: UByte = 0x1U
        const val BE: UByte = 0x2U

        const val VERSION_1: UByte = 0x1U

        const val OSABI_SYSTEM_V: UByte = 0x00U
        const val OSABI_HP_UX: UByte = 0x01U
        const val OSABI_NET_BSD: UByte = 0x02U
        const val OSABI_LINUX: UByte = 0x03U
        const val OSABI_GNU_HURD: UByte = 0x04U
        const val OSABI_SOLARIS: UByte = 0x06U
        const val OSABI_AIX: UByte = 0x07U
        const val OSABI_IRIX: UByte = 0x08U
        const val OSABI_FREE_BSD: UByte = 0x09U
        const val OSABI_TRU64: UByte = 0x0AU
        const val OSABI_NOVELL_MODESTO: UByte = 0x0BU
        const val OSABI_OPEN_BSD: UByte = 0x0CU
        const val OSABI_OPEN_VMS: UByte = 0x0DU
        const val OSABI_NON_STOP_KERNEL: UByte = 0x0EU
        const val OSABI_AROS: UByte = 0x0FU
        const val OSABI_FENIX_OS: UByte = 0x10U
        const val OSABI_NUXI_CLOUD_ABI: UByte = 0x11U
        const val OSABI_STRATUS_TECHNOLOGIES_OPEN_VOS: UByte = 0x12U

        const val TYPE_NONE: Short = 0x0000
        const val TYPE_REL: Short = 0x0001
        const val TYPE_EXEC: Short = 0x0002
        const val TYPE_DYN: Short = 0x0003
        const val TYPE_CORE: Short = 0x0004

        const val MACHINE_RISC_V: Short = 0x00F3
    }
}
