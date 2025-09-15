package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class UType extends Instruction {

    public UType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int imm() {
        return ((data >> 12) & 0b11111111111111111111) << 12;
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
