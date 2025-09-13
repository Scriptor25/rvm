package io.scriptor.type;

import io.scriptor.InstructionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class SType extends Instruction {

    private final InstructionDefinition definition;

    public SType(final int data) {
        super(data);

        definition = Arrays.stream(InstructionDefinition.values())
                           .filter(definition -> definition.getType() == SType.class)
                           .filter(definition -> definition.getOpcode() == opcode())
                           .filter(definition -> definition.getFunc3() == func3())
                           .findAny()
                           .orElseThrow();
    }

    @Override
    public int imm() {
        return (((data >> 25) & 0b1111111) << 5)
               | ((data >> 7) & 0b11111);
    }

    @Override
    public @NotNull InstructionDefinition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:S(imm=%02x rs2=%02x rs1=%02x func3=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rs2(),
                           rs1(),
                           func3(),
                           opcode());
    }
}
