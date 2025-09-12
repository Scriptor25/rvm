package io.scriptor.type;

public class BType extends Instruction {

    public BType(final int data) {
        super(data);
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 12)
               | (((data >> 7) & 0b1) << 11)
               | (((data >> 25) & 0b111111) << 5)
               | (((data >> 8) & 0b1111) << 1);
    }
}
