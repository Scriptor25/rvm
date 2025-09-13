package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

public enum ELF_ISA {

    RISC_V((short) 0xF3);

    public static @NotNull ELF_ISA of(final short value) {
        for (final var entry : values())
            if (entry.value == value)
                return entry;
        throw new NoSuchElementException("elf isa: %04x".formatted(value));
    }

    private final short value;

    ELF_ISA(final short value) {
        this.value = value;
    }
}
