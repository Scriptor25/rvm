package io.scriptor.impl;

import io.scriptor.machine.GeneralPurposeRegisterFile;
import io.scriptor.machine.Hart;
import io.scriptor.machine.Machine;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

public final class GeneralPurposeRegisterFileImpl implements GeneralPurposeRegisterFile {

    private final Hart hart;
    private final long[] values = new long[32];

    public GeneralPurposeRegisterFileImpl(final @NotNull Hart hart) {
        this.hart = hart;
    }

    @Override
    public @NotNull Machine machine() {
        return hart.machine();
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
    public void reset() {
        Arrays.fill(values, 0);
    }

    @Override
    public int getw(final int reg) {
        return (int) getd(reg);
    }

    @Override
    public int getwu(final int reg) {
        return (int) (getd(reg) & 0xFFFFFFFFL);
    }

    @Override
    public void putw(final int reg, final int val) {
        putd(reg, val);
    }

    @Override
    public void putwu(final int reg, final int val) {
        putd(reg, Integer.toUnsignedLong(val));
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
