package io.scriptor.impl;

import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.ObjectObjectHashMap;
import com.carrotsearch.hppc.ObjectObjectMap;
import io.scriptor.machine.Hart;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import static io.scriptor.isa.CSR.*;

public final class MMU {

    private record Key(long vpn, long asid) {
    }

    private static final class Entry {

        long pteaddr;
        long pte;
        long vpn;
        long asid;
        long pgbase;
        long pgsize;

        private Entry(
                final long pteaddr,
                final long pte,
                final long vpn,
                final long asid,
                final long pgbase,
                final long pgsize
        ) {
            this.pteaddr = pteaddr;
            this.pte = pte;
            this.vpn = vpn;
            this.asid = asid;
            this.pgbase = pgbase;
            this.pgsize = pgsize;
        }

        public boolean contains(final long vpn) {
            final var pgcount = pgsize >>> PAGE_SHIFT;
            return this.vpn <= vpn && vpn < this.vpn + pgcount;
        }
    }

    public enum Access {
        FETCH,
        READ,
        WRITE,
    }

    private static final long FETCH_PAGE_FAULT = 0x0CL;
    private static final long READ_PAGE_FAULT = 0x0DL;
    private static final long WRITE_PAGE_FAULT = 0x0FL;

    private static final int PAGE_SHIFT = 12;

    private final Hart hart;

    private final ObjectObjectMap<Key, Entry> tlb = new ObjectObjectHashMap<>();

    public MMU(final @NotNull Hart hart) {
        this.hart = hart;
    }

    public void flush(final long vaddr, final long asid) {
        if (vaddr == 0L && asid == 0L) {
            tlb.clear();
            return;
        }

        final var vpn = vaddr >>> PAGE_SHIFT;

        final var remove = new ObjectArrayList<Key>();
        for (final var e : tlb) {
            final var matches = (asid == 0L || asid == e.key.asid) && (vaddr == 0L || e.value.contains(vpn));
            if (matches) {
                remove.add(e.key);
            }
        }

        for (final var key : remove) {
            tlb.remove(key.value);
        }
    }

    public long translate(
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final boolean unsafe
    ) {
        if (priv == CSR_M) {
            return vaddr;
        }

        final var reg  = hart.csrFile().getd(satp, priv);
        final var mode = getMode(reg);
        final var asid = getASID(reg);
        final var ppn  = getPPN(reg);

        return switch (mode) {
            case 0 -> vaddr;
            case 8 -> sv39(priv, vaddr, access, asid, ppn, unsafe);
            case 9 -> sv48(priv, vaddr, access, asid, ppn, unsafe);
            case 10 -> sv57(priv, vaddr, access, asid, ppn, unsafe);
            default -> {
                pageFault(priv, vaddr, access, unsafe, "unsupported mode %d".formatted(mode));
                yield ~0L;
            }
        };
    }

    private long touch(
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final boolean unsafe,
            final long pteaddr,
            long pte
    ) {
        if (inaccessible(priv, access, pte)) {
            pageFault(priv, vaddr, access, unsafe, "inaccessible");
        }

        if (unprivileged(priv, pte)) {
            pageFault(priv, vaddr, access, unsafe, "unprivileged");
        }

        if (!pteA(pte) || (access == Access.WRITE && !pteD(pte))) {
            pte |= (1L << 6);
            if (access == Access.WRITE) {
                pte |= (1L << 7);
            }
            hart.machine().pWrite(pteaddr, 8, pte, unsafe);
        }

        return pte;
    }

