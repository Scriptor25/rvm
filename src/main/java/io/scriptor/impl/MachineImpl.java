package io.scriptor.impl;

import com.carrotsearch.hppc.LongObjectHashMap;
import com.carrotsearch.hppc.LongObjectMap;
import io.scriptor.elf.SymbolTable;
import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.FDT;
import io.scriptor.fdt.TreeBuilder;
import io.scriptor.impl.device.Memory;
import io.scriptor.impl.device.UART;
import io.scriptor.machine.Device;
import io.scriptor.machine.Hart;
import io.scriptor.machine.IODevice;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public final class MachineImpl implements Machine {

    private final LongObjectMap<Object> locks = new LongObjectHashMap<>();

    private final SymbolTable symbols = new SymbolTable();

    private final ByteOrder order;

    private final Hart[] harts;
    private final Device[] devices;

    private final Memory dt;

    private boolean once;
    private boolean active;

    private IntConsumer breakpointHandler;

    public MachineImpl(final int harts, final ByteOrder order, final @NotNull Function<Machine, Device>[] devices) {

        this.order = order;

        this.harts = new Hart[harts];
        for (int id = 0; id < harts; ++id) {
            this.harts[id] = new HartImpl(this, id);
        }

        this.devices = new Device[devices.length];
        for (int i = 0; i < devices.length; ++i)
            this.devices[i] = devices[i].apply(this);

        for (int j = 0; j < this.devices.length; ++j) {
            final var b = this.devices[j];
            if (b instanceof IODevice iob)
                for (int i = j + 1; i < this.devices.length; ++i) {
                    final var a = this.devices[i];
                    if (a instanceof IODevice ioa)
                        if (ioa.begin() < iob.end() && iob.begin() < ioa.end())
                            Log.warn("device map overlap: %s [%08x;%08x] and %s [%08x;%08x]",
                                     iob,
                                     iob.begin(),
                                     iob.end(),
                                     ioa,
                                     ioa.begin(),
                                     ioa.end());
                }
        }

        this.dt = new Memory(this, 0x100000000L, 0x2000, true);
        generateDeviceTree(this.dt.buffer());
    }

    @Override
    public @NotNull ByteOrder order() {
        return order;
    }

    @Override
    public @NotNull SymbolTable symbols() {
        return symbols;
    }

    @Override
    public int harts() {
        return harts.length;
    }

    @Override
    public @NotNull Hart hart(final int id) {
        if (id < 0 || id >= harts.length)
            throw new IllegalArgumentException();
        return harts[id];
    }

    @Override
    public <T extends Device> @NotNull T device(final @NotNull Class<T> type) {
        for (final var device : devices)
            if (type.isInstance(device))
                return type.cast(device);
        throw new NoSuchElementException();
    }

    @Override
    public <T extends Device> @NotNull T device(final @NotNull Class<T> type, int index) {
        for (final var device : devices)
            if (type.isInstance(device) && index-- <= 0)
                return type.cast(device);
        throw new NoSuchElementException();
    }

    @Override
    public <T extends Device> void device(final @NotNull Class<T> type, final @NotNull Predicate<T> function) {
        for (final var device : devices)
            if (type.isInstance(device))
                if (function.test(type.cast(device)))
                    return;
    }

    @Override
    public <T extends IODevice> @Nullable T device(final @NotNull Class<T> type, final long address) {
        for (final var device : devices)
            if (device instanceof IODevice iodevice)
                if (type.isInstance(iodevice) && iodevice.begin() <= address && address < iodevice.end())
                    return type.cast(iodevice);
        return null;
    }

    @Override
    public @NotNull Machine machine() {
        return this;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        for (final var device : devices)
            device.dump(out);

        for (final var hart : harts)
            hart.dump(out);

        dump(out, 0x80007000L, 0x1000L);
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
            } else if (allZero)
                continue;

            out.printf("%016x |", paddr + i);

            for (int j = 0; j < chunk; ++j)
                out.printf(" %02x", buffer[j]);
            for (int j = chunk; j < CHUNK; ++j)
                out.print(" 00");

            out.print(" | ");

            for (int j = 0; j < chunk; ++j)
                out.print(buffer[j] >= 0x20 ? (char) buffer[j] : '.');
            for (int j = chunk; j < CHUNK; ++j)
                out.print('.');

            out.println();
        }
        out.println("(END)");
    }

    @Override
    public void reset() {
        once = false;
        active = false;

        for (final var device : devices)
            device.reset();

        for (final var hart : harts) {
            hart.reset();
            hart.gprFile().putd(0x0A, hart.id()); // boot hart id
            hart.gprFile().putd(0x0B, dt.begin()); // device tree address
        }
    }

    @Override
    public void step() {
        if (!active)
            return;

        for (final var device : devices)
            device.step();

        for (final var hart : harts)
            hart.step();

        if (once) {
            active = false;
            breakpoint(-1);
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
    public @NotNull Object acquireLock(final long address) {
        if (locks.containsKey(address))
            return locks.get(address);

        final var lock = new Object();
        locks.put(address, lock);
        return lock;
    }

    @Override
    public long pRead(final long paddr, final int size, final boolean unsafe) {
        if (dt.begin() <= paddr && paddr + size <= dt.end())
            return dt.read((int) (paddr - dt.begin()), size);

        for (final var device : devices)
            if (device instanceof IODevice iodevice)
                if (iodevice.begin() <= paddr && paddr + size <= iodevice.end())
                    return iodevice.read((int) (paddr - iodevice.begin()), size);

        if (unsafe) {
            Log.warn("read invalid address: address=%x, size=%d", paddr, size);
            return 0L;
        }

        throw new TrapException(-1, 0x05L, paddr, "read invalid address: address=%x, size=%d", paddr, size);
    }

    @Override
    public void pWrite(final long paddr, final int size, final long value, final boolean unsafe) {
        if (dt.begin() <= paddr && paddr + size <= dt.end()) {
            dt.write((int) (paddr - dt.begin()), size, value);
            return;
        }

        for (final var device : devices)
            if (device instanceof IODevice iodevice)
                if (iodevice.begin() <= paddr && paddr + size <= iodevice.end()) {
                    iodevice.write((int) (paddr - iodevice.begin()), size, value);
                    return;
                }

        if (unsafe) {
            Log.warn("write invalid address: address=%x, size=%d, value=%x", paddr, size, value);
            return;
        }

        throw new TrapException(-1,
                                0x07L,
                                paddr,
                                "write invalid address: address=%x, size=%d, value=%x",
                                paddr,
                                size,
                                value);
    }

    @Override
    public void pDirect(final byte @NotNull [] data, final long paddr, final boolean write) {
        if (dt.begin() <= paddr && paddr + data.length <= dt.end()) {
            dt.direct(data, (int) (paddr - dt.begin()), write);
            return;
        }

        for (final var device : devices)
            if (device instanceof Memory memory)
                if (memory.begin() <= paddr && paddr + data.length <= memory.end()) {
                    memory.direct(data, (int) (paddr - memory.begin()), write);
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
                        .node(nb -> nb
                                .name("chosen")
                                .prop(pb -> pb.name("stdout-path").data("/soc/serial@10000000"))
                                .node(nb1 -> nb1
                                        .name("opensbi-domains")
                                        .prop(pb -> pb.name("compatible").data("opensbi,domain,config"))
                                        .node(nb2 -> nb2
                                                .name("tmemory")
                                                .prop(pb -> pb.name("phandle").data(-0x1))
                                                .prop(pb -> pb.name("compatible").data("opensbi,domain,memregion"))
                                                .prop(pb -> pb.name("base").data(0x80000000L))
                                                .prop(pb -> pb.name("order").data(20))
                                        )
                                        .node(nb2 -> nb2
                                                .name("umemory")
                                                .prop(pb -> pb.name("phandle").data(-0x2))
                                                .prop(pb -> pb.name("compatible").data("opensbi,domain,memregion"))
                                                .prop(pb -> pb.name("base").data(0x0L))
                                                .prop(pb -> pb.name("order").data(64))
                                        )
                                        .node(nb2 -> nb2
                                                .name("tuart")
                                                .prop(pb -> pb.name("phandle").data(-0x3))
                                                .prop(pb -> pb.name("compatible").data("opensbi,domain,memregion"))
                                                .prop(pb -> pb.name("base").data(0x20000000L))
                                                .prop(pb -> pb.name("order").data(12))
                                                .prop(pb -> pb.name("mmio").data())
                                                .prop(pb -> pb.name("devices").data(context.get(device(UART.class))))
                                        )
                                        .node(nb2 -> nb2
                                                .name("tdomain")
                                                .prop(pb -> pb.name("phandle").data(-0x4))
                                                .prop(pb -> pb.name("compatible").data("opensbi,domain,instance"))
                                                .prop(pb -> pb.name("possible-harts").data(context.get(harts[0])))
                                                .prop(pb -> pb.name("regions").data(-0x1, 0x3F, -0x3, 0x3F))
                                                .prop(pb -> pb.name("boot-hart").data(context.get(harts[0])))
                                                .prop(pb -> pb.name("next-arg1").data(0L))
                                                .prop(pb -> pb.name("next-addr").data(0x80000000L))
                                                .prop(pb -> pb.name("next-mode").data(1))
                                                .prop(pb -> pb.name("system-reset-allowed").data())
                                                .prop(pb -> pb.name("system-suspend-allowed").data())
                                        )
                                        .node(nb2 -> nb2
                                                .name("udomain")
                                                .prop(pb -> pb.name("phandle").data(-0x5))
                                                .prop(pb -> pb.name("compatible").data("opensbi,domain,instance"))
                                                .prop(pb -> pb.name("possible-harts").data(context.get(harts[0])))
                                                .prop(pb -> pb.name("regions").data(-0x1, 0x00, -0x3, 0x00, -0x2, 0x3F))
                                        ))
                        )
                        .node(nb -> {
                            nb.name("cpus")
                              .prop(pb -> pb.name("#address-cells").data(0x01))
                              .prop(pb -> pb.name("#size-cells").data(0x00))
                              .prop(pb -> pb.name("timebase-frequency").data(0x989680));

                            for (final var hart : harts) {
                                nb.node(builder -> hart.build(context,
                                                              builder.prop(pb -> pb
                                                                      .name("opensbi-domain")
                                                                      .data(-0x4))));
                            }
                        })
                        .node(nb -> {
                            nb.name("soc")
                              .prop(pb -> pb.name("#address-cells").data(0x02))
                              .prop(pb -> pb.name("#size-cells").data(0x02))
                              .prop(pb -> pb.name("compatible").data("simple-bus"))
                              .prop(pb -> pb.name("ranges").data());

                            for (final var device : devices)
                                nb.node(builder -> device.build(context, builder));
                        })
                )
                .build(tree -> FDT.write(tree, buffer));
    }

    @Override
    public void close() throws Exception {
        for (final var hart : harts)
            hart.close();
        for (final var device : devices)
            device.close();
    }
}
