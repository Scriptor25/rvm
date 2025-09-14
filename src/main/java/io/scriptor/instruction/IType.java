package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class IType extends Instruction {

    private final Definition definition;

    public IType(final int data) {
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
        return (data >> 20) & 0b111111111111;
    }

    @Override
    public @NotNull Definition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:I(imm=%02x rs1=%02x func3=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rs1(),
                           func3(),
                           rd(),
                           opcode());
    }
}
