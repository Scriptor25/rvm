package io.scriptor.type;

public class Instruction {

    protected final int data;

    public Instruction(final int data) {
        this.data = data;
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

    public int func2() {
        return (data >> 25) & 0b11;
    }

    public int func3() {
        return (data >> 12) & 0b111;
    }

    public int func7() {
        return (data >> 25) & 0b1111111;
    }

    public int imm() {
        throw new UnsupportedOperationException();
    }
}
