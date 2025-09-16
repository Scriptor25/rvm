package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

public class Instruction {

    @FunctionalInterface
    public interface Constructor<T extends Instruction> {

        @NotNull T create(final int data, final @NotNull Definition definition);
    }

    protected final int data;
    private final Definition definition;

    public Instruction(final int data, final @NotNull Definition definition) {
        this.data = data;
        this.definition = definition;
    }

    public int data() {
        return data;
    }

    public final @NotNull Definition def() {
        return definition;
    }

    public int opcode() {
        return data & 0b1111111;
    }

    public int rd() {
        return (data >> 7) & 0b11111;
    }

    public int rs1() {
        return (data >> 15) & 0b11111;
    }

    public int rs2() {
        return (data >> 20) & 0b11111;
    }

    public int rs3() {
        return (data >> 27) & 0b11111;
    }

    public int imm() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "?(opcode=%02x)".formatted(opcode());
    }
}
