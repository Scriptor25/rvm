package io.scriptor.impl;

import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static io.scriptor.isa.CSR.*;

public final class MMU {

    private record Key(long vpn, long asid, int hartid) {
    }

    private record Entry(long pte, long vpn, long asid, int hartid, long paddr, long psize, long val) {

        public boolean contains(final long vpn) {
            final var pcount = psize >>> PAGE_SHIFT;
            return this.vpn <= vpn && vpn < this.vpn + pcount;
        }
    }

    private static final long FETCH_PAGE_FAULT = 0x0CL;
    private static final long READ_PAGE_FAULT = 0x0DL;
    private static final long WRITE_PAGE_FAULT = 0x0FL;

    private static final int PAGE_SHIFT = 12;

    public static final int ACCESS_FETCH = 0b001;
    public static final int ACCESS_READ = 0b010;
    public static final int ACCESS_WRITE = 0b100;

    private final Machine machine;

    private final Map<Key, Entry> tlb = new HashMap<>();

    public MMU(final @NotNull Machine machine) {
        this.machine = machine;
    }

    public void flush(final int hartid, final long vaddr, final long asid) {
        if (vaddr == 0L && asid == 0L) {
            tlb.clear();
            return;
        }

        final var vpn = vaddr >>> PAGE_SHIFT;

        tlb.entrySet().removeIf(e -> {
            final var key   = e.getKey();
            final var entry = e.getValue();
            return hartid == key.hartid
                   && (asid == 0L || asid == key.asid)
                   && (vaddr == 0L || entry.contains(vpn));
        });
    }

    public long translate(
            final int hartid,
            final long vaddr,
            final int access,
            final int privilege,
            final boolean unsafe
    ) {
        if (privilege >= CSR_H) {
            return vaddr;
        }

        final var reg  = machine.hart(hartid).csrFile().getd(satp, CSR_M);
        final var mode = getMode(reg);
        final var asid = getASID(reg);
        final var ppn  = getPPN(reg);

        return switch (mode) {
            case 0 -> vaddr;
            case 8 -> sv39(hartid, vaddr, access, privilege, asid, ppn, unsafe);
            case 9 -> sv48(hartid, vaddr, access, privilege, asid, ppn, unsafe);
            case 10 -> sv57(hartid, vaddr, access, privilege, asid, ppn, unsafe);
            default -> {
                if (unsafe) {
                    Log.warn("page fault: unsupported mode %d", mode);
                    yield ~0L;
                }
                throw new TrapException(toCause(access), vaddr, "page fault: unsupported mode %d", mode);
            }
        };
    }

    private long touch(
            final long pteAddr,
            long pte,
            final long vaddr,
            final int access,
            final int privilege,
            final boolean unsafe
    ) {
        if (fail(pte, access, privilege)) {
            if (!unsafe) {
                throw new TrapException(toCause(access), vaddr, "page fault: unprivileged");
            }
            Log.warn("page fault: unprivileged");
        }

        if (!pteA(pte) || (access == ACCESS_WRITE && !pteD(pte))) {
            pte |= (1L << 6);
            if (access == ACCESS_WRITE) {
                pte |= (1L << 7);
            }
            machine.pWrite(pteAddr, 8, pte, unsafe);
        }

        return pte;
    }

