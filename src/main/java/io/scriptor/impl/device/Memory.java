package io.scriptor.impl.device;

import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.NodeBuilder;
import io.scriptor.machine.Device;
import io.scriptor.machine.IODevice;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Memory implements IODevice {

    private final Machine machine;
    private final long begin;
    private final long end;
    private final int capacity;
    private final boolean readonly;
    private final ByteBuffer buffer;

    public Memory(
            final @NotNull Machine machine,
            final long begin,
            final int capacity,
            final @NotNull ByteOrder order,
            final boolean readonly
    ) {
        this.machine = machine;
        this.begin = begin;
        this.end = begin + capacity;
        this.capacity = capacity;
        this.readonly = readonly;
        this.buffer = ByteBuffer.allocateDirect(capacity).order(order);
    }

    @Override
    public @NotNull Machine machine() {
        return machine;
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

        builder.name("memory@%x".formatted(begin))
               .prop(pb -> pb.name("phandle").data(phandle))
               .prop(pb -> pb.name("device_type").data("memory"))
               .prop(pb -> pb.name("reg").data(begin, end - begin));
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
        if (0 > offset || offset + size > capacity) {
            Log.error("invalid memory read offset=%x, size=%d", offset, size);
            return 0L;
        }

        return switch (size) {
            case 1 -> buffer.get(offset) & 0xFFL;
            case 2 -> buffer.getShort(offset) & 0xFFFFL;
            case 4 -> buffer.getInt(offset) & 0xFFFFFFFFL;
            case 8 -> buffer.getLong(offset);
            default -> {
                Log.error("invalid memory read offset=%x, size=%d", offset, size);
                yield 0L;
            }
        };
    }

    @Override
    public void write(final int offset, final int size, final long value) {
        if (readonly) {
            Log.error("invalid read-only memory write offset=%x, size=%d, value=%x", offset, size, value);
            return;
        }

        if (0 > offset || offset + size > capacity) {
            Log.error("invalid memory write offset=%x, size=%d, value=%x", offset, size, value);
            return;
        }

        switch (size) {
            case 1 -> buffer.put(offset, (byte) (value & 0xFFL));
            case 2 -> buffer.putShort(offset, (short) (value & 0xFFFFL));
            case 4 -> buffer.putInt(offset, (int) (value & 0xFFFFFFFFL));
            case 8 -> buffer.putLong(offset, value);
            default -> Log.error("invalid memory write offset=%x, size=%d, value=%x", offset, size, value);
        }
    }

    @Override
    public @NotNull String toString() {
        return "memory@%x".formatted(begin);
    }

    public void direct(final byte @NotNull [] data, final int offset, final boolean write) {
        if (0 > offset || offset + data.length > capacity) {
            Log.warn("direct read/write out of bounds: offset=%x, length=%d", offset, data.length);
            return;
        }

        if (write) {
            buffer.put(offset, data, 0, data.length);
        } else {
            buffer.get(offset, data, 0, data.length);
        }
    }

    public @NotNull ByteBuffer buffer() {
        return buffer;
    }
}
