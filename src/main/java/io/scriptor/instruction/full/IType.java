package io.scriptor.instruction.full;

import io.scriptor.Definition;
import io.scriptor.instruction.Instruction;
import org.jetbrains.annotations.NotNull;

public final class IType extends Instruction {

    public IType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int imm() {
        return (data >> 20) & 0b111111111111;
    }

    @Override
    public String toString() {
        return "%s:I(imm=%02x rs1=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rs1(),
                           rd(),
                           opcode());
    }
}
