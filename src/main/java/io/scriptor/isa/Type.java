package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

public record Type(@NotNull String label, @NotNull Operand[] operands) {

    @Override
    public @NotNull String toString() {
        return "type %s %s".formatted(label, operands);
    }
}
