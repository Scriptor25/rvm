package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class RType extends Instruction {

    public RType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public String toString() {
        return "%s:R(rs2=%02x rs1=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           rs2(),
                           rs1(),
                           rd(),
                           opcode());
    }
}
