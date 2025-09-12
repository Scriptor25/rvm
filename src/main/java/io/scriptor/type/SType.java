package io.scriptor.type;

public class SType extends Instruction {

    public SType(final int data) {
        super(data);
    }

    @Override
    public int imm() {
        return (((data >> 25) & 0b1111111) << 5)
               | ((data >> 7) & 0b11111);
    }
}
