package io.scriptor.impl;

import io.scriptor.machine.CSRFile;
import io.scriptor.machine.CSRMeta;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.scriptor.isa.CSR.readonly;
import static io.scriptor.isa.CSR.unprivileged;

public final class CSRFile64 implements CSRFile {

    private final Map<Integer, CSRMeta> metadata = new HashMap<>();

    private final long[] values = new long[0x1000];
    private final boolean[] present = new boolean[0x1000];

    @Override
    public void reset() {
        Arrays.fill(values, 0);
        Arrays.fill(present, false);
    }

    @Override
    public void define(final int addr) {
        metadata.put(addr, new CSRMeta(~0L, -1, null, null));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final long mask) {
        metadata.put(addr, new CSRMeta(mask, -1, null, null));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final long mask, final int base) {
        metadata.put(addr, new CSRMeta(mask, base, null, null));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final long mask, final int base, final long val) {
        metadata.put(addr, new CSRMeta(mask, base, null, null));
        present[addr] = true;
        values[addr] = val & mask;
    }

    @Override
    public void defineVal(final int addr, final long val) {
        metadata.put(addr, new CSRMeta(~0L, -1, null, null));
        present[addr] = true;
        values[addr] = val;
    }

    @Override
    public void define(final int addr, final @NotNull LongSupplier get) {
        metadata.put(addr, new CSRMeta(~0L, -1, get, null));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final @NotNull LongSupplier get, final @NotNull LongConsumer set) {
        metadata.put(addr, new CSRMeta(~0L, -1, get, set));
        present[addr] = true;
        values[addr] = 0L;
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
    public int getw(final int addr, final int priv) {
        return (int) getd(addr, priv);
    }

    @Override
    public int getwu(final int addr, final int priv) {
        return (int) (getd(addr, priv) & 0xFFFFFFFFL);
    }

    @Override
    public long getd(int addr, final int priv) {
        if (!present[addr]) {
            Log.error("read csr addr=%03x, priv=%x: not present", addr, priv);
            throw new TrapException(0x02, addr);
        }

        if (unprivileged(addr, priv)) {
            Log.error("read csr addr=%03x, priv=%x: unprivileged", addr, priv);
            throw new TrapException(0x02, addr);
        }

        final var meta = metadata.get(addr);
        final var mask = meta.mask();

        if (meta.get() != null) {
            return meta.get().getAsLong() & mask;
        }

        for (int base; present[addr] && (base = metadata.get(addr).base()) >= 0; ) {
            addr = base;
        }

        if (!present[addr]) {
            Log.error("subsequent read csr addr=%03x: not present", addr);
            throw new TrapException(0x02, addr);
        }

        return values[addr] & mask;
    }

    @Override
    public void putw(final int addr, final int priv, final int val) {
        putd(addr, priv, val);
    }

    @Override
    public void putwu(final int addr, final int priv, final int val) {
        putd(addr, priv, Integer.toUnsignedLong(val));
    }

    @Override
    public void putd(int addr, final int priv, final long val) {
        if (!present[addr]) {
            Log.error("write csr addr=%03x, priv=%x, val=%x: not present", addr, priv, val);
            throw new TrapException(0x02, addr);
        }

        if (unprivileged(addr, priv)) {
            Log.error("write csr addr=%03x, priv=%x, val=%x: unprivileged", addr, priv, val);
            return;
        }

        if (readonly(addr)) {
            Log.error("write csr addr=%03x, priv=%x, val=%x: read-only", addr, priv, val);
            throw new TrapException(0x02, addr);
        }

        final var meta = metadata.get(addr);
        final var mask = meta.mask();

        if (meta.get() != null) {
            if (meta.set() == null) {
                Log.error("write csr addr=%03x, priv=%x, val=%x: read-only", addr, priv, val);
                throw new TrapException(0x02, addr);
            }
            meta.set().accept(val & mask);
            throw new TrapException(0x02, addr);
        }

        for (int base; present[addr] && (base = metadata.get(addr).base()) >= 0; ) {
            addr = base;
        }

        if (!present[addr]) {
            Log.error("subsequent write csr addr=%03x, val=%x: not present", addr, val);
            throw new TrapException(0x02, addr);
        }

        values[addr] = (values[addr] & ~mask) | (val & mask);
    }
}
