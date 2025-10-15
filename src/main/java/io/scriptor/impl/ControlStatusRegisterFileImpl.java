package io.scriptor.impl;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.ObjectArrayList;
import io.scriptor.machine.ControlStatusRegisterFile;
import io.scriptor.machine.ControlStatusRegisterMeta;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.scriptor.isa.CSR.readonly;
import static io.scriptor.isa.CSR.unprivileged;

public final class ControlStatusRegisterFileImpl implements ControlStatusRegisterFile {

    private final Machine machine;
    private final IntObjectMap<ControlStatusRegisterMeta> metadata = new IntObjectHashMap<>();

    private final long[] values = new long[0x1000];
    private final boolean[] present = new boolean[0x1000];

    public ControlStatusRegisterFileImpl(final @NotNull Machine machine) {
        this.machine = machine;
    }

    @Override
    public @NotNull Machine machine() {
        return machine;
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
    public void reset() {
        Arrays.fill(values, 0);
        Arrays.fill(present, false);
    }

    @Override
    public void define(final int addr) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(~0L,
                                                   -1,
                                                   null,
                                                   null,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final long mask) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(mask,
                                                   -1,
                                                   null,
                                                   null,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final long mask, final int base) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(mask,
                                                   base,
                                                   null,
                                                   null,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final long mask, final int base, final long val) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(mask,
                                                   base,
                                                   null,
                                                   null,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = val & mask;
    }

    @Override
    public void defineVal(final int addr, final long val) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(~0L,
                                                   -1,
                                                   null,
                                                   null,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = val;
    }

    @Override
    public void define(final int addr, final @NotNull LongSupplier get) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(~0L,
                                                   -1,
                                                   get,
                                                   null,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void define(final int addr, final @NotNull LongSupplier get, final @NotNull LongConsumer set) {
        metadata.put(addr,
                     new ControlStatusRegisterMeta(~0L,
                                                   -1,
                                                   get,
                                                   set,
                                                   new ObjectArrayList<>(),
                                                   new ObjectArrayList<>()));
        present[addr] = true;
        values[addr] = 0L;
    }

    @Override
    public void hookGet(final int addr, final @NotNull LongConsumer hook) {
        metadata.get(addr).getHooks().add(hook);
    }

    @Override
    public void hookSet(final int addr, final @NotNull LongConsumer hook) {
        metadata.get(addr).setHooks().add(hook);
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
            throw new TrapException(0x02L, addr, "read csr addr=%03x, priv=%x: not present", addr, priv);
        }

        if (unprivileged(addr, priv)) {
            throw new TrapException(0x02L, addr, "read csr addr=%03x, priv=%x: unprivileged", addr, priv);
        }

        final var meta = metadata.get(addr);
        final var mask = meta.mask();

        if (meta.get() != null) {
            final var value = meta.get().getAsLong() & mask;
            for (final var hook : meta.getHooks()) {
                hook.value.accept(value);
            }
            return value;
        }

        for (int base; present[addr] && (base = metadata.get(addr).base()) >= 0; ) {
            addr = base;
        }

        if (!present[addr]) {
            throw new TrapException(0x02L, addr, "subsequent read csr addr=%03x: not present", addr);
        }

        final var value = values[addr] & mask;
        for (final var hook : meta.getHooks()) {
            hook.value.accept(value);
        }
        return value;
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
            throw new TrapException(0x02L, addr, "write csr addr=%03x, priv=%x, val=%x: not present", addr, priv, val);
        }

        if (unprivileged(addr, priv)) {
            Log.error("write csr addr=%03x, priv=%x, val=%x: unprivileged", addr, priv, val);
            return;
        }

        if (readonly(addr)) {
            throw new TrapException(0x02L, addr, "write csr addr=%03x, priv=%x, val=%x: read-only", addr, priv, val);
        }

        final var meta = metadata.get(addr);
        final var mask = meta.mask();

        if (meta.get() != null) {
            if (meta.set() == null) {
                throw new TrapException(0x02L, addr, "write csr addr=%03x, priv=%x, val=%x: read-only", addr, priv, val);
            }
            final var value = val & mask;
            for (final var hook : meta.setHooks()) {
                hook.value.accept(value);
            }
            meta.set().accept(value);
            return;
        }

        for (int base; present[addr] && (base = metadata.get(addr).base()) >= 0; ) {
            addr = base;
        }

        if (!present[addr]) {
            throw new TrapException(0x02L, addr, "subsequent write csr addr=%03x, val=%x: not present", addr, val);
        }

        final var value = (values[addr] & ~mask) | (val & mask);
        for (final var hook : meta.setHooks()) {
            hook.value.accept(value);
        }
        values[addr] = value;
    }
}
