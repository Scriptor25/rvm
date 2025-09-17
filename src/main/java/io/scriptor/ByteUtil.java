package io.scriptor;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ByteUtil {

    private ByteUtil() {
    }

    public static short parseShortLE(final byte @NotNull [] bytes) {
        return (short) (((bytes[1] & 0xff) << 8)
                        | (bytes[0] & 0xff));
    }

    public static short parseShortBE(final byte @NotNull [] bytes) {
        return (short) (((bytes[0] & 0xff) << 8)
                        | (bytes[1] & 0xff));
    }

    public static int parseIntLE(final byte @NotNull [] bytes) {
        return ((bytes[3] & 0xff) << 24)
               | ((bytes[2] & 0xff) << 16)
               | ((bytes[1] & 0xff) << 8)
               | (bytes[0] & 0xff);
    }

    public static int parseIntBE(final byte @NotNull [] bytes) {
        return ((bytes[0] & 0xff) << 24)
               | ((bytes[1] & 0xff) << 16)
               | ((bytes[2] & 0xff) << 8)
               | (bytes[3] & 0xff);
    }

    public static long parseLongLE(final byte @NotNull [] bytes) {
        return ((bytes[7] & 0xffL) << 56L)
               | ((bytes[6] & 0xffL) << 48L)
               | ((bytes[5] & 0xffL) << 40L)
               | ((bytes[4] & 0xffL) << 32L)
               | ((bytes[3] & 0xffL) << 24L)
               | ((bytes[2] & 0xffL) << 16L)
               | ((bytes[1] & 0xffL) << 8L)
               | (bytes[0] & 0xffL);
    }

    public static long parseLongBE(final byte @NotNull [] bytes) {
        return ((bytes[0] & 0xffL) << 56L)
               | ((bytes[1] & 0xffL) << 48L)
               | ((bytes[2] & 0xffL) << 40L)
               | ((bytes[3] & 0xffL) << 32L)
               | ((bytes[4] & 0xffL) << 24L)
               | ((bytes[5] & 0xffL) << 16L)
               | ((bytes[6] & 0xffL) << 8L)
               | (bytes[7] & 0xffL);
    }

    public static short readShortLE(final @NotNull IOStream stream) throws IOException {
        return parseShortLE(stream.read(2));
    }

    public static short readShortBE(final @NotNull IOStream stream) throws IOException {
        return parseShortBE(stream.read(2));
    }

    public static int readIntLE(final @NotNull IOStream stream) throws IOException {
        return parseIntLE(stream.read(4));
    }

    public static int readIntBE(final @NotNull IOStream stream) throws IOException {
        return parseIntBE(stream.read(4));
    }

    public static long readLongLE(final @NotNull IOStream stream) throws IOException {
        return parseLongLE(stream.read(8));
    }

    public static long readLongBE(final @NotNull IOStream stream) throws IOException {
        return parseLongBE(stream.read(8));
    }

    public static @NotNull String readString(final @NotNull IOStream stream) throws IOException {
        final var builder = new StringBuilder();
        for (int b; (b = stream.read()) > 0; )
            builder.append((char) b);
        return builder.toString();
    }

    public static int signExtend(final int value, final int bits) {
        final var shift = 32 - bits;
        return (value << shift) >> shift;
    }

    public static long signExtend(final long value, final int bits) {
        final var shift = 64 - bits;
        return (value << shift) >> shift;
    }
}
