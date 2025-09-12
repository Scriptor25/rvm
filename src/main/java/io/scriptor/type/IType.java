package io.scriptor.type;

public class IType extends Instruction {

    public IType(final int data) {
        super(data);
    }

    @Override
    public int imm() {
        return (data >> 20) & 0b111111111111;
    }
}
