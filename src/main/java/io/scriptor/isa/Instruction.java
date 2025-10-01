package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

import java.util.function.IntPredicate;

public record Instruction(
        @NotNull String mnemonic,
        int ilen,
        int mask,
        int bits,
        int restriction,
        @NotNull Operand[] operands
) implements IntPredicate {

    @Override
    public boolean test(final int value) {
        if (bits != (value & mask)) {
            return false;
        }
        for (final var operand : operands) {
            if (operand.excludes(value)) {
                return false;
            }
        }
        return true;
    }

    public void decode(final int instruction, final int @NotNull [] values, final @NotNull String... labels) {
        if (values.length < labels.length) {
            throw new IllegalArgumentException();
        }

        for (int i = 0; i < labels.length; ++i) {
            final var label = labels[i];

            var found = false;
            for (final var operand : operands) {
                if (operand.label().equals(label)) {
                    final var value = operand.extract(instruction);
                    values[i] = value;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException(label);
            }
        }
    }

    @Override
    public @NotNull String toString() {
        return "%s [%d -> %08x & %08x] %s".formatted(mnemonic, ilen, bits, mask, operands);
    }
}
