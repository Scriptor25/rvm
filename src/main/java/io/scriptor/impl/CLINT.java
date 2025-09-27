package io.scriptor.impl;

import io.scriptor.machine.Machine;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

import static io.scriptor.isa.CSR.CSR_M;
import static io.scriptor.isa.CSR.mip;

public final class CLINT {

    private final Machine machine;
    private final int count;
    private final int[] msip;
    private final long[] mtimecmp;
    private long mtime;

    public CLINT(final @NotNull Machine machine, final int hartCount) {
        this.machine = machine;
        this.count = hartCount;
        this.msip = new int[hartCount];
        this.mtimecmp = new long[hartCount];
        this.mtime = 0;
    }

    public void dump(final @NotNull PrintStream out) {
        out.printf("clint: mtime=%x%n", mtime);
        for (int i = 0; i < count; ++i) {
            out.printf("#%-2d | msip=%x mtimecmp=%x%n", i, msip[i], mtimecmp[i]);
        }
    }

    public void reset() {
        Arrays.fill(msip, 0);
        Arrays.fill(mtimecmp, ~0L);

        mtime = 0L;
    }

    public void step() {
        mtime++;

        for (int id = 0; id < count; ++id) {
            final var timerPending    = Long.compareUnsigned(mtime, mtimecmp[id]) >= 0;
            final var softwarePending = msip[id] != 0L;

            var pending = 0L;
            if (timerPending)
                pending |= (1L << 7);
            if (softwarePending)
                pending |= (1L << 3);

            final var hart = machine.getHart(id);
            hart.csrFile().putd(mip, CSR_M, pending);
            if (pending != 0 && hart.wfi()) {
                hart.wake();
            }
        }
    }

    public void msip(final int id, final int value) {
        msip[id] = value;
    }

    public int msip(final int id) {
        return msip[id];
    }

    public void mtimecmp(final int id, final long value) {
        mtimecmp[id] = value;
    }

    public long mtimecmp(final int id) {
        return mtimecmp[id];
    }

    public void mtime(final long value) {
        mtime = value;
    }

    public long mtime() {
        return mtime;
    }
}
