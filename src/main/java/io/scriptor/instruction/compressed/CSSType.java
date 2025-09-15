package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CSSType extends CompressedInstruction {

    public CSSType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int rs2() {
        return (data >> 2) & 0b11111;
    }

    // TODO: imm encoding? [12:7]
}
