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

import static io.scriptor.isa.CSR.CSR_M;
import static io.scriptor.isa.CSR.mip;

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
    private final int[] msip;
    private final long[] mtimecmp;
    private long mtime;

    public CLINT(final @NotNull Machine machine, final long begin, final long end, final int hartCount) {
        this.machine = machine;
        this.begin = begin;
        this.end = end;
        this.hartCount = hartCount;
        this.msip = new int[hartCount];
        this.mtimecmp = new long[hartCount];
        this.mtime = 0;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        out.printf("clint: mtime=%x%n", mtime);
        for (int i = 0; i < hartCount; ++i) {
            out.printf("#%-2d | msip=%x mtimecmp=%x%n", i, msip[i], mtimecmp[i]);
        }
    }

    @Override
    public void reset() {
        Arrays.fill(msip, 0);
        Arrays.fill(mtimecmp, ~0L);

        mtime = 0L;
    }

    @Override
    public void step() {
        mtime++;

        for (int id = 0; id < hartCount; ++id) {
            final var timerPending    = Long.compareUnsigned(mtime, mtimecmp[id]) >= 0;
            final var softwarePending = msip[id] != 0L;

            var pending = 0L;
            if (timerPending)
                pending |= (1L << 7);
            if (softwarePending)
                pending |= (1L << 3);

            final var hart = machine.hart(id);
            hart.csrFile().putd(mip, CSR_M, pending);
            if (pending != 0 && hart.wfi()) {
                hart.wake();
            }
        }
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.push(this);

        final var ie = new int[4 * hartCount];
        for (int i = 0; i < hartCount; ++i) {
            final var cpuI = context.get(machine.hart(i));
            ie[i << 2] = cpuI;
            ie[(i << 2) + 1] = 0x03;
            ie[(i << 2) + 2] = cpuI;
            ie[(i << 2) + 3] = 0x07;
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
    public long read(final int offset, final int size) { // TODO: check size
        if (offset >= MSIP_BASE && offset < MTIMECMP_BASE && size == 4) {
            final var hart = (offset - MSIP_BASE) / MSIP_STRIDE;
            if (hart >= hartCount) {
                return 0L;
            }
            return msip[hart] & 0xFFFFFFFFL;
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
    public void write(final int offset, final int size, final long value) { // TODO: check size, check read-only
        if (offset >= MSIP_BASE && offset < MTIMECMP_BASE && size == 4) {
            final var hart = (offset - MSIP_BASE) / MSIP_STRIDE;
            if (hart >= hartCount) {
                return;
            }
            msip[hart] = (int) (value & 0xFFFFFFFFL);
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

    public void mtimecmp(final int id, final long value) {
        mtimecmp[id] = value;
    }

    public long mtimecmp(final int id) {
        return mtimecmp[id];
    }

    public long mtime() {
        return mtime;
    }
}
