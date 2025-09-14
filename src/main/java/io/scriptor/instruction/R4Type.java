package io.scriptor.instruction;

import io.scriptor.Definition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class R4Type extends Instruction {

    private final Definition definition;

    public R4Type(final int data) {
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
        return "%s:R4(rs3=%02x func2=%02x rs2=%02x rs1=%02x func3=%02x rd=%02x opcode=%02x)"
                .formatted(def(),
                           rs3(),
                           func2(),
                           rs2(),
                           rs1(),
                           func3(),
                           rd(),
                           opcode());
    }
}
