package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class R4Type extends Instruction {

    public R4Type(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public String toString() {
        return "%s:R4(rs3=%02x rs2=%02x rs1=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           rs3(),
                           rs2(),
                           rs1(),
                           rd(),
                           opcode());
    }
}
