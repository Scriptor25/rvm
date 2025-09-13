package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

public enum ELF_OSABI {

    SYSTEM_V((byte) 0x00),
    HP_UX((byte) 0x01),
    NET_BSD((byte) 0x02),
    LINUX((byte) 0x03),
    GNU_HURD((byte) 0x04),
    SOLARIS((byte) 0x06),
    AIX((byte) 0x07),
    IRIX((byte) 0x08),
    FREE_BSD((byte) 0x09),
    TRU64((byte) 0x0a),
    NOVELL_MODESTO((byte) 0x0b),
    OPEN_BSD((byte) 0x0c),
    OPEN_VMS((byte) 0x0d),
    NON_STOP_KERNEL((byte) 0x0e),
    AROS((byte) 0x0f),
    FENIX_OS((byte) 0x10),
    NUXI_CLOUD_ABI((byte) 0x11),
    STRATUS_TECHNOLOGIES_OPEN_VOS((byte) 0x12);

    public static @NotNull ELF_OSABI of(final byte value) {
        for (final var entry : values())
            if (entry.value == value)
                return entry;
        throw new NoSuchElementException("elf osabi: %02x".formatted(value));
    }

    private final byte value;

    ELF_OSABI(final byte value) {
        this.value = value;
    }
}