    private long walk(
            final int levels,
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        {
            final var vpn = vaddr >>> PAGE_SHIFT;

            var entry = tlb.get(new Key(vpn, asid));
            if (entry == null) {
                entry = tlb.get(new Key(vpn, 0L));
            }
            if (entry != null && entry.contains(vpn)) {
                entry.pte = touch(priv, vaddr, access, unsafe, entry.pteaddr, entry.pte);

                final var pgoffset = vaddr & (entry.pgsize - 1L);
                return entry.pgbase | pgoffset;
            }
        }

        var a = root << PAGE_SHIFT;

        Log.info("walk(levels=%d, priv=%x, vaddr=%x, access=%s, asid=%x, root=%x)",
                 levels,
                 priv,
                 vaddr,
                 access,
                 asid,
                 root);

        for (int i = levels - 1; i >= 0; --i) {
            final var vpn = vpn(vaddr, i);

            Log.info("  i=%d, a=%x, vpn=%x", i, a, vpn);

            final var pteaddr = a + vpn * 8L;

            var pte = hart.machine().pRead(pteaddr, 8, unsafe);

            Log.info("   => pteaddr=%x, pte=%016x, V=%b, R=%b, W=%b, X=%b, U=%b, G=%b, A=%b, D=%b",
                     pteaddr,
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
                pageFault(priv, vaddr, access, unsafe, "invalid entry");
                return ~0L;
            }

            if (!pteR(pte) && pteW(pte)) {
                pageFault(priv, vaddr, access, unsafe, "reserved entry type");
                return ~0L;
            }

            if (pteR(pte) || pteX(pte)) {
                pte = touch(priv, vaddr, access, unsafe, pteaddr, pte);

                final var mask = (1L << (i * 9)) - 1L;
                final var vvpn = vaddr >>> PAGE_SHIFT;

                var ppn = ppn(pte, i);
                if (i > 0) {
                    ppn &= ~mask;
                    ppn |= vvpn & mask;
                }

                final var pgsize   = 1L << (PAGE_SHIFT + i * 9);
                final var pgbase   = ppn << PAGE_SHIFT;
                final var pgoffset = vaddr & (pgsize - 1L);
                final var paddr    = pgbase | pgoffset;

                final var ptevpn = vvpn & ~mask;

                add(new Entry(pteaddr, pte, ptevpn, asid, pgbase, pgsize));

                Log.info("   => ppn=%x, pgsize=%x, pgbase=%x, pgoffset=%x, ptevpn=%x, paddr=%x",
                         ppn,
                         pgsize,
                         pgbase,
                         pgoffset,
                         ptevpn,
                         paddr);

                return paddr;
            }

            a = ppn(pte) << PAGE_SHIFT;
        }

        pageFault(priv, vaddr, access, unsafe, "missing entry");
        return ~0L;
    }

    private long sv39(
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(3, priv, vaddr, access, asid, root, unsafe);
    }

    private long sv48(
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(4, priv, vaddr, access, asid, root, unsafe);
    }

    private long sv57(
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(5, priv, vaddr, access, asid, root, unsafe);
    }

    private void add(final @NotNull Entry entry) {
        final var asid = pteG(entry.pte) ? 0L : entry.asid;

        final var pgcount = entry.pgsize >>> PAGE_SHIFT;
        for (long i = 0L; i < pgcount; ++i) {
            final var key = new Key(entry.vpn + i, asid);
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

    private static long toCause(final @NotNull Access access) {
        return switch (access) {
            case FETCH -> FETCH_PAGE_FAULT;
            case READ -> READ_PAGE_FAULT;
            case WRITE -> WRITE_PAGE_FAULT;
        };
    }

    private boolean unprivileged(final int priv, final long pte) {
        final var status = hart.csrFile().getd(sstatus, priv);
        final var sum    = (status & STATUS_SUM) != 0L;
        final var u      = pteU(pte);

        return (!sum && u && priv != CSR_U) || (!u && priv == CSR_U);
    }

    private boolean inaccessible(final int priv, final @NotNull Access access, final long pte) {

        final var u = pteU(pte);
        final var x = pteX(pte);
        final var w = pteW(pte);
        final var r = pteR(pte);

        return switch (access) {
            case FETCH -> !x || (u && priv != CSR_U);
            case READ -> !r;
            case WRITE -> !w;
        };
    }

    private void pageFault(
            final int priv,
            final long vaddr,
            final @NotNull Access access,
            final boolean unsafe,
            final @NotNull String message
    ) {
        if (unsafe) {
            Log.warn("page fault (priv=%x, vaddr=%016x, access=%s): %s",
                     priv,
                     vaddr,
                     access,
                     message);
            return;
        }
        throw new TrapException(toCause(access),
                                vaddr,
                                "page fault (priv=%x, vaddr=%016x, access=%s): %s",
                                priv,
                                vaddr,
                                access,
                                message);
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

    private static long vpn(final long vaddr, final int i) {
        return (vaddr >>> (PAGE_SHIFT + i * 9)) & 0x1FFL;
    }

    private static long ppn(final long pte, final int i) {
        return ((pte >>> 10) & 0xFFFFFFFFFFFL) >>> (i * 9);
    }

    private static long ppn(final long pte) {
        return (pte >>> 10) & 0xFFFFFFFFFFFL;
    }
}
