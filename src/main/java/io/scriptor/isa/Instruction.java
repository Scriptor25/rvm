package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
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

    public int decode(final int instruction, final @NotNull String label) {
        for (final var operand : operands) {
            if (operand.label().equals(label)) {
                return operand.extract(instruction);
            }
        }
        throw new NoSuchElementException(label);
    }

    public void decode(final int instruction, final int @NotNull [] values, final @NotNull String label0) {
        if (values.length < 1) {
            throw new IllegalArgumentException();
        }

        values[0] = decode(instruction, label0);
    }

    public void decode(
            final int instruction,
            final int @NotNull [] values,
            final @NotNull String label0,
            final @NotNull String label1
    ) {
        if (values.length < 2) {
            throw new IllegalArgumentException();
        }

        values[0] = decode(instruction, label0);
        values[1] = decode(instruction, label1);
    }

    public void decode(
            final int instruction,
            final int @NotNull [] values,
            final @NotNull String label0,
            final @NotNull String label1,
            final @NotNull String label2
    ) {
        if (values.length < 3) {
            throw new IllegalArgumentException();
        }

        values[0] = decode(instruction, label0);
        values[1] = decode(instruction, label1);
        values[2] = decode(instruction, label2);
    }

    @Override
    public @NotNull String toString() {
        return "%s [%d -> %08x & %08x] %s".formatted(mnemonic, ilen, bits, mask, operands);
    }
}
