package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CBType extends CompressedInstruction {

    public CBType(final int data, final @NotNull Definition definition) {
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

    // TODO: imm encoding? [12:10], [6:2]
}
