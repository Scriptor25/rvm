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

    public static final int PRIORITY_BASE = 0x0000;
    public static final int PRIORITY_STRIDE = 4;
    public static final int PENDING_BASE = 0x1000;
    public static final int PENDING_STRIDE = 4;
    public static final int ENABLE_BASE = 0x2000;
    public static final int ENABLE_STRIDE = 4;
    public static final int CONTEXT_BASE = 0x200000;
    public static final int CONTEXT_STRIDE = 0x1000;
    public static final int THRESHOLD = 0x0;
    public static final int CLAIM = 0x4;

    private final Machine machine;
    private final long begin;
    private final long end;
    private final int hartCount;
    private final int ndev;

    private final int[] priority;
    private final int[] pending;
    private final int[][] enable;
    private final int[] threshold;

    public PLIC(final @NotNull Machine machine, final long begin, final long end, final int hartCount, final int ndev) {
        this.machine = machine;
        this.begin = begin;
        this.end = end;
        this.hartCount = hartCount;
        this.ndev = ndev;

        this.priority = new int[ndev + 1];
        this.pending = new int[ndev + 1];

        this.enable = new int[hartCount][((ndev + 0b11111) >> 5)];
        this.threshold = new int[hartCount];
    }

    @Override
    public void dump(final @NotNull PrintStream out) {
        out.println("plic:");
        out.println(" - priority:");
        for (int i = 0; i < priority.length; ++i) {
            out.printf("    #%-2d | %d%n", i, priority[i]);
        }
        out.println(" - pending:");
        for (int i = 0; i < pending.length; ++i) {
            out.printf("    #%-2d | %d%n", i, pending[i]);
        }
        out.println(" - harts:");
        for (int i = 0; i < hartCount; ++i) {
            out.printf("    #%-2d | threshold=%d, enable=", i, threshold[i]);
            for (int j = 0; j < enable[i].length; ++j) {
                if (j != 0) {
                    out.print(',');
                }
                out.printf("%08x", enable[i][j]);
            }
            out.println();
        }
    }

    @Override
    public void reset() {
        Arrays.fill(priority, 0);
        Arrays.fill(pending, 0);

        for (int i = 0; i < hartCount; ++i) {
            Arrays.fill(enable[i], 0);
            threshold[i] = 0;
        }
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.push(this);

        final var cpu0 = context.get(machine.hart(0));

        builder.name("plic@%x".formatted(begin))
               .prop(pb -> pb.name("phandle").data(phandle))
               .prop(pb -> pb.name("compatible").data("riscv,plic0"))
               .prop(pb -> pb.name("reg").data(begin, end - begin))
               .prop(pb -> pb.name("interrupt-controller").data())
               .prop(pb -> pb.name("#interrupt-cells").data(0x01))
               .prop(pb -> pb.name("riscv,ndev").data(0x20))
               .prop(pb -> pb.name("interrupts-extended").data(cpu0, 0x0b));
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
        if (offset >= PRIORITY_BASE && offset < PENDING_BASE && size == 4) {
            final var index = ((offset - PRIORITY_BASE) / PRIORITY_STRIDE) + 1;
            if (index > ndev) {
                return 0L;
            }
            return priority[index] & 0xFFFFFFFFL;
        }

        if (offset >= PENDING_BASE && offset < ENABLE_BASE && size == 4) {
            final var index = ((offset - PENDING_BASE) / PENDING_STRIDE) + 1;
            if (index > ndev) {
                return 0L;
            }
            return pending[index] & 0xFFFFFFFFL;
        }

        if (offset >= ENABLE_BASE && offset < CONTEXT_BASE && size == 4) {
            final var hart = (offset - ENABLE_BASE) / 0x100;
            final var word = (offset - ENABLE_BASE - hart * 0x100) / ENABLE_STRIDE;
            if (hart >= hartCount || word < 0 || word >= enable[hart].length) {
                return 0L;
            }
            return enable[hart][word] & 0xFFFFFFFFL;
        }

        if (offset >= CONTEXT_BASE && size == 4) {
            final var hart    = (offset - CONTEXT_BASE) / CONTEXT_STRIDE;
            final var context = offset - CONTEXT_BASE - hart * CONTEXT_STRIDE;
            if (hart >= hartCount) {
                return 0L;
            }
            return switch (context) {
                case THRESHOLD -> threshold[hart] & 0xFFFFFFFFL;
                case CLAIM -> 0L; // TODO: implement claim
                default -> 0L;
            };
        }

        Log.error("invalid plic read offset=%x, size=%d", offset, size);
        return 0L;
    }

    @Override
    public void write(final int offset, final int size, final long value) { // TODO: check size, check read-only
        if (offset >= PRIORITY_BASE && offset < PENDING_BASE && size == 4) {
            final var index = ((offset - PRIORITY_BASE) / PRIORITY_STRIDE) + 1;
            if (index > ndev) {
                return;
            }
            priority[index] = (int) (value & 0x7FFFFFFFL);
            return;
        }

        if (offset >= ENABLE_BASE && offset < CONTEXT_BASE && size == 4) {
            final var hart = (offset - ENABLE_BASE) / 0x100;
            final var word = (offset - ENABLE_BASE - hart * 0x100) / ENABLE_STRIDE;
            if (hart >= hartCount || word < 0 || word >= enable[hart].length) {
                return;
            }
            enable[hart][word] = (int) (value & 0xFFFFFFFFL);
            return;
        }

        if (offset >= CONTEXT_BASE && size == 4) {
            final var hart    = (offset - CONTEXT_BASE) / CONTEXT_STRIDE;
            final var context = offset - CONTEXT_BASE - hart * CONTEXT_STRIDE;
            if (hart >= hartCount) {
                return;
            }
            switch (context) {
                case THRESHOLD -> {
                    threshold[hart] = (int) (value & 0x7FFFFFFFL);
                }
                case CLAIM -> {
                    // TODO: implement claim
                }
            }
            return;
        }

        Log.error("invalid plic write offset=%x, size=%d, value=%x", offset, size, value);
    }

    @Override
    public @NotNull String toString() {
        return "plic@%x".formatted(begin);
    }

    public void setPending(final int source) {
        if (source <= 0 || source > ndev) {
            return;
        }
        pending[source] = 1;
    }

    public void clearPending(final int source) {
        if (source <= 0 || source > ndev) {
            return;
        }
        pending[source] = 0;
    }
}
