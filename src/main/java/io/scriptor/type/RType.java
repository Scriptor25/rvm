package io.scriptor.type;

import io.scriptor.InstructionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class RType extends Instruction {

    private final InstructionDefinition definition;

    public RType(final int data) {
        super(data);

        definition = Arrays.stream(InstructionDefinition.values())
                           .filter(definition -> definition.getType() == RType.class)
                           .filter(definition -> definition.getOpcode() == opcode())
                           .filter(definition -> definition.getFunc3() == func3())
                           .filter(definition -> definition.getFunc7() == func7())
                           .findAny()
                           .orElseThrow();
    }

    @Override
    public @NotNull InstructionDefinition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:R(func7=%02x rs2=%02x rs1=%02x func3=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           func7(),
                           rs2(),
                           rs1(),
                           func3(),
                           rd(),
                           opcode());
    }
}
