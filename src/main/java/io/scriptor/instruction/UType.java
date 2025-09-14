package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class UType extends Instruction {

    private final Definition definition;

    public UType(final int data) {
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
        return ((data >> 12) & 0b11111111111111111111) << 12;
    }

    @Override
    public @NotNull Definition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:U(imm=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rd(),
                           opcode());
    }
}
