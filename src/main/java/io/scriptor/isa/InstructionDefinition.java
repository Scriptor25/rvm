package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.IntPredicate;

public record InstructionDefinition(
        @NotNull String mnemonic,
        int mask,
        int bits,
        @NotNull Map<String, Operand> operands
) implements IntPredicate {

    @Override
    public boolean test(final int value) {
        return bits == (value & mask)
               && operands.values()
                          .stream()
                          .noneMatch(operand -> operand.excludes(value));
    }

    public int get(final @NotNull String operand, final int value) {
        return operands.get(operand).extract(value);
    }
}
