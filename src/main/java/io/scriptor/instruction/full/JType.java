package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class JType extends Instruction {

    public JType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 20)
               | (((data >> 12) & 0b11111111) << 12)
               | (((data >> 20) & 0b1) << 11)
               | (((data >> 21) & 0b1111111111) << 1);
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
