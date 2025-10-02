package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

public interface Device {

    void dump(@NotNull PrintStream out);

    void reset();

    default void step() {
    }
}
