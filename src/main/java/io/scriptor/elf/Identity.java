package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static io.scriptor.elf.ELF.*;
import static io.scriptor.util.ByteUtil.*;

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
public record Identity(
        byte @NotNull [] magic,
        byte class_,
        byte data,
        byte version,
        byte osabi,
        byte abiversion
) {

    public static @NotNull Identity read(final @NotNull ByteBuffer buffer) throws IOException {
        final var magic = new byte[4];
        buffer.get(magic);

        final var class_     = buffer.get();
        final var data       = buffer.get();
        final var version    = buffer.get();
        final var osabi      = buffer.get();
        final var abiversion = buffer.get();

        // 7 bytes padding

        return new Identity(magic, class_, data, version, osabi, abiversion);
    }

    public short readShort(final @NotNull InputStream stream) throws IOException {
        return switch (data) {
            case LE -> readShortLE(stream);
            case BE -> readShortBE(stream);
            default -> throw new IllegalStateException();
        };
    }

    public int readInt(final @NotNull InputStream stream) throws IOException {
        return switch (data) {
            case LE -> readIntLE(stream);
            case BE -> readIntBE(stream);
            default -> throw new IllegalStateException();
        };
    }

    public long readLong(final @NotNull InputStream stream) throws IOException {
        return switch (data) {
            case LE -> readLongLE(stream);
            case BE -> readLongBE(stream);
            default -> throw new IllegalStateException();
        };
    }

    public long readOffset(final @NotNull InputStream stream) throws IOException {
        return switch (class_) {
            case ELF32 -> readInt(stream);
            case ELF64 -> readLong(stream);
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public @NotNull String toString() {
        return "magic=[%x, %x, %x, %x], class=%x, data=%x, version=%x, osabi=%x, abiversion=%x"
                .formatted(magic[0], magic[1], magic[2], magic[3], class_, data, version, osabi, abiversion);
    }
}
