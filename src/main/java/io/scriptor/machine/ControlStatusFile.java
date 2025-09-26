package io.scriptor.machine;

import org.jetbrains.annotations.Contract;

public interface ControlStatusFile {

    /**
     * read a 4-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    int getw(final int addr, final int priv);

    /**
     * write a 4-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    @Contract(mutates = "this")
    void putw(final int addr, final int priv, final int val);

    /**
     * read an 8-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    long getd(final int addr, int priv);

    /**
     * write an 8-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    @Contract(mutates = "this")
    void putd(final int addr, final int priv, final long val);

    @Contract(mutates = "this")
    void reset();

    @Contract(mutates = "this")
    void putw(final int addr, final int val);

    @Contract(mutates = "this")
    void putd(final int addr, final long val);

    int getw(final int addr);

    long getd(final int addr);
}
