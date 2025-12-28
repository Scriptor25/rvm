package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;

public final class ByteUtil {

    private ByteUtil() {
    }

    public static short parseShortLE(final byte @NotNull [] bytes) {
        return (short) (((bytes[1] & 0xFF) << 0x08)
                        | (bytes[0] & 0xFF));
    }

    public static short parseShortBE(final byte @NotNull [] bytes) {
        return (short) (((bytes[0] & 0xFF) << 0x08)
                        | (bytes[1] & 0xFF));
    }

    public static int parseIntLE(final byte @NotNull [] bytes) {
        return ((bytes[3] & 0xFF) << 0x18)
               | ((bytes[2] & 0xFF) << 0x10)
               | ((bytes[1] & 0xFF) << 0x08)
               | (bytes[0] & 0xFF);
    }

    public static int parseIntBE(final byte @NotNull [] bytes) {
        return ((bytes[0] & 0xFF) << 0x18)
               | ((bytes[1] & 0xFF) << 0x10)
               | ((bytes[2] & 0xFF) << 0x08)
               | (bytes[3] & 0xFF);
    }

    public static long parseLongLE(final byte @NotNull [] bytes) {
        return ((bytes[7] & 0xFFL) << 0x38L)
               | ((bytes[6] & 0xFFL) << 0x30L)
               | ((bytes[5] & 0xFFL) << 0x28L)
               | ((bytes[4] & 0xFFL) << 0x20L)
               | ((bytes[3] & 0xFFL) << 0x18L)
               | ((bytes[2] & 0xFFL) << 0x10L)
               | ((bytes[1] & 0xFFL) << 0x08L)
               | (bytes[0] & 0xFFL);
    }

    public static long parseLongBE(final byte @NotNull [] bytes) {
        return ((bytes[0] & 0xFFL) << 0x38L)
               | ((bytes[1] & 0xFFL) << 0x30L)
               | ((bytes[2] & 0xFFL) << 0x28L)
               | ((bytes[3] & 0xFFL) << 0x20L)
               | ((bytes[4] & 0xFFL) << 0x18L)
               | ((bytes[5] & 0xFFL) << 0x10L)
               | ((bytes[6] & 0xFFL) << 0x08L)
               | (bytes[7] & 0xFFL);
    }

    public static short readShortLE(final @NotNull InputStream stream) throws IOException {
        return parseShortLE(stream.readNBytes(2));
    }

    public static short readShortBE(final @NotNull InputStream stream) throws IOException {
        return parseShortBE(stream.readNBytes(2));
    }

    public static int readIntLE(final @NotNull InputStream stream) throws IOException {
        return parseIntLE(stream.readNBytes(4));
    }

    public static int readIntBE(final @NotNull InputStream stream) throws IOException {
        return parseIntBE(stream.readNBytes(4));
    }

    public static long readLongLE(final @NotNull InputStream stream) throws IOException {
        return parseLongLE(stream.readNBytes(8));
    }

    public static long readLongBE(final @NotNull InputStream stream) throws IOException {
        return parseLongBE(stream.readNBytes(8));
    }

    public static @NotNull String readString(final @NotNull InputStream stream) throws IOException {
        final var builder = new StringBuilder();
        for (int b; (b = stream.read()) > 0; ) {
            builder.append((char) b);
        }
        return builder.toString();
    }

    public static int signExtend(final int value, final int bits) {
        final var shift = 0x20 - bits;
        return (value << shift) >> shift;
    }

    public static long signExtend(final long value, final int bits) {
        final var shift = 0x40 - bits;
        return (value << shift) >> shift;
    }

    public static void dump(final @NotNull PrintStream out, final @NotNull ByteBuffer buffer) {

        final var CHUNK = 0x10;

        var allZero      = false;
        var allZeroBegin = 0L;

        while (buffer.hasRemaining()) {

            final var begin = buffer.position();
            final var chunk = Math.min(buffer.remaining(), CHUNK);

            final var bytes = new byte[chunk];
            buffer.get(bytes);

            boolean allZeroP = true;
            for (int j = 0; j < chunk; ++j)
                if (bytes[j] != 0) {
                    allZeroP = false;
                    break;
                }

            if (allZero && !allZeroP) {
                allZero = false;
                out.printf("%08x - %08x%n", allZeroBegin, begin - 1);
            } else if (!allZero && allZeroP) {
                allZero = true;
                allZeroBegin = begin;
                continue;
            } else if (allZero)
                continue;

            out.printf("%08x |", begin);

            for (int j = 0; j < chunk; ++j)
                out.printf(" %02X", bytes[j]);
            for (int j = chunk; j < CHUNK; ++j)
                out.print(" 00");

            out.print(" | ");

            for (int j = 0; j < chunk; ++j)
                out.print(bytes[j] >= 0x20 ? (char) bytes[j] : '.');
            for (int j = chunk; j < CHUNK; ++j)
                out.print('.');

            out.println();
        }
        out.println("(END)");
    }
}
