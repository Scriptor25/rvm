package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

public interface GPRFile {

    void reset();

    void dump(final @NotNull PrintStream out);

    /**
     * read a 4-byte value from a general purpose register.
     *
     * @param reg source register
     */
    int getw(final int reg);

    /**
     * write a 4-byte value to a general purpose register.
     *
     * @param reg destination register
     * @param val source value
     */
    void putw(final int reg, final int val);

    /**
     * read an 8-byte value from a general purpose register.
     *
     * @param reg source register
     */
    long getd(final int reg);

    /**
     * write an 8-byte value to a general purpose register.
     *
     * @param reg destination register
     * @param val source value
     */
    void putd(final int reg, final long val);
}
