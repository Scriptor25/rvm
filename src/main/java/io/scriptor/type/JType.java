package io.scriptor.type;

import io.scriptor.InstructionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class JType extends Instruction {

    private final InstructionDefinition definition;

    public JType(final int data) {
        super(data);

        definition = Arrays.stream(InstructionDefinition.values())
                           .filter(definition -> definition.getType() == JType.class)
                           .filter(definition -> definition.getOpcode() == opcode())
                           .findAny()
                           .orElseThrow();
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 20)
               | (((data >> 12) & 0b11111111) << 12)
               | (((data >> 20) & 0b1) << 11)
               | (((data >> 21) & 0b1111111111) << 1);
    }

    @Override
    public @NotNull InstructionDefinition def() {
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
