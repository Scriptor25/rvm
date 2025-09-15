package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class BType extends Instruction {

    public BType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 12)
               | (((data >> 7) & 0b1) << 11)
               | (((data >> 25) & 0b111111) << 5)
               | (((data >> 8) & 0b1111) << 1);
    }

    @Override
    public String toString() {
        return "%s:B(imm=%02x rs2=%02x rs1=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rs2(),
                           rs1(),
                           opcode());
    }
}
