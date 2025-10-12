package io.scriptor.impl;

import com.carrotsearch.hppc.LongObjectHashMap;
import com.carrotsearch.hppc.LongObjectMap;
import io.scriptor.elf.SymbolTable;
import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.FDT;
import io.scriptor.fdt.TreeBuilder;
import io.scriptor.impl.device.CLINT;
import io.scriptor.impl.device.Memory;
import io.scriptor.impl.device.PLIC;
import io.scriptor.impl.device.UART;
import io.scriptor.machine.Device;
import io.scriptor.machine.Hart;
import io.scriptor.machine.IODevice;
import io.scriptor.machine.Machine;
import io.scriptor.util.ExtendedInputStream;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static io.scriptor.impl.MMU.*;

public final class MachineImpl implements Machine {

    public static final long DRAM = 0x00000000L;

    private final LongObjectMap<Object> locks = new LongObjectHashMap<>();

    private final SymbolTable symbols = new SymbolTable();

    private final MMU mmu;
    private final Hart[] harts;
    private final IODevice[] devices;

    private final Memory memory;
    private final Memory deviceTreeMemory;

    private long entry;
    private long offset;

    private boolean once;
    private boolean active;

    private IntConsumer breakpointHandler;
    private IntConsumer trapHandler;

    public MachineImpl(int memoryCapacity, final @NotNull ByteOrder memoryOrder, final int hartCount) {

        memoryCapacity = ((memoryCapacity + 0b111) & ~0b111);

        mmu = new MMU(this);

        memory = new Memory(this,
                            DRAM,
                            DRAM + memoryCapacity,
                            memoryCapacity,
                            memoryOrder,
                            false);

        deviceTreeMemory = new Memory(this,
                                      0x100000000L,
                                      0x100002000L,
                                      0x2000,
                                      memoryOrder,
                                      true);

        devices = new IODevice[5];
        devices[0] = memory;
        devices[1] = deviceTreeMemory;
        devices[2] = new CLINT(this,
                               0x20000000L,
                               0x20010000L,
                               hartCount);
        devices[3] = new PLIC(this,
                              0xC0000000L,
                              0xC4000000L,
                              hartCount,
                              32);
        devices[4] = new UART(this,
                              0x10000000L,
                              0x10000100L,
                              System.in,
                              System.out);

        harts = new Hart[hartCount];
        for (int id = 0; id < hartCount; ++id) {
            harts[id] = new HartImpl(this, id);
        }

        for (int j = 0; j < devices.length; ++j) {
            for (int i = j + 1; i < devices.length; ++i) {
                if (devices[i].begin() < devices[j].end() && devices[j].begin() < devices[i].end()) {
                    Log.warn("device map overlap: %s [%08x;%08x] and %s [%08x;%08x]",
                             devices[j],
                             devices[j].begin(),
                             devices[j].end(),
                             devices[i],
                             devices[i].begin(),
                             devices[i].end());
                }
            }
        }

        generateDeviceTree(deviceTreeMemory.buffer());
    }

    @Override
    public @NotNull SymbolTable symbols() {
        return symbols;
    }

    @Override
    public @NotNull MMU mmu() {
        return mmu;
    }

    @Override
    public @NotNull Hart hart(final int id) {
        if (id < 0 || id >= harts.length) {
            throw new IllegalArgumentException();
        }
        return harts[id];
    }

    @Override
    public <T extends Device> @NotNull T device(final @NotNull Class<T> type) {
        for (final var device : devices) {
            if (type.isInstance(device)) {
                return type.cast(device);
            }
        }
        throw new NoSuchElementException();
    }

    @Override
    public <T extends Device> @NotNull T device(final @NotNull Class<T> type, final @NotNull Predicate<T> predicate) {
        for (final var device : devices) {
            if (type.isInstance(device)) {
                final var t = type.cast(device);
                if (predicate.test(t)) {
                    return t;
                }
            }
        }
        throw new NoSuchElementException();
    }

    @Override
    public <T extends Device> @NotNull T device(final @NotNull Class<T> type, int index) {
        for (final var device : devices) {
            if (type.isInstance(device) && index-- <= 0) {
                return type.cast(device);
            }
        }
        throw new NoSuchElementException();
    }

