package io.scriptor.type;

public class JType extends Instruction {

    public JType(final int data) {
        super(data);
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 20)
               | (((data >> 12) & 0b11111111) << 12)
               | (((data >> 20) & 0b1) << 11)
               | (((data >> 21) & 0b1111111111) << 1);
    }
}
