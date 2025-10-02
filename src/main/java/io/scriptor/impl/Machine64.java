package io.scriptor.impl;

import io.scriptor.elf.SymbolTable;
import io.scriptor.machine.Hart;
import io.scriptor.machine.Machine;
import io.scriptor.util.ExtendedInputStream;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

public final class Machine64 implements Machine {

    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private final long DRAM_BASE = 0x80000000L;

    private final SymbolTable symbols = new SymbolTable();
    private final ByteBuffer memory;
    private final CLINT clint;
    private final Hart[] harts;

    private final Map<Integer, Long> registers = new HashMap<>();
    private long entry;
    private long offset;

    private boolean once;
    private boolean active;

    private IntConsumer breakpointHandler;
    private IntConsumer trapHandler;

    public Machine64(final int capacity, final @NotNull ByteOrder order, final int count) {
        this.memory = ByteBuffer.allocateDirect(capacity).order(order);
        this.clint = new CLINT(this, count);
        this.harts = new Hart[count];
        for (int id = 0; id < count; ++id) {
            this.harts[id] = new Hart64(this, id);
        }
    }

    @Override
    public @NotNull SymbolTable symbols() {
        return symbols;
    }

    @Override
    public @NotNull CLINT clint() {
        return clint;
    }

    @Override
    public @NotNull Hart hart(final int id) {
        return harts[id];
    }

    @Override
    public @NotNull Stream<Hart> harts() {
        return Arrays.stream(harts);
    }

    @Override
    public void reset() {
        once = false;
        active = false;

        clint.reset();

        for (final var hart : harts) {
            hart.reset(entry);
            registers.forEach((register, value) -> hart.gprFile().putd(register, value));
        }
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        clint.dump(out);
        for (int id = 0; id < harts.length; ++id) {
            out.printf("hart #%x:%n", id);
            harts[id].dump(out);
        }
    }

    @Override
    public void dump(final @NotNull PrintStream out, final long address, final long size) {
        if (size == 0) {
            out.println("<empty>");
            return;
        }

        final var CHUNK = 0x20;

        var allZero      = false;
        var allZeroBegin = 0L;

        for (long i = 0; i < size; i += CHUNK) {

            final var chunk = (int) Math.min(size - i, CHUNK);
            if (chunk <= 0)
                break;

            final var buffer = new byte[chunk];
            if (!direct(address + i, buffer, false))
                break;

            boolean allZeroP = true;
            for (int j = 0; j < chunk; ++j)
                if (buffer[j] != 0) {
                    allZeroP = false;
                    break;
                }

            if (allZero && !allZeroP) {
                allZero = false;
                out.printf("%016x - %016x%n", allZeroBegin, i - 1);
            } else if (!allZero && allZeroP) {
                allZero = true;
                allZeroBegin = i;
                continue;
            } else if (allZero) {
                continue;
            }

            out.printf("%016x |", address + i);

            for (int j = 0; j < chunk; ++j) {
                out.printf(" %02x", buffer[j]);
            }
            for (int j = chunk; j < CHUNK; ++j) {
                out.print(" 00");
            }

            out.print(" | ");

            for (int j = 0; j < chunk; ++j) {
                out.print(buffer[j] >= 0x20 ? (char) buffer[j] : '.');
            }
            for (int j = chunk; j < CHUNK; ++j) {
                out.print('.');
            }

            out.println();
        }
        out.println("(END)");
    }

    @Override
    public synchronized void tick() throws InterruptedException {
        if (!active) {
            wait();
            return;
        }

        clint.step();
        for (final var hart : harts) {
            hart.step();
        }

        if (once) {
            active = false;
        }
    }

    @Override
    public synchronized void step() {
        once = true;
        active = true;
        notify();
    }

    @Override
    public synchronized void spin() {
        once = false;
        active = true;
        notify();
    }

    @Override
    public void pause() {
        once = false;
        active = false;
    }

    @Override
    public void onBreakpoint(final @NotNull IntConsumer handler) {
        this.breakpointHandler = handler;
    }

    @Override
    public void breakpoint(final int id) {
        if (breakpointHandler != null) {
            breakpointHandler.accept(id);
        }
    }

    @Override
    public void onTrap(final @NotNull IntConsumer handler) {
        this.trapHandler = handler;
    }

    @Override
    public void trap(final int id) {
        if (trapHandler != null) {
            trapHandler.accept(id);
        }
    }

