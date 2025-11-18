package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public interface CSRFile extends Device {

    void define(int addr);

    void define(int addr, long mask);

    void define(int addr, long mask, int base);

    void define(int addr, long mask, int base, long value);

    void defineVal(int addr, long val);

    void define(int addr, @NotNull LongSupplier get);

    void define(int addr, @NotNull LongSupplier get, @NotNull LongConsumer set);

    void hookGet(int addr, @NotNull LongConsumer hook);

    void hookSet(int addr, @NotNull LongConsumer hook);

    /**
     * read a 4-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    int getw(int addr, int priv);

    int getwu(int addr, int priv);

    /**
     * read an 8-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv privilege level
     */
    long getd(int addr, int priv);

    /**
     * write a 4-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    void putw(int addr, int priv, int val);

    void putwu(int addr, int priv, int val);

    /**
     * write an 8-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv privilege level
     * @param val  source value
     */
    void putd(int addr, int priv, long val);
}
