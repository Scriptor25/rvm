package io.scriptor.elf

import io.scriptor.util.ByteUtil
import io.scriptor.util.Log.format
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @param magic      0x7F followed by ELF (45 4c 46) in ASCII; these four bytes constitute the magic number.
 * @param `class`    This byte is set to either 1 or 2 to signify 32- or 64-bit format, respectively.
 * @param data       This byte is set to either 1 or 2 to signify little or big endianness, respectively. This affects interpretation of multibyte fields starting with offset 0x10.
 * @param version    Set to 1 for the original and current version of ELF.
 * @param osabi      Identifies the target operating system ABI.
 * @param abiversion Further specifies the ABI version.
 */
data class Identity(
    val magic: ByteArray,
    val `class`: UByte,
    val data: UByte,
    val version: UByte,
    val osabi: UByte,
    val abiversion: UByte,
) {
    fun readShort(stream: InputStream): UShort {
        return when (data) {
            ELF.LE -> ByteUtil.readShortLE(stream)
            ELF.BE -> ByteUtil.readShortBE(stream)
            else -> throw IllegalStateException()
        }
    }

    fun readInt(stream: InputStream): UInt {
        return when (data) {
            ELF.LE -> ByteUtil.readIntLE(stream)
            ELF.BE -> ByteUtil.readIntBE(stream)
            else -> throw IllegalStateException()
        }
    }

    fun readLong(stream: InputStream): ULong {
        return when (data) {
            ELF.LE -> ByteUtil.readLongLE(stream)
            ELF.BE -> ByteUtil.readLongBE(stream)
            else -> throw IllegalStateException()
        }
    }

    fun readOffset(stream: InputStream): ULong {
        return when (`class`) {
            ELF.ELF32 -> readInt(stream).toULong()
            ELF.ELF64 -> readLong(stream)
            else -> throw IllegalStateException()
        }
    }

    fun order(): ByteOrder {
        return when (data) {
            ELF.LE -> ByteOrder.LITTLE_ENDIAN
            ELF.BE -> ByteOrder.BIG_ENDIAN
            else -> throw IllegalStateException()
        }
    }

    override fun toString(): String {
        return format(
            "magic=[%x, %x, %x, %x], class=%x, data=%x, version=%x, osabi=%x, abiversion=%x",
            magic[0],
            magic[1],
            magic[2],
            magic[3],
            `class`,
            data,
            version,
            osabi,
            abiversion,
        )
    }

    companion object {
        fun read(buffer: ByteBuffer): Identity {
            val magic = ByteArray(4)
            buffer.get(magic)

            val `class` = buffer.get().toUByte()
            val data = buffer.get().toUByte()
            val version = buffer.get().toUByte()
            val osabi = buffer.get().toUByte()
            val abiversion = buffer.get().toUByte()

            // 7 bytes padding
            return Identity(magic, `class`, data, version, osabi, abiversion)
        }
    }
}
