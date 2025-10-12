package io.scriptor.impl;

import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.ObjectObjectHashMap;
import com.carrotsearch.hppc.ObjectObjectMap;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import static io.scriptor.isa.CSR.*;

public final class MMU {

    private record Key(long vpn, long asid, int hartid) {
    }

    private static final class Entry {

        long pteaddr;
        long pte;
        long vpn;
        long asid;
        int hartid;
        long pgbase;
        long pgsize;

        private Entry(
                final long pteaddr,
                final long pte,
                final long vpn,
                final long asid,
                final int hartid,
                final long pgbase,
                final long pgsize
        ) {
            this.pteaddr = pteaddr;
            this.pte = pte;
            this.vpn = vpn;
            this.asid = asid;
            this.hartid = hartid;
            this.pgbase = pgbase;
            this.pgsize = pgsize;
        }

        public boolean contains(final long vpn) {
            final var pgcount = pgsize >>> PAGE_SHIFT;
            return this.vpn <= vpn && vpn < this.vpn + pgcount;
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

    private final ObjectObjectMap<Key, Entry> tlb = new ObjectObjectHashMap<>();

    public MMU(final @NotNull Machine machine) {
        this.machine = machine;
    }

    public void flush(final int hartid, final long vaddr, final long asid) {
        if (vaddr == 0L && asid == 0L) {
            tlb.clear();
            return;
        }

        final var vpn = vaddr >>> PAGE_SHIFT;

        final var remove = new ObjectArrayList<Key>();
        for (final var e : tlb) {
            final var matches = hartid == e.key.hartid
                                && (asid == 0L || asid == e.key.asid)
                                && (vaddr == 0L || e.value.contains(vpn));
            if (matches) {
                remove.add(e.key);
            }
        }

        for (final var key : remove) {
            tlb.remove(key.value);
        }
    }

    public long translate(
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
            final boolean unsafe
    ) {
        if (priv >= CSR_H) {
            return vaddr;
        }

        final var reg  = machine.hart(hartid).csrFile().getd(satp, CSR_M);
        final var mode = getMode(reg);
        final var asid = getASID(reg);
        final var ppn  = getPPN(reg);

        return switch (mode) {
            case 0 -> vaddr;
            case 8 -> sv39(hartid, vaddr, access, priv, asid, ppn, unsafe);
            case 9 -> sv48(hartid, vaddr, access, priv, asid, ppn, unsafe);
            case 10 -> sv57(hartid, vaddr, access, priv, asid, ppn, unsafe);
            default -> {
                if (unsafe) {
                    Log.warn("page fault (hartid=%x, vaddr=%x, access=%x, priv=%x): unsupported mode %d",
                             hartid,
                             vaddr,
                             access,
                             priv,
                             mode);
                    yield ~0L;
                }
                throw new TrapException(toCause(access),
                                        vaddr,
                                        "page fault (hartid=%x, vaddr=%x, access=%x, priv=%x): unsupported mode %d",
                                        hartid,
                                        vaddr,
                                        access,
                                        priv,
                                        mode);
            }
        };
    }

    private long touch(
            final long pteaddr,
            long pte,
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
            final boolean unsafe
    ) {
        if (inaccessible(pte, access, priv)) {
            pageFault(hartid, vaddr, access, priv, unsafe, "inaccessible");
        }
        if (unprivileged(hartid, priv, pte)) {
            pageFault(hartid, vaddr, access, priv, unsafe, "unprivileged");
        }

        if (!pteA(pte) || (access == ACCESS_WRITE && !pteD(pte))) {
            pte |= (1L << 6);
            if (access == ACCESS_WRITE) {
                pte |= (1L << 7);
            }
            machine.pWrite(pteaddr, 8, pte, unsafe);
        }

        return pte;
    }

    private long walk(
            final int levels,
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
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
                entry.pte = touch(entry.pteaddr, entry.pte, hartid, vaddr, access, priv, unsafe);

                final var pgoffset = vaddr & (entry.pgsize - 1L);
                return entry.pgbase | pgoffset;
            }
        }

        var a = root << PAGE_SHIFT;

        Log.info("walk(levels=%d, hartid=%x, vaddr=%x, access=%x, priv=%x, asid=%x, root=%x)",
                 levels,
                 hartid,
                 vaddr,
                 access,
                 priv,
                 asid,
                 root);

        for (int i = levels - 1; i >= 0; --i) {
            final var vpn = vpn(vaddr, i);

            Log.info("  i=%d, a=%x, vpn=%x", i, a, vpn);

            final var pteaddr = a + vpn * 8L;

            var pte = machine.pRead(pteaddr, 8, unsafe);

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
                pageFault(hartid, vaddr, access, priv, unsafe, "invalid entry");
                return ~0L;
            }

            if (!pteR(pte) && pteW(pte)) {
                pageFault(hartid, vaddr, access, priv, unsafe, "reserved entry type");
                return ~0L;
            }

            if (pteR(pte) || pteX(pte)) {
                pte = touch(pteaddr, pte, hartid, vaddr, access, priv, unsafe);

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

                add(new Entry(pteaddr, pte, ptevpn, asid, hartid, pgbase, pgsize));

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

        pageFault(hartid, vaddr, access, priv, unsafe, "missing entry");
        return ~0L;
    }

    private long sv39(
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(3, hartid, vaddr, access, priv, asid, root, unsafe);
    }

    private long sv48(
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(4, hartid, vaddr, access, priv, asid, root, unsafe);
    }

    private long sv57(
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
            final long asid,
            final long root,
            final boolean unsafe
    ) {
        return walk(5, hartid, vaddr, access, priv, asid, root, unsafe);
    }

    private void add(final @NotNull Entry entry) {
        final var asid = pteG(entry.pte) ? 0L : entry.asid;

        final var pgcount = entry.pgsize >>> PAGE_SHIFT;
        for (long i = 0L; i < pgcount; ++i) {
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

    private boolean unprivileged(final int hartid, final int priv, final long pte) {
        final var status = machine.hart(hartid).csrFile().getd(sstatus, priv);
        final var sum    = (status & STATUS_SUM) != 0L;
        final var u      = pteU(pte);

        return (!sum && u && priv != CSR_U) || (!u && priv == CSR_U);
    }

    private boolean inaccessible(final long pte, final int access, final int priv) {

        final var u = pteU(pte);
        final var x = pteX(pte);
        final var w = pteW(pte);
        final var r = pteR(pte);

        return switch (access) {
            case ACCESS_FETCH -> !x || (u && priv != CSR_U);
            case ACCESS_READ -> !r;
            case ACCESS_WRITE -> !w;
            default -> false;
        };
    }

    private void pageFault(
            final int hartid,
            final long vaddr,
            final int access,
            final int priv,
            final boolean unsafe,
            final @NotNull String message
    ) {
        if (unsafe) {
            Log.warn("page fault (hartid=%x, vaddr=%016x, access=%x, priv=%x): %s",
                     hartid,
                     vaddr,
                     access,
                     priv,
                     message);
            return;
        }
        throw new TrapException(toCause(access),
                                vaddr,
                                "page fault (hartid=%x, vaddr=%016x, access=%x, priv=%x): %s",
                                hartid,
                                vaddr,
                                access,
                                priv,
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
