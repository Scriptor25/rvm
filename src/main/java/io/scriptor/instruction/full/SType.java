package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class SType extends Instruction {

    public SType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int imm() {
        return (((data >> 25) & 0b1111111) << 5)
               | ((data >> 7) & 0b11111);
    }

    @Override
    public String toString() {
        return "%s:S(imm=%02x rs2=%02x rs1=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rs2(),
                           rs1(),
                           opcode());
    }
}