    @Override
    public <T extends Device> void device(final @NotNull Class<T> type, final @NotNull Consumer<T> consumer) {
        for (final var device : devices) {
            if (type.isInstance(device)) {
                consumer.accept(type.cast(device));
            }
        }
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        for (final var device : devices) {
            device.dump(out);
        }

        for (final var hart : harts) {
            hart.dump(out);
        }
    }

    @Override
    public void dump(final @NotNull PrintStream out, final long paddr, final long length) {
        if (length == 0) {
            out.println("<empty>");
            return;
        }

        final var CHUNK = 0x20;

        var allZero      = false;
        var allZeroBegin = 0L;

        for (long i = 0; i < length; i += CHUNK) {

            final var chunk = (int) Math.min(length - i, CHUNK);
            if (chunk <= 0)
                break;

            final var buffer = new byte[chunk];
            pDirect(buffer, paddr + i, false);

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

            out.printf("%016x |", paddr + i);

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
    public void reset() {
        once = false;
        active = false;

        for (final var device : devices) {
            device.reset();
        }

        for (final var hart : harts) {
            hart.reset(entry);
            hart.gprFile().putd(0x0A, 0x00000000L); // boot hart id
            hart.gprFile().putd(0x0B, deviceTreeMemory.begin()); // device tree address
        }
    }

    @Override
    public void step() {
        if (!active) {
            return;
        }

        for (final var device : devices) {
            device.step();
        }

        for (final var hart : harts) {
            hart.step();
        }

        if (once) {
            active = false;
        }
    }

    @Override
    public void spinOnce() {
        once = true;
        active = true;
    }

    @Override
    public void spin() {
        once = false;
        active = true;
    }

    @Override
    public void pause() {
        once = false;
        active = false;
    }

    @Override
    public void onBreakpoint(final @NotNull IntConsumer handler) {
        breakpointHandler = handler;
    }

    @Override
    public boolean breakpoint(final int id) {
        if (breakpointHandler != null) {
            breakpointHandler.accept(id);
            return true;
        }
        return false;
    }

    @Override
    public void onTrap(final @NotNull IntConsumer handler) {
        trapHandler = handler;
    }

    @Override
    public void trap(final int id) {
        if (trapHandler != null) {
            trapHandler.accept(id);
        }
    }

    @Override
    public @NotNull Object acquireLock(final long address) {
        if (locks.containsKey(address)) {
            return locks.get(address);
        }

        final var lock = new Object();
        locks.put(address, lock);
        return lock;
    }

    @Override
    public void entry(final long entry) {
        this.entry = entry;
    }

    @Override
    public long entry() {
        return entry;
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
    public void segment(
            final @NotNull ExtendedInputStream stream,
            final long address,
            final int size,
            final int allocate
    ) throws IOException {
        if (memory.begin() <= address && address + allocate <= memory.end()) {
            final var data = new byte[allocate];
            final var read = stream.read(data, 0, size);
            if (read != size) {
                Log.warn("end of stream: %d != %d", read, size);
            }

            final var offset = (int) (address - memory.begin());
            memory.direct(data, offset, true);

            return;
        }

        Log.error("memory write out of bounds: segment address=%x, size=%d, allocate=%d", address, size, allocate);
    }

    @Override
    public int fetch(final int hartid, final long pc, final boolean unsafe) {
        var ppc = mmu.translate(hartid, pc, ACCESS_FETCH, harts[hartid].privilege(), unsafe);

        if (ppc != pc) {
            Log.info("virtual address %016x -> physical address %016x", pc, ppc);
        }

        if (unsafe && ppc == ~0L) {
            Log.warn("fetch invalid virtual address: pc=%x", pc);
            return 0;
        }

        if (memory.begin() <= ppc && ppc + 4 <= memory.end()) {
            final var offset = (int) (ppc - memory.begin());
            return (int) memory.read(offset, 4);
        }

        if (unsafe) {
            Log.warn("fetch invalid address: pc=%x", ppc);
            return 0;
        }

        throw new TrapException(0x01, ppc, "fetch invalid address: pc=%x", ppc);
    }

    @Override
    public long read(final int hartid, final long vaddr, final int size, final boolean unsafe) {
        final var paddr = mmu.translate(hartid, vaddr, ACCESS_READ, harts[hartid].privilege(), unsafe);

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        if (unsafe && paddr == ~0L) {
            Log.warn("read invalid virtual address: address=%x, size=%d", vaddr, size);
            return 0L;
        }

        return pRead(paddr, size, unsafe);
    }

    @Override
    public void write(final int hartid, final long vaddr, final int size, final long value, final boolean unsafe) {
        final var paddr = mmu.translate(hartid, vaddr, ACCESS_WRITE, harts[hartid].privilege(), unsafe);

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        if (unsafe && paddr == ~0L) {
            Log.warn("write invalid virtual address: address=%x, size=%d, value=%x", vaddr, size, value);
            return;
        }

        pWrite(paddr, size, value, unsafe);
    }

    @Override
    public long pRead(final long paddr, final int size, final boolean unsafe) {
        for (final var device : devices) {
            if (device.begin() <= paddr && paddr + size <= device.end()) {
                return device.read((int) (paddr - device.begin()), size);
            }
        }

        if (unsafe) {
            Log.warn("read invalid address: address=%x, size=%d", paddr, size);
            return 0L;
        }

        throw new TrapException(0x05, paddr, "read invalid address: address=%x, size=%d", paddr, size);
    }

    @Override
    public void pWrite(final long paddr, final int size, final long value, final boolean unsafe) {
        for (final var device : devices) {
            if (device.begin() <= paddr && paddr + size <= device.end()) {
                device.write((int) (paddr - device.begin()), size, value);
                return;
            }
        }

        if (unsafe) {
            Log.warn("write invalid address: address=%x, size=%d, value=%x", paddr, size, value);
            return;
        }

        throw new TrapException(0x07, paddr,
                                "write invalid address: address=%x, size=%d, value=%x",
                                paddr, size, value);
    }

    @Override
    public void direct(int hartid, byte @NotNull [] data, long vaddr, boolean write) {
        final var paddr = mmu.translate(hartid,
                                        vaddr,
                                        write ? ACCESS_WRITE : ACCESS_READ,
                                        harts[hartid].privilege(),
                                        true);

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        if (paddr == ~0L) {
            Log.warn("direct read/write invalid virtual address: address=%x, length=%d", vaddr, data.length);
            return;
        }

        pDirect(data, paddr, write);
    }

    @Override
    public void pDirect(final byte @NotNull [] data, final long paddr, final boolean write) {
        if (memory.begin() <= paddr && paddr + data.length <= memory.end()) {
            final var offset = (int) (paddr - memory.begin());
            memory.direct(data, offset, write);
            return;
        }

        Log.warn("direct read/write invalid address: address=%x, length=%d", paddr, data.length);
    }

    @Override
    public void generateDeviceTree(final @NotNull ByteBuffer buffer) {

        final var context = new BuilderContext<Device>();

        TreeBuilder
                .create()
                .root(rb -> rb
                        .name("")
                        .prop(pb -> pb.name("#address-cells").data(0x02))
                        .prop(pb -> pb.name("#size-cells").data(0x02))
                        .prop(pb -> pb.name("compatible").data("rvm,riscv-virt"))
                        .prop(pb -> pb.name("model").data("RVM"))
                        .node(nb -> nb.name("chosen")
                                      .prop(pb -> pb.name("stdout-path").data("/soc/serial@10000000"))
                        )
                        .node(nb -> {
                            nb.name("cpus")
                              .prop(pb -> pb.name("#address-cells").data(0x01))
                              .prop(pb -> pb.name("#size-cells").data(0x00))
                              .prop(pb -> pb.name("timebase-frequency").data(0x989680));

                            for (final var hart : harts) {
                                nb.node(builder -> hart.build(context, builder));
                            }
                        })
                        .node(nb -> {
                            nb.name("soc")
                              .prop(pb -> pb.name("#address-cells").data(0x02))
                              .prop(pb -> pb.name("#size-cells").data(0x02))
                              .prop(pb -> pb.name("compatible").data("simple-bus"))
                              .prop(pb -> pb.name("ranges").data());

                            for (final var device : devices) {
                                nb.node(builder -> device.build(context, builder));
                            }
                        })
                )
                .build(tree -> FDT.write(tree, buffer));
    }
}
