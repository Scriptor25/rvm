package io.scriptor.impl;

import io.scriptor.elf.SymbolTable;
import io.scriptor.io.IOStream;
import io.scriptor.io.LongByteBuffer;
import io.scriptor.machine.Hart;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class Machine64 implements Machine {

    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    private final SymbolTable symbols = new SymbolTable();
    private final long dram = 0x80000000L;
    private final LongByteBuffer memory;
    private long entry;
    private final CLINT clint;
    private final Hart[] harts;

    public Machine64(final long memory, final int harts) {
        this.memory = new LongByteBuffer(0x1000, memory);
        this.clint = new CLINT(this, harts);
        this.harts = new Hart[harts];
        for (int id = 0; id < harts; ++id) {
            this.harts[id] = new Hart64(this, id);
        }
    }

    @Override
    public @NotNull SymbolTable getSymbols() {
        return symbols;
    }

    @Override
    public @NotNull CLINT getCLINT() {
        return clint;
    }

    @Override
    public @NotNull Hart getHart(final int id) {
        return harts[id];
    }

    @Override
    public @NotNull Stream<Hart> getHarts() {
        return Arrays.stream(harts);
    }

    @Override
    public void reset() {
        clint.reset();
        for (final var hart : harts) {
            hart.reset(entry);
        }
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        clint.dump(out);
        for (int id = 0; id < harts.length; ++id) {
            out.printf("cpu@%d:%n", id);
            harts[id].dump(out);
        }
    }

    @Override
    public void step() {
        clint.step();

        for (final var hart : harts)
            hart.step();
    }

    public @NotNull Object acquireLock(final long address) {
        return locks.computeIfAbsent(address, _ -> new Object());
    }

    @Override
    public void setEntry(final long entry) {
        if (dram <= entry && entry < dram + memory.capacity()) {
            this.entry = entry;
            return;
        }

        throw new IllegalArgumentException("entry=%016x".formatted(entry));
    }

    @Override
    public void loadDirect(final @NotNull IOStream stream, final long address, final long size, final long allocate)
            throws IOException {
        final var remainder = allocate - size;

        if (0L <= address && address + allocate < memory.capacity()) {
            stream.read(memory, address, size);
            memory.fill(address + size, remainder, (byte) 0);
            return;
        }

        throw new IllegalArgumentException("address=%016x, size=%x, allocate=%x".formatted(address, size, allocate));
    }

    @Override
    public void loadSegment(
            final @NotNull IOStream stream,
            final long address,
            final long size,
            final long allocate
    ) throws IOException {
        final var remainder = allocate - size;

        if (dram <= address && address + allocate < dram + memory.capacity()) {
            final var dst = address - dram;
            stream.read(memory, dst, size);
            memory.fill(dst + size, remainder, (byte) 0);
            return;
        }

        throw new IllegalArgumentException("address=%016x, size=%x, allocate=%x".formatted(address, size, allocate));
    }

    @Override
    public long read(final long address, final int size, final boolean unsafe) {
        if (dram <= address && address + size < dram + memory.capacity()) {
            final var bytes = new byte[size];
            memory.get(address - dram, bytes, 0, size);

            var value = 0L;
            for (int i = 0; i < size; ++i) {
                value |= (bytes[i] & 0xFFL) << (i << 3);
            }

            return value;
        }

        if (unsafe) {
            return 0L;
        }

        for (int id = 0; id < harts.length; ++id) {
            // msip[id]
            if (address == 0x2000000L + 4L * id && size == 4) {
                return Integer.toUnsignedLong(clint.msip(id));
            }

            // mtimecmp[id]
            if (address == 0x2004000L + 8L * id && size == 8) {
                return clint.mtimecmp(id);
            }
        }

        // mtime
        if (address == 0x200BFF8L && size == 8) {
            return clint.mtime();
        }

        // uart rx
        if (address == 0x010000000L && size == 1) {
            try {
                return ((long) System.in.read()) & 0xFFL;
            } catch (final IOException e) {
                Log.warn("uart rx read: %s", e);
                return 0L;
            }
        }

        // uart lsr
        if (address == 0x010000005L && size == 1) {
            try {
                return System.in.available() > 0 ? 1L : 0L;
            } catch (final IOException e) {
                Log.warn("uart lsr read: %s", e);
                return 0L;
            }
        }

        Log.warn("read [%016x:%016x]".formatted(address, address + size - 1));
        throw new TrapException(5, address);
    }

    @Override
    public void write(final long address, final int size, final long value, final boolean unsafe) {
        if (dram <= address && address + size < dram + memory.capacity()) {
            final var bytes = new byte[size];
            for (int i = 0; i < size; ++i) {
                bytes[i] = (byte) ((value >> (i << 3)) & 0xFF);
            }

            memory.put(address - dram, bytes, 0, size);
            return;
        }

        if (unsafe) {
            return;
        }

        for (int id = 0; id < harts.length; ++id) {
            // msip[id]
            if (address == 0x2000000L + 4L * id && size == 4) {
                clint.msip(id, (int) (value & 0xFFFFFFFFL));
                return;
            }

            // mtimecmp[id]
            if (address == 0x2004000L + 8L * id && size == 8) {
                clint.mtimecmp(id, value);
                return;
            }
        }

        // mtime
        if (address == 0x200BFF8L && size == 8) {
            clint.mtime(value);
            return;
        }

        // uart rx
        if (address == 0x10000000L && size == 1) {
            System.out.print((char) (value & 0xFF));
            return;
        }

        Log.warn("store [%016x:%016x] = %x".formatted(address, address + size - 1, value));
        throw new TrapException(7, address);
    }

    @Override
    public int fetch(final long pc, final boolean unsafe) {
        if (dram <= pc && pc + 4 < dram + memory.capacity()) {
            final var bytes = new byte[4];
            memory.get(pc - dram, bytes, 0, 4);

            var value = 0;
            for (int i = 0; i < 4; ++i) {
                value |= (bytes[i] & 0xFF) << (i << 3);
            }

            return value;
        }

        if (unsafe) {
            return 0;
        }

        Log.warn("fetch pc=%016x", pc);
        throw new TrapException(1, pc);
    }

    private String readString(final long addr, final int size) {
        final var bytes = new byte[size];
        memory.get(addr, bytes, 0, size);
        return new String(bytes);
    }
}
