package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record Type(@NotNull String label, @NotNull Map<String, Operand> operands) {

    @Override
    public @NotNull String toString() {
        return "type %s %s".formatted(label, operands);
    }
}
