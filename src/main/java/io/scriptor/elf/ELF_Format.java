package io.scriptor.elf;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.NoSuchElementException;

import static io.scriptor.Bytes.*;

public enum ELF_Format {

    ELF32LE(32, false),
    ELF32BE(32, true),
    ELF64LE(64, false),
    ELF64BE(64, true);

    public static @NotNull ELF_Format of(final byte class_, final byte data) {
        if (class_ == 0x01) {
            if (data == 0x01) {
                return ELF_Format.ELF32LE;
            } else if (data == 0x02) {
                return ELF_Format.ELF32BE;
            }
        } else if (class_ == 0x02) {
            if (data == 0x01) {
                return ELF_Format.ELF64LE;
            } else if (data == 0x02) {
                return ELF_Format.ELF64BE;
            }
        }
        throw new NoSuchElementException("elf format: class=%02x, data=%02x".formatted(class_, data));
    }

    private final int bits;
    private final boolean inverse;

    ELF_Format(final int bits, final boolean inverse) {
        this.bits = bits;
        this.inverse = inverse;
    }

    public int getBits() {
        return bits;
    }

    public boolean isInverse() {
        return inverse;
    }

    public short readShort(final @NotNull IOStream stream) throws IOException {
        return inverse ? readShortBE(stream) : readShortLE(stream);
    }

    public int readInt(final @NotNull IOStream stream) throws IOException {
        return inverse ? readIntBE(stream) : readIntLE(stream);
    }

    public long readLong(final @NotNull IOStream stream) throws IOException {
        return inverse ? readLongBE(stream) : readLongLE(stream);
    }

    public long readOffset(final @NotNull IOStream stream) throws IOException {
        return switch (bits) {
            case 32 -> readInt(stream);
            case 64 -> readLong(stream);
            default -> throw new IllegalStateException();
        };
    }
}
