package io.scriptor.type;

import io.scriptor.InstructionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class IType extends Instruction {

    private final InstructionDefinition definition;

    public IType(final int data) {
        super(data);

        definition = Arrays.stream(InstructionDefinition.values())
                           .filter(definition -> definition.getType() == IType.class)
                           .filter(definition -> definition.getOpcode() == opcode())
                           .filter(definition -> definition.getFunc3() == func3())
                           .findAny()
                           .orElseThrow();
    }

    @Override
    public int imm() {
        return (data >> 20) & 0b111111111111;
    }

    @Override
    public @NotNull InstructionDefinition def() {
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
