package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public interface ControlStatusRegisterFile extends Device {

    void define(final int addr);

    void define(final int addr, final long mask);

    void define(final int addr, final long mask, final int base);

    void define(final int addr, final long mask, final int base, final long value);

    void defineVal(final int addr, final long val);

    void define(final int addr, final @NotNull LongSupplier get);

    void define(final int addr, final @NotNull LongSupplier get, final @NotNull LongConsumer set);

    /**
     * read a 4-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    int getw(final int addr, final int priv);

    int getwu(final int addr, final int priv);

    /**
     * read an 8-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    long getd(final int addr, int priv);

    /**
     * write a 4-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    void putw(final int addr, final int priv, final int val);

    void putwu(final int addr, final int priv, final int val);

    /**
     * write an 8-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    void putd(final int addr, final int priv, final long val);
}
