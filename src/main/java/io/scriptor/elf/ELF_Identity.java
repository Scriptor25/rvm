package io.scriptor.elf;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static io.scriptor.ByteUtil.*;
import static io.scriptor.elf.ELF.*;

/**
 * @param magic      0x7F followed by ELF (45 4c 46) in ASCII;
 *                   these four bytes constitute the magic number.
 * @param class_     This byte is set to either 1 or 2 to signify 32- or 64-bit format, respectively.
 * @param data       This byte is set to either 1 or 2 to signify little or big endianness, respectively.
 *                   This affects interpretation of multibyte fields starting with offset 0x10.
 * @param version    Set to 1 for the original and current version of ELF.
 * @param osabi      Identifies the target operating system ABI.
 * @param abiversion Further specifies the ABI version.
 */
public record ELF_Identity(
        byte @NotNull [] magic,
        byte class_,
        byte data,
        byte version,
        byte osabi,
        byte abiversion
) {

    public static @NotNull ELF_Identity read(final @NotNull IOStream stream) throws IOException {
        final var magic = new byte[4];
        stream.read(magic);
        final var class_     = stream.read();
        final var data       = stream.read();
        final var version    = stream.read();
        final var osabi      = stream.read();
        final var abiversion = stream.read();
        stream.read(0x07);
        return new ELF_Identity(magic, class_, data, version, osabi, abiversion);
    }

    public short readShort(final @NotNull IOStream stream) throws IOException {
        return switch (data) {
            case DATA_LE -> readShortLE(stream);
            case DATA_BE -> readShortBE(stream);
            default -> throw new IllegalStateException();
        };
    }

    public int readInt(final @NotNull IOStream stream) throws IOException {
        return switch (data) {
            case DATA_LE -> readIntLE(stream);
            case DATA_BE -> readIntBE(stream);
            default -> throw new IllegalStateException();
        };
    }

    public long readLong(final @NotNull IOStream stream) throws IOException {
        return switch (data) {
            case DATA_LE -> readLongLE(stream);
            case DATA_BE -> readLongBE(stream);
            default -> throw new IllegalStateException();
        };
    }

    public long readOffset(final @NotNull IOStream stream) throws IOException {
        return switch (class_) {
            case CLASS_32 -> readInt(stream);
            case CLASS_64 -> readLong(stream);
            default -> throw new IllegalStateException();
        };
    }
}
