package io.scriptor.impl.device;

import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.NodeBuilder;
import io.scriptor.machine.Device;
import io.scriptor.machine.IODevice;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Arrays;

public final class PLIC implements IODevice {

    static final class Context {

        final int[] enable = new int[0x20];
        int threshold = 0;
        int claim = 0;
    }

    public static final int SOURCE_COUNT = 0x400;
    public static final int CONTEXT_COUNT = 0x3E00;

    public static final int PRIORITY_BASE = 0x0000;
    public static final int PENDING_BASE = 0x1000;
    public static final int ENABLE_BASE = 0x2000;
    public static final int ENABLE_STRIDE = 0x100;
    public static final int CONTEXT_BASE = 0x200000;
    public static final int CONTEXT_STRIDE = 0x1000;

    public static final int CONTEXT_OFFSET_THRESHOLD = 0x0;
    public static final int CONTEXT_OFFSET_CLAIM = 0x4;
    public static final int CONTEXT_OFFSET_RESERVED = 0x8;

    private final Machine machine;
    private final long begin;
    private final long end;
    private final int ndev;

    private final int[] priority;
    private final int[] pending;

    private final Context[] contexts;

    public PLIC(final @NotNull Machine machine, final long begin, final int ndev) {
        this.machine = machine;
        this.begin = begin;
        this.end = begin + 0x4000000L;
        this.ndev = ndev;

        this.priority = new int[SOURCE_COUNT];
        this.pending = new int[SOURCE_COUNT >> 2];

        this.contexts = new Context[CONTEXT_COUNT];
        for (int i = 0; i < this.contexts.length; ++i)
            this.contexts[i] = new Context();
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
        Arrays.fill(priority, 0);
        Arrays.fill(pending, 0);

        for (final var context : contexts) {
            Arrays.fill(context.enable, 0);
            context.threshold = 0;
            context.claim = 0;
        }
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.get(this);

        final var ie = new int[2 * machine.harts()];
        for (int i = 0; i < machine.harts(); ++i) {
            final var cpu = context.get(machine.hart(i));
            ie[i * 2] = cpu;
            ie[i * 2 + 1] = 0x0B;
        }

        builder.name("plic@%x".formatted(begin))
               .prop(pb -> pb.name("phandle").data(phandle))
               .prop(pb -> pb.name("compatible").data("riscv,plic0"))
               .prop(pb -> pb.name("reg").data(begin, end - begin))
               .prop(pb -> pb.name("interrupt-controller").data())
               .prop(pb -> pb.name("#interrupt-cells").data(0x01))
               .prop(pb -> pb.name("riscv,ndev").data(ndev))
               .prop(pb -> pb.name("interrupts-extended").data(ie));
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
        if (offset >= PRIORITY_BASE && offset < PENDING_BASE && size == 4) {
            final var index = (offset - PRIORITY_BASE) / 4;

            if (index < priority.length)
                return priority[index] & 0xFFFFFFFFL;
        }

        if (offset >= PENDING_BASE && offset < ENABLE_BASE && size == 4) {
            final var index = (offset - PENDING_BASE) / 4;

            if (index < pending.length)
                return pending[index] & 0xFFFFFFFFL;
        }

        if (offset >= ENABLE_BASE && offset < CONTEXT_BASE && size == 4) {
            final var base    = offset - ENABLE_BASE;
            final var context = base / ENABLE_STRIDE;
            final var index   = (base - context * ENABLE_STRIDE) / 4;

            if (context < contexts.length && index >= 0 && index < contexts[context].enable.length)
                return contexts[context].enable[index] & 0xFFFFFFFFL;
        }

        if (offset >= CONTEXT_BASE && size == 4) {
            final var base    = offset - CONTEXT_BASE;
            final var context = base / CONTEXT_STRIDE;
            final var index   = base - context * CONTEXT_STRIDE;

            if (context < contexts.length)
                switch (index) {
                    case CONTEXT_OFFSET_THRESHOLD -> {
                        return contexts[context].threshold & 0xFFFFFFFFL;
                    }
                    case CONTEXT_OFFSET_CLAIM -> {
                        return contexts[context].claim & 0xFFFFFFFFL;
                    }
                    case CONTEXT_OFFSET_RESERVED -> {
                        return 0L;
                    }
                }
        }

        Log.error("invalid plic read offset=%x, size=%d", offset, size);
        return 0L;
    }

    @Override
    public void write(final int offset, final int size, final long value) {
        if (offset >= PRIORITY_BASE && offset < PENDING_BASE && size == 4) {
            final var index = (offset - PRIORITY_BASE) / 4;

            if (index < priority.length) {
                priority[index] = (int) (value & 0xFFFFFFFFL);
                return;
            }
        }

        if (offset >= ENABLE_BASE && offset < CONTEXT_BASE && size == 4) {
            final var base    = offset - ENABLE_BASE;
            final var context = base / ENABLE_STRIDE;
            final var index   = (base - context * ENABLE_STRIDE) / 4;

            if (context < contexts.length && index >= 0 && index < contexts[context].enable.length) {
                contexts[context].enable[index] = (int) (value & 0xFFFFFFFFL);
                return;
            }
        }

        if (offset >= CONTEXT_BASE && size == 4) {
            final var base    = offset - CONTEXT_BASE;
            final var context = base / CONTEXT_STRIDE;
            final var index   = base - context * CONTEXT_STRIDE;

            if (context < contexts.length) {
                switch (index) {
                    case CONTEXT_OFFSET_THRESHOLD -> {
                        contexts[context].threshold = (int) (value & 0xFFFFFFFFL);
                        return;
                    }
                    case CONTEXT_OFFSET_CLAIM -> {
                        contexts[context].claim = (int) (value & 0xFFFFFFFFL);
                        return;
                    }
                    case CONTEXT_OFFSET_RESERVED -> {
                        return;
                    }
                }
            }
        }

        Log.error("invalid plic write offset=%x, size=%d, value=%x", offset, size, value);
    }

    @Override
    public @NotNull String toString() {
        return "plic@%x".formatted(begin);
    }
}
