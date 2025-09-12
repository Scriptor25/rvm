package io.scriptor.type;

public class UType extends Instruction {

    public UType(final int data) {
        super(data);
    }

    @Override
    public int imm() {
        return ((data >> 12) & 0b11111111111111111111) << 12;
    }
}
