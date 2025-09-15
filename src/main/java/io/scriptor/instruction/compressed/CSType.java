package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CSType extends CompressedInstruction {

    public CSType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int rs1() {
        return inflateRegister((data >> 7) & 0b111);
    }

    @Override
    public int rs2() {
        return inflateRegister((data >> 2) & 0b111);
    }

    // TODO: imm encoding? [12:10], [6:5]
}
