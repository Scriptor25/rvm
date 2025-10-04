package io.scriptor.impl.device;

import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.NodeBuilder;
import io.scriptor.machine.Device;
import io.scriptor.machine.IODevice;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public final class UART implements IODevice {

    private static final int TX_OFFSET = 0x00;
    private static final int RX_OFFSET = 0x04;
    private static final int STATUS_OFFSET = 0x08;

    private static final int TX_READY = 0b01;
    private static final int RX_READY = 0b10;

    private final Machine machine;
    private final long begin;
    private final long end;
    private final InputStream in;
    private final OutputStream out;

    public UART(
            final @NotNull Machine machine,
            final long begin,
            final long end,
            final @NotNull InputStream in,
            final @NotNull OutputStream out
    ) {
        this.machine = machine;
        this.begin = begin;
        this.end = end;
        this.in = in;
        this.out = out;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.push(this);

        final var plic0 = context.get(machine.device(PLIC.class));

        builder.name("serial@%x".formatted(begin))
               .prop(pb -> pb.name("phandle").data(phandle))
               .prop(pb -> pb.name("compatible").data("ns16550a"))
               .prop(pb -> pb.name("reg").data(begin, end - begin))
               .prop(pb -> pb.name("clock-frequency").data(0x384000))
               .prop(pb -> pb.name("current-speed").data(0x1c200))
               .prop(pb -> pb.name("reg-shift").data(0x00))
               .prop(pb -> pb.name("reg-io-width").data(0x04))
               .prop(pb -> pb.name("interrupts").data(0x01))
               .prop(pb -> pb.name("interrupt-parent").data(plic0));
    }

    @Override
    public long begin() {
        return begin;
    }

    @Override
    public long end() {
        return end;
    }

    @Override
    public long read(final int offset, final int size) {
        try {
            return switch (offset) {
                case RX_OFFSET -> in.available() > 0 ? (in.read() & 0xFFL) : -1L;
                case STATUS_OFFSET -> TX_READY | (in.available() > 0 ? RX_READY : 0);
                default -> {
                    Log.error("invalid uart read offset=%x, size=%d", offset, size);
                    yield 0L;
                }
            };
        } catch (final IOException e) {
            Log.error("uart: %s", e);
            return 0L;
        }
    }

    @Override
    public void write(final int offset, final int size, final long value) {
        try {
            switch (offset) {
                case TX_OFFSET -> {
                    out.write((int) (value & 0xFFL));
                    out.flush();
                }
                default -> Log.error("invalid uart write offset=%x, size=%d, value=%x", offset, size, value);
            }
        } catch (final IOException e) {
            Log.error("uart: %s", e);
        }
    }

    @Override
    public @NotNull String toString() {
        return "serial@%x".formatted(begin);
    }
}
