package io.scriptor.impl;

import io.scriptor.machine.CSRFile;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

import static io.scriptor.isa.CSR.readonly;
import static io.scriptor.isa.CSR.unprivileged;

public final class CSRFile64 implements CSRFile {

    private final long[] values = new long[0x1000];
    private final boolean[] present = new boolean[0x1000];

    @Override
    public void reset() {
        Arrays.fill(values, 0);
        Arrays.fill(present, false);
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        int j = 0;
        for (int i = 0; i < values.length; ++i) {
            if (!present[i]) {
                continue;
            }

            out.printf("%03x: %016x  ", i, values[i]);

            if (++j % 4 == 0) {
                out.println();
            }
        }
        if (j % 4 != 0) {
            out.println();
        }
    }

    @Override
    public int getw(final int addr) {
        return (int) getd(addr);
    }

    @Override
    public int getw(final int addr, final int priv) {
        return (int) getd(addr, priv);
    }

    @Override
    public int getwu(int addr) {
        return (int) (getd(addr) & 0xFFFFFFFFL);
    }

    @Override
    public int getwu(final int addr, final int priv) {
        return (int) (getd(addr, priv) & 0xFFFFFFFFL);
    }

    @Override
    public long getd(final int addr) {
        return values[addr];
    }

    @Override
    public long getd(final int addr, final int priv) {
        if (!present[addr]) {
            Log.warn("read csr[addr=%03x, priv=%x]: not present", addr, priv);
            return 0L;
        }

        if (unprivileged(addr, priv)) {
            Log.warn("read csr[addr=%03x, priv=%x]: unprivileged", addr, priv);
            return 0L;
        }

        return values[addr];
    }

    @Override
    public void putw(final int addr, final int val) {
        putd(addr, val);
    }

    @Override
    public void putw(final int addr, final int priv, final int val) {
        putd(addr, priv, val);
    }

    @Override
    public void putwu(final int addr, final int val) {
        putd(addr, Integer.toUnsignedLong(val));
    }

    @Override
    public void putwu(final int addr, final int priv, final int val) {
        putd(addr, priv, Integer.toUnsignedLong(val));
    }

    @Override
    public void putd(final int addr, final long val) {
        values[addr] = val;
        present[addr] = true;
    }

    @Override
    public void putd(final int addr, final int priv, final long val) {
        if (!present[addr]) {
            Log.warn("write csr[addr=%03x, priv=%x] = %x: not present", addr, priv, val);
            return;
        }

        if (unprivileged(addr, priv)) {
            Log.warn("write csr[addr=%03x, priv=%x] = %x: unprivileged", addr, priv, val);
            return;
        }

        if (readonly(addr)) {
            Log.warn("write csr[addr=%03x, priv=%x] = %x: read-only", addr, priv, val);
            return;
        }

        values[addr] = val;
    }
}
