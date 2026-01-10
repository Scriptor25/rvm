package io.scriptor.util

import java.io.InputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import kotlin.math.min

object ByteUtil {

    fun parseShortLE(bytes: ByteArray): UShort {
        return (((bytes[1].toUInt() and 0xFFu) shl 0x08)
                or (bytes[0].toUInt() and 0xFFu)).toUShort()
    }

    fun parseShortBE(bytes: ByteArray): UShort {
        return (((bytes[0].toUInt() and 0xFFu) shl 0x08)
                or (bytes[1].toUInt() and 0xFFu)).toUShort()
    }

    fun parseIntLE(bytes: ByteArray): UInt {
        return (((bytes[3].toUInt() and 0xFFu) shl 0x18)
                or ((bytes[2].toUInt() and 0xFFu) shl 0x10)
                or ((bytes[1].toUInt() and 0xFFu) shl 0x08)
                or (bytes[0].toUInt() and 0xFFu))
    }

    fun parseIntBE(bytes: ByteArray): UInt {
        return (((bytes[0].toUInt() and 0xFFu) shl 0x18)
                or ((bytes[1].toUInt() and 0xFFu) shl 0x10)
                or ((bytes[2].toUInt() and 0xFFu) shl 0x08)
                or (bytes[3].toUInt() and 0xFFu))
    }

    fun parseLongLE(bytes: ByteArray): ULong {
        return (((bytes[7].toULong() and 0xFFu) shl 0x38)
                or ((bytes[6].toULong() and 0xFFu) shl 0x30)
                or ((bytes[5].toULong() and 0xFFu) shl 0x28)
                or ((bytes[4].toULong() and 0xFFu) shl 0x20)
                or ((bytes[3].toULong() and 0xFFu) shl 0x18)
                or ((bytes[2].toULong() and 0xFFu) shl 0x10)
                or ((bytes[1].toULong() and 0xFFu) shl 0x08)
                or (bytes[0].toULong() and 0xFFu))
    }

    fun parseLongBE(bytes: ByteArray): ULong {
        return (((bytes[0].toULong() and 0xFFu) shl 0x38)
                or ((bytes[1].toULong() and 0xFFu) shl 0x30)
                or ((bytes[2].toULong() and 0xFFu) shl 0x28)
                or ((bytes[3].toULong() and 0xFFu) shl 0x20)
                or ((bytes[4].toULong() and 0xFFu) shl 0x18)
                or ((bytes[5].toULong() and 0xFFu) shl 0x10)
                or ((bytes[6].toULong() and 0xFFu) shl 0x08)
                or (bytes[7].toULong() and 0xFFu))
    }

    fun readShortLE(stream: InputStream): UShort {
        return parseShortLE(stream.readNBytes(2))
    }

    fun readShortBE(stream: InputStream): UShort {
        return parseShortBE(stream.readNBytes(2))
    }

    fun readIntLE(stream: InputStream): UInt {
        return parseIntLE(stream.readNBytes(4))
    }

    fun readIntBE(stream: InputStream): UInt {
        return parseIntBE(stream.readNBytes(4))
    }

    fun readLongLE(stream: InputStream): ULong {
        return parseLongLE(stream.readNBytes(8))
    }

    fun readLongBE(stream: InputStream): ULong {
        return parseLongBE(stream.readNBytes(8))
    }

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
                out.printf("%08x - %08x%n", allZeroBegin, begin - 1)
            } else if (!allZero && allZeroP) {
                allZero = true
                allZeroBegin = begin.toLong()
                continue
            } else if (allZero) continue

            out.printf("%08x |", begin)

            for (j in 0..<chunk) out.printf(" %02X", bytes[j])
            for (j in chunk..<CHUNK) out.print(" 00")

            out.print(" | ")

            for (j in 0..<chunk) out.print(if (bytes[j] >= 0x20) Char(bytes[j].toUShort()) else '.')
            for (j in chunk..<CHUNK) out.print('.')

            out.println()
        }
        out.println("(END)")
    }
}
