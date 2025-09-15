package io.scriptor.instruction.compressed;

import io.scriptor.Definition;
import io.scriptor.instruction.CompressedInstruction;
import org.jetbrains.annotations.NotNull;

public final class CJType extends CompressedInstruction {

    public CJType(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    // TODO: imm encoding? [12:2]
}
