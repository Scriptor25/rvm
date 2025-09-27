package io.scriptor.impl;

import io.scriptor.machine.GPRFile;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

public final class GPRFile64 implements GPRFile {

    private final long[] values = new long[32];

    @Override
    public void reset() {
        Arrays.fill(values, 0);
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        for (int i = 0; i < values.length; ++i) {
            out.printf("x%-2d: %016x  ", i, values[i]);

            if ((i + 1) % 4 == 0) {
                out.println();
            }
        }
    }

    @Override
    public int getw(final int reg) {
        if (reg == 0) {
            return 0;
        }

        return (int) (values[reg] & 0xFFFFFFFFL);
    }

    @Override
    public void putw(final int reg, final int val) {
        if (reg == 0) {
            return;
        }

        values[reg] = Integer.toUnsignedLong(val);
    }

    @Override
    public long getd(final int reg) {
        if (reg == 0) {
            return 0L;
        }

        return values[reg];
    }

    @Override
    public void putd(final int reg, final long val) {
        if (reg == 0) {
            return;
        }

        values[reg] = val;
    }
}
