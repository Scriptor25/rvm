package io.scriptor.impl;

import io.scriptor.machine.FloatingPointRegisterFile;
import io.scriptor.machine.Hart;
import io.scriptor.machine.Machine;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

public final class FloatingPointRegisterFileImpl implements FloatingPointRegisterFile {

    private final Hart hart;
    private final long[] values = new long[32];

    public FloatingPointRegisterFileImpl(final @NotNull Hart hart) {
        this.hart = hart;
    }

    @Override
    public @NotNull Machine machine() {
        return hart.machine();
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        for (int i = 0; i < values.length; ++i) {
            out.printf("f%-2d: %016x ", i, values[i]);

            if ((i + 1) % 4 == 0) {
                out.println();
            }
        }
    }

    @Override
    public void reset() {
        Arrays.fill(values, 0L);
    }

    @Override
    public float getf(final int reg) {
        return Float.intBitsToFloat(getfr(reg));
    }

    @Override
    public double getd(final int reg) {
        return Double.longBitsToDouble(getdr(reg));
    }

    @Override
    public void putf(final int reg, final float val) {
        putfr(reg, Float.floatToRawIntBits(val));
    }

    @Override
    public void putd(final int reg, final double val) {
        putdr(reg, Double.doubleToRawLongBits(val));
    }

    @Override
    public int getfr(final int reg) {
        if (reg == 0) {
            return 0;
        }
        return (int) (values[reg] & 0xFFFFFFFFL);
    }

    @Override
    public long getdr(final int reg) {
        if (reg == 0) {
            return 0L;
        }
        return values[reg];
    }

    @Override
    public void putfr(final int reg, final int val) {
        if (reg == 0) {
            return;
        }
        values[reg] = val & 0xFFFFFFFFL;
    }

    @Override
    public void putdr(final int reg, final long val) {
        if (reg == 0) {
            return;
        }
        values[reg] = val;
    }
}
