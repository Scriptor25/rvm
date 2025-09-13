package io.scriptor.type;

import io.scriptor.InstructionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class UType extends Instruction {

    private final InstructionDefinition definition;

    public UType(final int data) {
        super(data);

        definition = Arrays.stream(InstructionDefinition.values())
                           .filter(definition -> definition.getType() == UType.class)
                           .filter(definition -> definition.getOpcode() == opcode())
                           .findAny()
                           .orElseThrow();
    }

    @Override
    public int imm() {
        return ((data >> 12) & 0b11111111111111111111) << 12;
    }

    @Override
    public @NotNull InstructionDefinition def() {
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
