package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

public enum ELF_Type {

    NONE((short) 0x0000),
    REL((short) 0x0001),
    EXEC((short) 0x0002),
    DYN((short) 0x0003),
    CORE((short) 0x0004);

    public static @NotNull ELF_Type of(final short value) {
        for (final var entry : values())
            if (entry.value == value)
                return entry;
        throw new NoSuchElementException("elf type: %04x".formatted(value));
    }

    private final short value;

    ELF_Type(final short value) {
        this.value = value;
    }
}
