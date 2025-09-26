package io.scriptor.impl;

import io.scriptor.machine.ControlStatusFile;
import io.scriptor.util.Log;

import java.util.Arrays;

import static io.scriptor.isa.CSR.readonly;
import static io.scriptor.isa.CSR.unprivileged;

public class ControlStatusFile64 implements ControlStatusFile {

    private final long[] csrs = new long[0x1000];
    private final boolean[] csrsp = new boolean[0x1000];

    @Override
    public int getw(final int addr, final int priv) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putw(final int addr, final int priv, final int val) {
        throw new UnsupportedOperationException();
    }


    @Override
    public long getd(final int addr, final int priv) {
        if (!csrsp[addr]) {
            Log.warn("read csr[addr=%03x, priv=%x]: not present", addr, priv);
            return 0L;
        }

        if (unprivileged(addr, priv)) {
            Log.warn("read csr[addr=%03x, priv=%x]: unprivileged", addr, priv);
            return 0L;
        }

        return csrs[addr];
    }

    @Override
    public void putd(final int addr, final int priv, final long val) {
        if (!csrsp[addr]) {
            Log.warn("write csr[addr=%03x, priv=%x] = val=%x: not present", addr, priv, val);
            return;
        }

        if (unprivileged(addr, priv)) {
            Log.warn("write csr[addr=%03x, priv=%x] = val=%x: unprivileged", addr, priv, val);
            return;
        }

        if (readonly(addr)) {
            Log.warn("write csr[addr=%03x, priv=%x] = val=%x: read-only", addr, priv, val);
            return;
        }

        csrs[addr] = val;
    }

    @Override
    public void reset() {
        Arrays.fill(csrs, 0);
        Arrays.fill(csrsp, false);
    }

    @Override
    public void putw(final int addr, final int val) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putd(final int addr, final long val) {
        csrs[addr] = val;
        csrsp[addr] = true;
    }

    @Override
    public int getw(final int addr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getd(final int addr) {
        return csrs[addr];
    }
}
