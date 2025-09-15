package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CRType extends CompressedInstruction {

    public CRType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    private int rd_rs1() {
        return (data >> 7) & 0b11111;
    }

    @Override
    public int rd() {
        return rd_rs1();
    }

    @Override
    public int rs1() {
        return rd_rs1();
    }

    @Override
    public int rs2() {
        return (data >> 2) & 0b11111;
    }
}
