package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

public interface CSRFile {

    void reset();

    void dump(final @NotNull PrintStream out);

    int getw(final int addr);

    /**
     * read a 4-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    int getw(final int addr, final int priv);

    int getwu(final int addr);

    int getwu(final int addr, final int priv);

    long getd(final int addr);

    /**
     * read an 8-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    long getd(final int addr, int priv);

    void putw(final int addr, final int val);

    /**
     * write a 4-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    void putw(final int addr, final int priv, final int val);

    void putwu(final int addr, final int val);

    void putwu(final int addr, final int priv, final int val);

    void putd(final int addr, final long val);

    /**
     * write an 8-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    void putd(final int addr, final int priv, final long val);
}
