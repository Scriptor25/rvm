package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

public class CompressedInstruction extends Instruction {

    public static int inflateRegister(final int rvc) {
        return rvc + 8;
    }

    public CompressedInstruction(final int data, final @NotNull Definition definition) {
        super(data, definition);
    }

    @Override
    public int opcode() {
        return data & 0b11;
    }

    @Override
    public int rd() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int rs1() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int rs2() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int rs3() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "C.?(opcode=%02x)".formatted(opcode());
    }
}
