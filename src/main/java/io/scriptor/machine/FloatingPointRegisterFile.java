package io.scriptor.machine;

public interface FloatingPointRegisterFile extends Device {

    float getf(final int reg);

    double getd(final int reg);

    void putf(final int reg, final float val);

    void putd(final int reg, final double val);

    int getfr(final int reg);

    long getdr(final int reg);

    void putfr(final int reg, final int val);

    void putdr(final int reg, final long val);
}
