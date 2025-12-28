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

public final class CLINT implements IODevice {

    private static final int MSIP_BASE = 0x0000;
    private static final int MSIP_STRIDE = 4;
    private static final int MTIMECMP_BASE = 0x4000;
    private static final int MTIMECMP_STRIDE = 8;
    private static final int CONTEXT_BASE = 0xB000;
    private static final int MTIME_OFFSET = 0xBFF8;

    private final Machine machine;
    private final long begin;
    private final long end;
    private final int hartCount;

    private long mtime;

    private final long[] mtimecmp;

    /**
     * machine-level external interrupt pending bit
     */
    private final boolean[] meip;
    /**
     * machine-level software interrupt pending bit
     */
    private final boolean[] msip;

    public CLINT(final @NotNull Machine machine, final long begin, final int hartCount) {
        this.machine = machine;
        this.begin = begin;
        this.end = begin + 0x10000L;
        this.hartCount = hartCount;

        this.mtime = 0L;
        this.mtimecmp = new long[hartCount];

        this.meip = new boolean[hartCount];
        this.msip = new boolean[hartCount];
    }

    @Override
    public @NotNull Machine machine() {
        return machine;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        out.printf("clint: mtime=%x%n", mtime);
        for (int id = 0; id < hartCount; ++id) {
            out.printf("#%-2d | mtimecmp=%x meip=%b msip=%b%n", id, mtimecmp[id], meip[id], msip[id]);
        }
    }

    @Override
    public void reset() {
        mtime = 0L;
        Arrays.fill(mtimecmp, ~0L);
        Arrays.fill(meip, false);
        Arrays.fill(msip, false);
    }

    @Override
    public void step() {
        ++mtime;
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.push(this);

        final var ie = new int[4 * hartCount];
        for (int i = 0; i < hartCount; ++i) {
            final var cpu = context.get(machine.hart(i));
            ie[i * 4] = cpu;
            ie[i * 4 + 1] = 0x03;
            ie[i * 4 + 2] = cpu;
            ie[i * 4 + 3] = 0x07;
        }

        builder.name("clint@%x".formatted(begin))
               .prop(pb -> pb.name("phandle").data(phandle))
               .prop(pb -> pb.name("compatible").data("riscv,clint0"))
               .prop(pb -> pb.name("reg").data(begin, end - begin))
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
        if (offset >= MSIP_BASE && offset < MTIMECMP_BASE && size == 4) {
            final var hart = (offset - MSIP_BASE) / MSIP_STRIDE;
            if (hart >= hartCount) {
                return 0L;
            }
            return msip[hart] ? 1L : 0L;
        }

        if (offset >= MTIMECMP_BASE && offset < CONTEXT_BASE && size == 8) {
            final var hart = (offset - MTIMECMP_BASE) / MTIMECMP_STRIDE;
            if (hart >= hartCount) {
                return 0L;
            }
            return mtimecmp[hart];
        }

        if (offset == MTIME_OFFSET && size == 8) {
            return mtime;
        }

        Log.error("invalid clint read offset=%x, size=%d", offset, size);
        return 0L;
    }

    @Override
    public void write(final int offset, final int size, final long value) {
        if (offset >= MSIP_BASE && offset < MTIMECMP_BASE && size == 4) {
            final var hart = (offset - MSIP_BASE) / MSIP_STRIDE;
            if (hart >= hartCount) {
                return;
            }
            msip[hart] = value != 0L;
            return;
        }

        if (offset >= MTIMECMP_BASE && offset < CONTEXT_BASE && size == 8) {
            final var hart = (offset - MTIMECMP_BASE) / MTIMECMP_STRIDE;
            if (hart >= hartCount) {
                return;
            }
            mtimecmp[hart] = value;
            return;
        }

        Log.error("invalid clint write offset=%x, size=%d, value=%x", offset, size, value);
    }

    @Override
    public @NotNull String toString() {
        return "clint@%x".formatted(begin);
    }

    public long mtime() {
        return mtime;
    }

    public long mtimecmp(final int id) {
        return mtimecmp[id];
    }

    public void mtimecmp(final int id, final long value) {
        mtimecmp[id] = value;
    }

    public boolean meip(final int id) {
        return meip[id];
    }

    public boolean mtip(final int id) {
        return Long.compareUnsigned(mtime, mtimecmp[id]) >= 0;
    }

    public boolean msip(final int id) {
        return msip[id];
    }

    public void msip(final int id, final boolean value) {
        msip[id] = value;
    }
}
