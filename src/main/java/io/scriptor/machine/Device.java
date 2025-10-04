package io.scriptor.machine;

import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.NodeBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

public interface Device {

    void dump(@NotNull PrintStream out);

    void reset();

    default void step() {
    }

    default void build(@NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
    }
}
