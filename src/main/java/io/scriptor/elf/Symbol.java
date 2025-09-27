package io.scriptor.elf;

import org.jetbrains.annotations.NotNull;

public record Symbol(long addr, @NotNull String name) {

    @Override
    public @NotNull String toString() {
        return "%016x : '%s'".formatted(addr, name);
    }
}
