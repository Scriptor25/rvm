package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CAType extends CompressedInstruction {

    public CAType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    private int rd_rs1() {
        return inflateRegister((data >> 7) & 0b111);
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
        return inflateRegister((data >> 2) & 0b111);
    }
}
