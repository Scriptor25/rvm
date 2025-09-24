package io.scriptor.isa;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.IntPredicate;

public record Instruction(
        @NotNull String mnemonic,
        int ilen,
        int mask,
        int bits,
        int restriction,
        @NotNull Map<String, Operand> operands
) implements IntPredicate {

    @Override
    @Contract(pure = true)
    public boolean test(final int value) {
        return bits == (value & mask)
               && operands.values()
                          .stream()
                          .noneMatch(operand -> operand.excludes(value));
    }

    @Contract(pure = true)
    public int get(final @NotNull String operand, final int value) {
        if (!operands.containsKey(operand))
            throw new IllegalArgumentException("operand name '%s'".formatted(operand));
        return operands.get(operand).extract(value);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return "%s [%d -> %08x & %08x] %s".formatted(mnemonic, ilen, bits, mask, operands);
    }
}
