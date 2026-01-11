package io.scriptor.util

import io.scriptor.util.Log.format
import java.io.InputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import kotlin.math.min

object ByteUtil {

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseShortLE(bytes: UByteArray): UShort {
        return (((bytes[1].toUInt() and 0xFFU) shl 0x08)
                or (bytes[0].toUInt() and 0xFFU)).toUShort()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseShortBE(bytes: UByteArray): UShort {
        return (((bytes[0].toUInt() and 0xFFU) shl 0x08)
                or (bytes[1].toUInt() and 0xFFU)).toUShort()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseIntLE(bytes: UByteArray): UInt {
        return (((bytes[3].toUInt() and 0xFFU) shl 0x18)
                or ((bytes[2].toUInt() and 0xFFU) shl 0x10)
                or ((bytes[1].toUInt() and 0xFFU) shl 0x08)
                or (bytes[0].toUInt() and 0xFFU))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseIntBE(bytes: UByteArray): UInt {
        return (((bytes[0].toUInt() and 0xFFU) shl 0x18)
                or ((bytes[1].toUInt() and 0xFFU) shl 0x10)
                or ((bytes[2].toUInt() and 0xFFU) shl 0x08)
                or (bytes[3].toUInt() and 0xFFU))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseLongLE(bytes: UByteArray): ULong {
        return (((bytes[7].toULong() and 0xFFU) shl 0x38)
                or ((bytes[6].toULong() and 0xFFU) shl 0x30)
                or ((bytes[5].toULong() and 0xFFU) shl 0x28)
                or ((bytes[4].toULong() and 0xFFU) shl 0x20)
                or ((bytes[3].toULong() and 0xFFU) shl 0x18)
                or ((bytes[2].toULong() and 0xFFU) shl 0x10)
                or ((bytes[1].toULong() and 0xFFU) shl 0x08)
                or (bytes[0].toULong() and 0xFFU))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseLongBE(bytes: UByteArray): ULong {
        return (((bytes[0].toULong() and 0xFFU) shl 0x38)
                or ((bytes[1].toULong() and 0xFFU) shl 0x30)
                or ((bytes[2].toULong() and 0xFFU) shl 0x28)
                or ((bytes[3].toULong() and 0xFFU) shl 0x20)
                or ((bytes[4].toULong() and 0xFFU) shl 0x18)
                or ((bytes[5].toULong() and 0xFFU) shl 0x10)
                or ((bytes[6].toULong() and 0xFFU) shl 0x08)
                or (bytes[7].toULong() and 0xFFU))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toByteArrayLE(value: UShort): UByteArray {
        return ubyteArrayOf(
            (value.toUInt() and 0xFFU).toUByte(),
            ((value.toUInt() shr 0x08) and 0xFFU).toUByte(),
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toByteArrayBE(value: UShort): UByteArray {
        return ubyteArrayOf(
            ((value.toUInt() shr 0x08) and 0xFFU).toUByte(),
            (value.toUInt() and 0xFFU).toUByte(),
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toByteArrayLE(value: UInt): UByteArray {
        return ubyteArrayOf(
            value.toUByte(),
            (value shr 0x08).toUByte(),
            (value shr 0x10).toUByte(),
            (value shr 0x18).toUByte(),
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toByteArrayBE(value: UInt): UByteArray {
        return ubyteArrayOf(
            (value shr 0x18).toUByte(),
            (value shr 0x10).toUByte(),
            (value shr 0x08).toUByte(),
            value.toUByte(),
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toByteArrayLE(value: ULong): UByteArray {
        return ubyteArrayOf(
            value.toUByte(),
            (value shr 0x08).toUByte(),
            (value shr 0x10).toUByte(),
            (value shr 0x18).toUByte(),
            (value shr 0x20).toUByte(),
            (value shr 0x28).toUByte(),
            (value shr 0x30).toUByte(),
            (value shr 0x38).toUByte(),
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toByteArrayBE(value: ULong): UByteArray {
        return ubyteArrayOf(
            (value shr 0x38).toUByte(),
            (value shr 0x30).toUByte(),
            (value shr 0x28).toUByte(),
            (value shr 0x20).toUByte(),
            (value shr 0x18).toUByte(),
            (value shr 0x10).toUByte(),
            (value shr 0x08).toUByte(),
            value.toUByte(),
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseShortLE(string: String): UShort = parseShortLE(string.hexToUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseShortBE(string: String): UShort = parseShortBE(string.hexToUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseIntLE(string: String): UInt = parseIntLE(string.hexToUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseIntBE(string: String): UInt = parseIntBE(string.hexToUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseLongLE(string: String): ULong = parseLongLE(string.hexToUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun parseLongBE(string: String): ULong = parseLongBE(string.hexToUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toHexStringLE(value: UShort): String = toByteArrayLE(value).toHexString()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toHexStringBE(value: UShort): String = toByteArrayBE(value).toHexString()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toHexStringLE(value: UInt): String = toByteArrayLE(value).toHexString()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toHexStringBE(value: UInt): String = toByteArrayBE(value).toHexString()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toHexStringLE(value: ULong): String = toByteArrayLE(value).toHexString()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun toHexStringBE(value: ULong): String = toByteArrayBE(value).toHexString()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun readShortLE(stream: InputStream): UShort = parseShortLE(stream.readNBytes(2).toUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun readShortBE(stream: InputStream): UShort = parseShortBE(stream.readNBytes(2).toUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun readIntLE(stream: InputStream): UInt = parseIntLE(stream.readNBytes(4).toUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun readIntBE(stream: InputStream): UInt = parseIntBE(stream.readNBytes(4).toUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun readLongLE(stream: InputStream): ULong = parseLongLE(stream.readNBytes(8).toUByteArray())

    @OptIn(ExperimentalUnsignedTypes::class)
    fun readLongBE(stream: InputStream): ULong = parseLongBE(stream.readNBytes(8).toUByteArray())

    fun readString(stream: InputStream): String {
        val builder = StringBuilder()
        var b: Int
        while ((stream.read().also { b = it }) > 0) {
            builder.appendCodePoint(b)
        }
        return builder.toString()
    }

    fun signExtend(value: UInt, bits: Int): Int {
        val shift = 0x20 - bits
        return (value shl shift).toInt() shr shift
    }

    fun signExtendLong(value: UInt, bits: Int): Long {
        val shift = 0x40 - bits
        return (value.toULong() shl shift).toLong() shr shift
    }

    fun signExtendLong(value: ULong, bits: Int): Long {
        val shift = 0x40 - bits
        return (value shl shift).toLong() shr shift
    }

    fun dump(out: PrintStream, buffer: ByteBuffer) {
        val CHUNK = 0x10

        var allZero = false
        var allZeroBegin = 0L

        while (buffer.hasRemaining()) {
            val begin = buffer.position()
            val chunk = min(buffer.remaining(), CHUNK)

            val bytes = ByteArray(chunk)
            buffer.get(bytes)

            var allZeroP = true
            for (j in 0..<chunk) if (bytes[j].toInt() != 0) {
                allZeroP = false
                break
            }

            if (allZero && !allZeroP) {
                allZero = false
                out.println(format("%08x - %08x", allZeroBegin, begin - 1))
            } else if (!allZero && allZeroP) {
                allZero = true
                allZeroBegin = begin.toLong()
                continue
            } else if (allZero) continue

            out.print(format("%08x |", begin))

            for (j in 0..<chunk) out.print(format(" %02X", bytes[j]))
            for (j in chunk..<CHUNK) out.print(" 00")

            out.print(" | ")

            for (j in 0..<chunk) out.print(if (bytes[j] >= 0x20) Char(bytes[j].toUShort()) else '.')
            for (j in chunk..<CHUNK) out.print('.')

            out.println()
        }
        out.println("(END)")
    }
}
