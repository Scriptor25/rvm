package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class JType extends Instruction {

    private final Definition definition;

    public JType(final int data) {
        super(data);

        final var definitions = Arrays.stream(Definition.values())
                                      .filter(definition -> definition.filter(this))
                                      .toList();

        if (definitions.size() != 1) {
            throw new IllegalStateException();
        }

        definition = definitions.getFirst();
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 20)
               | (((data >> 12) & 0b11111111) << 12)
               | (((data >> 20) & 0b1) << 11)
               | (((data >> 21) & 0b1111111111) << 1);
    }

    @Override
    public @NotNull Definition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:J(imm=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rd(),
                           opcode());
    }
}
