package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CIWType extends CompressedInstruction {

    public CIWType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int rd() {
        return inflateRegister((data >> 2) & 0b111);
    }

    // TODO: imm encoding? [12:5]
}
