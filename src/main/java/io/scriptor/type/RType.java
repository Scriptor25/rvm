package io.scriptor.type;

public class RType extends Instruction {

    public RType(final int data) {
        super(data);
    }

    @Override
    public String toString() {
        return "%02x | %01x | %01x | %01x | %01x | %02x".formatted(func7(), rs2(), rs1(), func3(), rd(), opcode());
    }
}