    private long walk(
            final int levels,
            final int hartid,
            final long vaddr,
            final int access,
            final int privilege,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        {
            final var vpn = vaddr >>> PAGE_SHIFT;

            var entry = tlb.get(new Key(vpn, asid, hartid));
            if (entry == null) {
                entry = tlb.get(new Key(vpn, 0L, hartid));
            }
            if (entry != null && entry.contains(vpn)) {
                touch(entry.pte, entry.val, vaddr, access, privilege, unsafe);

                final var pOffset = vaddr & (entry.psize - 1L);
                return entry.paddr + pOffset;
            }
        }

        final var mask = (1L << 9) - 1L;

        final var vpn = new long[levels];
        for (int i = 0; i < levels; ++i) {
            vpn[i] = (vaddr >>> (PAGE_SHIFT + i * 9)) & mask;
        }

        var a = root << PAGE_SHIFT;

        Log.info("walk(levels=%d, hartid=%x, vaddr=%x, access=%x, privilege=%x, asid=%x, root=%x)",
                 levels,
                 hartid,
                 vaddr,
                 access,
                 privilege,
                 asid,
                 root);

        for (int i = levels - 1; i >= 0; --i) {
            Log.info("  i=%d, a=%x, vpn=%x",
                     i,
                     a,
                     vpn[i]);

            final var pteAddr = a + vpn[i] * 8;

            var pte = machine.pRead(pteAddr, 8, unsafe);

            Log.info("   => pteAddr=%x, pte=%016x, V=%b, R=%b, W=%b, X=%b, U=%b, G=%b, A=%b, D=%b",
                     pteAddr,
                     pte,
                     pteV(pte),
                     pteR(pte),
                     pteW(pte),
                     pteX(pte),
                     pteU(pte),
                     pteG(pte),
                     pteA(pte),
                     pteD(pte));

            if (!pteV(pte)) {
                if (unsafe) {
                    Log.warn("page fault: invalid entry");
                    return ~0L;
                }
                throw new TrapException(toCause(access), vaddr, "page fault: invalid entry");
            }

            if (!pteR(pte) && pteW(pte)) {
                if (unsafe) {
                    Log.warn("page fault: reserved entry type");
                    return ~0L;
                }
                throw new TrapException(toCause(access), vaddr, "page fault: reserved entry type");
            }

            if (pteR(pte) || pteX(pte)) {
                pte = touch(pteAddr, pte, vaddr, access, privilege, unsafe);

                final var pSize   = 1L << (PAGE_SHIFT + i * 9);
                final var pOffset = vaddr & (pSize - 1L);

                final var ppn   = ppn(pte, i);
                final var pAddr = (ppn << PAGE_SHIFT);

                final var pteVpn = (vaddr >>> PAGE_SHIFT) & ~((1L << (i * 9)) - 1L);

                add(new Entry(pteAddr, pteVpn, asid, hartid, pAddr, pSize, pte));

                return pAddr + pOffset;
            }

            a = ppn(pte, 0) << PAGE_SHIFT;
        }

        if (unsafe) {
            Log.warn("page fault: missing entry");
            return ~0L;
        }
        throw new TrapException(toCause(access), vaddr, "page fault: missing entry");
    }

    private long sv39(
            final int hartid,
            final long vaddr,
            final int access,
            final int privilege,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(3, hartid, vaddr, access, privilege, asid, root, unsafe);
    }

    private long sv48(
            final int hartid,
            final long vaddr,
            final int access,
            final int privilege,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(4, hartid, vaddr, access, privilege, asid, root, unsafe);
    }

    private long sv57(
            final int hartid,
            final long vaddr,
            final int access,
            final int privilege,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(5, hartid, vaddr, access, privilege, asid, root, unsafe);
    }

    private void add(final @NotNull Entry entry) {
        final var asid = pteG(entry.val) ? 0L : entry.asid;

        final var pcount = entry.psize >>> PAGE_SHIFT;
        for (long i = 0L; i < pcount; ++i) {
            final var key = new Key(entry.vpn + i, asid, entry.hartid);
            tlb.put(key, entry);
        }
    }

    private static int getMode(final long satp) {
        return (int) ((satp >>> 60) & 0xFL);
    }

    private static int getASID(final long satp) {
        return (int) ((satp >>> 44) & 0xFFFFL);
    }

    private static long getPPN(final long satp) {
        return satp & 0xFFFFFFFFFFFL;
    }

    private static long toCause(final int access) {
        return switch (access) {
            case ACCESS_FETCH -> FETCH_PAGE_FAULT;
            case ACCESS_READ -> READ_PAGE_FAULT;
            case ACCESS_WRITE -> WRITE_PAGE_FAULT;
            default -> 0L;
        };
    }

    private static boolean fail(final long pte, final int access, final int privilege) {
        return (!pteU(pte) && privilege == CSR_U)
               || (!pteX(pte) && (access & ACCESS_FETCH) != 0)
               || (!pteW(pte) && (access & ACCESS_WRITE) != 0)
               || (!pteR(pte) && (access & ACCESS_READ) != 0);
    }

    private static boolean pteV(final long pte) {
        return (pte & 1L) != 0L;
    }

    private static boolean pteR(final long pte) {
        return ((pte >>> 1) & 1L) != 0L;
    }

    private static boolean pteW(final long pte) {
        return ((pte >>> 2) & 1L) != 0L;
    }

    private static boolean pteX(final long pte) {
        return ((pte >>> 3) & 1L) != 0L;
    }

    private static boolean pteU(final long pte) {
        return ((pte >>> 4) & 1L) != 0L;
    }

    private static boolean pteG(final long pte) {
        return ((pte >>> 5) & 1L) != 0L;
    }

    private static boolean pteA(final long pte) {
        return ((pte >>> 6) & 1L) != 0L;
    }

    private static boolean pteD(final long pte) {
        return ((pte >>> 7) & 1L) != 0L;
    }

    private static long ppn(final long pte, final int i) {
        return ((pte >>> 10) & 0xFFFFFFFFFFFL) >>> (i * 9);
    }
}
