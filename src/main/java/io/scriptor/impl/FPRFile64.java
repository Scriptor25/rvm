package io.scriptor.impl;

import io.scriptor.machine.FPRFile;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

public final class FPRFile64 implements FPRFile {

    private final double[] values = new double[32];

    @Override
    public void reset() {
        Arrays.fill(values, 0.0);
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        for (int i = 0; i < values.length; ++i) {
            out.printf("f%-2d: %-16f  ", i, values[i]);

            if ((i + 1) % 4 == 0) {
                out.println();
            }
        }
    }

    @Override
    public float getf(final int reg) {
        return (float) values[reg];
    }

    @Override
    public double getd(final int reg) {
        return values[reg];
    }

    @Override
    public void putf(final int reg, final float val) {
        values[reg] = val;
    }

    @Override
    public void putd(final int reg, final double val) {
        values[reg] = val;
    }

    @Override
    public int getfr(final int reg) {
        return Float.floatToRawIntBits((float) values[reg]);
    }

    @Override
    public long getdr(final int reg) {
        return Double.doubleToRawLongBits(values[reg]);
    }

    @Override
    public void putfr(final int reg, final int val) {
        values[reg] = Float.intBitsToFloat(val);
    }

    @Override
    public void putdr(final int reg, final long val) {
        values[reg] = Double.longBitsToDouble(val);
    }
}
