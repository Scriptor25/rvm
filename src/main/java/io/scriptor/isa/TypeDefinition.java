package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record TypeDefinition(@NotNull String label, @NotNull Map<String, Operand> operands) {
}