    @Override
    public @NotNull Object acquireLock(final long address) {
        return locks.computeIfAbsent(address, _ -> new Object());
    }

    @Override
    public void entry(final long entry) {
        if (DRAM_BASE <= entry && entry < DRAM_BASE + memory.capacity()) {
            this.entry = entry;
            return;
        }

        throw new IllegalArgumentException("set entry=%016x".formatted(entry));
    }

    @Override
    public void register(final int register, final long value) {
        registers.put(register, value);
    }

    @Override
    public void offset(final long offset) {
        this.offset = offset;
    }

    @Override
    public long offset() {
        return offset;
    }

    @Override
    public void order(final @NotNull ByteOrder order) {
        memory.order(order);
    }

    @Override
    public void segment(
            final @NotNull ExtendedInputStream stream,
            final long address,
            final long size,
            final long allocate
    ) throws IOException {
        final var sizeInt     = (int) size;
        final var allocateInt = (int) allocate;

        if (sizeInt != size || allocateInt != allocate) {
            throw new IllegalArgumentException();
        }

        final var begin = (int) (address - DRAM_BASE);
        final var end   = begin + allocateInt;

        if (0 <= begin && end <= memory.capacity()) {
            stream.read(memory.slice(begin, sizeInt).order(memory.order()));
            memory.put(begin + sizeInt, new byte[allocateInt - sizeInt]);
            return;
        }

        throw new IllegalArgumentException("segment address=%016x, size=%x, allocate=%x"
                                                   .formatted(address, size, allocate));
    }

    @Override
    public int fetch(final long pc, final boolean unsafe) {
        if (DRAM_BASE <= pc && pc + 4 <= DRAM_BASE + memory.capacity()) {
            final var base = (int) (pc - DRAM_BASE);
            return memory.getInt(base);
        }

        if (unsafe) {
            return 0;
        }

        Log.error("fetch pc=%016x", pc);
        throw new TrapException(1, pc);
    }

    @Override
    public long read(final long address, final int size, final boolean unsafe) {
        if (DRAM_BASE <= address && address + size <= DRAM_BASE + memory.capacity()) {
            final var base = (int) (address - DRAM_BASE);
            return switch (size) {
                case 1 -> (long) memory.get(base) & 0xFFL;
                case 2 -> (long) memory.getShort(base) & 0xFFFFL;
                case 4 -> (long) memory.getInt(base) & 0xFFFFFFFFL;
                case 8 -> memory.getLong(base);
                default -> throw new IllegalArgumentException("read size=%x".formatted(size));
            };
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
                Log.error("read stdin: %s", e);
                return 0L;
            }
        }

        // uart lsr
        if (address == 0x010000005L && size == 1) {
            try {
                return System.in.available() > 0 ? 1L : 0L;
            } catch (final IOException e) {
                Log.error("read stdin: %s", e);
                return 0L;
            }
        }

        Log.error("read address=%016x, size=%x".formatted(address, size));
        throw new TrapException(5, address);
    }

    @Override
    public void write(final long address, final int size, final long value, final boolean unsafe) {
        if (DRAM_BASE <= address && address + size <= DRAM_BASE + memory.capacity()) {
            final var base = (int) (address - DRAM_BASE);
            switch (size) {
                case 1 -> memory.put(base, (byte) (value & 0xFFL));
                case 2 -> memory.putShort(base, (short) (value & 0xFFFFL));
                case 4 -> memory.putInt(base, (int) (value & 0xFFFFFFFFL));
                case 8 -> memory.putLong(base, value);
                default -> throw new IllegalArgumentException("write size=%x".formatted(size));
            }
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
            System.out.write((int) (value & 0xFFL));
            System.out.flush();
            return;
        }

        Log.error("write address=%016x, size=%x, value=%x".formatted(address, size, value));
        throw new TrapException(7, address);
    }

    @Override
    public boolean direct(final long address, final byte @NotNull [] buffer, final boolean write) {
        if (DRAM_BASE <= address && address + buffer.length <= DRAM_BASE + memory.capacity()) {
            final var base = (int) (address - DRAM_BASE);
            if (write) {
                memory.put(base, buffer, 0, buffer.length);
            } else {
                memory.get(base, buffer, 0, buffer.length);
            }
            return true;
        }

        Log.warn("direct read/write out of bounds: address=%016x, length=%x, base=%016x, capacity=%x",
                 address,
                 buffer.length,
                 DRAM_BASE,
                 memory.capacity());
        return false;
    }
}
