package io.scriptor.type;

import io.scriptor.InstructionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class R4Type extends Instruction {

    private final InstructionDefinition definition;

    public R4Type(final int data) {
        super(data);

        definition = Arrays.stream(InstructionDefinition.values())
                           .filter(definition -> definition.getType() == R4Type.class)
                           .filter(definition -> definition.getOpcode() == opcode())
                           .filter(definition -> definition.getFunc2() == func2())
                           .filter(definition -> definition.getFunc3() == func3())
                           .findAny()
                           .orElseThrow();
    }

    @Override
    public @NotNull InstructionDefinition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:R4(rs3=%02x func2=%02x rs2=%02x rs1=%02x func3=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           rs3(),
                           func2(),
                           rs2(),
                           rs1(),
                           func3(),
                           rd(),
                           opcode());
    }
}
