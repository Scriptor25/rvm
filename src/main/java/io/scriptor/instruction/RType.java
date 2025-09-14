package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class RType extends Instruction {

    private final Definition definition;

    public RType(final int data) {
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
    public @NotNull Definition def() {
        return definition;
    }

    @Override
    public String toString() {
        return "%s:R(func7=%02x rs2=%02x rs1=%02x func3=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           func7(),
                           rs2(),
                           rs1(),
                           func3(),
                           rd(),
                           opcode());
    }
}
