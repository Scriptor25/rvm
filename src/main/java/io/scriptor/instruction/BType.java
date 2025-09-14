package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class BType extends Instruction {

    private final Definition definition;

    public BType(final int data) {
        super(data);

        final var definitions = Arrays.stream(Definition.values())
                                      .filter(definition -> definition.filter(this))
                                      .toList();

        if (definitions.size() != 1) {
            throw new IllegalStateException();
        }

        definition = definitions.getFirst();
    }

    @Override
    public int imm() {
        return (((data >> 31) & 0b1) << 12)
               | (((data >> 7) & 0b1) << 11)
               | (((data >> 25) & 0b111111) << 5)
               | (((data >> 8) & 0b1111) << 1);
    }


    @Override
    public @NotNull Definition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:B(imm=%02x rs2=%02x rs1=%02x func3=%02x opcode=%02x)"
                .formatted(def(),
                           imm(),
                           rs2(),
                           rs1(),
                           func3(),
                           opcode());
    }
}
