package io.scriptor.impl

import io.scriptor.isa.CSR
import io.scriptor.machine.Hart
import io.scriptor.util.Log
import io.scriptor.util.Log.format

class MMU(private val hart: Hart) {

    private data class Key(val vpn: ULong, val asid: ULong)

    private class Entry(
        val pteaddr: ULong,
        var pte: ULong,
        val vpn: ULong,
        val asid: ULong,
        val pgbase: ULong,
        val pgsize: ULong,
    ) {
        operator fun contains(vpn: ULong): Boolean = this.vpn <= vpn && vpn < this.vpn + (pgsize shr PAGE_SHIFT)
    }

    enum class Access {
        FETCH,
        READ,
        WRITE,
    }

    private val tlb: MutableMap<Key, Entry> = HashMap()

    fun flush(vaddr: ULong, asid: ULong) {
        if (vaddr == 0UL && asid == 0UL) {
            tlb.clear()
            return
        }

        val vpn = vaddr shr PAGE_SHIFT

        val remove = ArrayList<Key>()
        for (e in tlb) {
            val matches = (asid == 0UL || asid == e.key.asid) && (vaddr == 0UL || vpn in e.value)
            if (matches) {
                remove.add(e.key)
            }
        }

        for (key in remove) {
            tlb.remove(key)
        }
    }

    fun translate(
        priv: UInt,
        vaddr: ULong,
        access: Access,
        unsafe: Boolean,
    ): ULong {
        if (priv == CSR.CSR_M) {
            return vaddr
        }

        val reg = hart.csrFile.getdu(CSR.satp, priv)
        val mode = getMode(reg)
        val asid = getASID(reg)
        val ppn = getPPN(reg)

        return when (mode) {
            0U -> vaddr
            8U -> sv39(priv, vaddr, access, asid.toULong(), ppn, unsafe)
            9U -> sv48(priv, vaddr, access, asid.toULong(), ppn, unsafe)
            10U -> sv57(priv, vaddr, access, asid.toULong(), ppn, unsafe)
            else -> {
                pageFault(priv, vaddr, access, unsafe, format("unsupported mode %d", mode))
                0UL.inv()
            }
        }
    }

    private fun touch(
        priv: UInt,
        vaddr: ULong,
        access: Access,
        unsafe: Boolean,
        pteaddr: ULong,
        pte: ULong,
    ): ULong {
        var pte = pte
        if (inaccessible(priv, access, pte)) {
            pageFault(priv, vaddr, access, unsafe, "inaccessible")
        }

        if (unprivileged(priv, pte)) {
            pageFault(priv, vaddr, access, unsafe, "unprivileged")
        }

        if (!pteA(pte) || (access == Access.WRITE && !pteD(pte))) {
            pte = pte or (1UL shl 6)
            if (access == Access.WRITE) {
                pte = pte or (1UL shl 7)
            }
            hart.machine.pWrite(pteaddr, 8U, pte, unsafe)
        }

        return pte
    }

    private fun walk(
        levels: Int,
        priv: UInt,
        vaddr: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong {
        Log.info(
            "walk(levels=%d, priv=%d, vaddr=%016x, access=%s, asid=%x, root=%x)",
            levels,
            priv,
            vaddr,
            access,
            asid,
            root,
        )

        run {
            val vpn = vaddr shr PAGE_SHIFT
            var entry = tlb[Key(vpn, asid)]
            if (entry == null) {
                entry = tlb[Key(vpn, 0UL)]
            }
            if (entry != null && vpn in entry) {
                entry.pte = touch(priv, vaddr, access, unsafe, entry.pteaddr, entry.pte)

                val pgsize = entry.pgsize
                val pgbase = entry.pgbase
                val pgoffset = vaddr and (pgsize - 1UL)
                val ptevpn = entry.vpn
                val paddr = pgbase or pgoffset

                Log.info(
                    "   => found translation cache: pgsize=%x, pgbase=%016x, pgoffset=%03x, ptevpn=%x, paddr=%x",
                    pgsize,
                    pgbase,
                    pgoffset,
                    ptevpn,
                    paddr,
                )

                return paddr
            }
        }

        var a = root shl PAGE_SHIFT

        for (i in levels - 1 downTo 0) {
            val vpn = vpn(vaddr, i)

            Log.info("  i=%d, a=%016x, vpn=%03x", i, a, vpn)

            val pteaddr = a + vpn * 8UL

            var pte = hart.machine.pRead(pteaddr, 8U, unsafe)

            Log.info(
                "   => pteaddr=%016x, pte=%016x, V=%b, R=%b, W=%b, X=%b, U=%b, G=%b, A=%b, D=%b",
                pteaddr,
                pte,
                pteV(pte),
                pteR(pte),
                pteW(pte),
                pteX(pte),
                pteU(pte),
                pteG(pte),
                pteA(pte),
                pteD(pte),
            )

            if (!pteV(pte)) {
                pageFault(priv, vaddr, access, unsafe, "invalid entry")
                return 0UL.inv()
            }

            if (!pteR(pte) && pteW(pte)) {
                pageFault(priv, vaddr, access, unsafe, "reserved entry type")
                return 0UL.inv()
            }

            if (pteR(pte) || pteX(pte)) {
                pte = touch(priv, vaddr, access, unsafe, pteaddr, pte)

                val mask = (1UL shl (i * 9)) - 1UL
                val vvpn = vaddr shr PAGE_SHIFT

                var ppn = ppn(pte, i)
                if (i > 0) {
                    ppn = ppn and mask.inv()
                    ppn = ppn or (vvpn and mask)
                }

                val pgsize = 1UL shl (PAGE_SHIFT + i * 9)
                val pgbase = ppn shl PAGE_SHIFT
                val pgoffset = vaddr and (pgsize - 1UL)
                val paddr = pgbase or pgoffset

                val ptevpn = vvpn and mask.inv()

                add(Entry(pteaddr, pte, ptevpn, asid, pgbase, pgsize))

                Log.info(
                    "   => ppn=%x, pgsize=%x, pgbase=%016x, pgoffset=%03x, ptevpn=%x, paddr=%016x",
                    ppn,
                    pgsize,
                    pgbase,
                    pgoffset,
                    ptevpn,
                    paddr,
                )

                return paddr
            }

            a = ppn(pte) shl PAGE_SHIFT
        }

        pageFault(priv, vaddr, access, unsafe, "missing entry")
        return 0UL.inv()
    }

    private fun sv39(
        priv: UInt,
        vaddr: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong = walk(3, priv, vaddr, access, asid, root, unsafe)

    private fun sv48(
        priv: UInt,
        vaddr: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong = walk(4, priv, vaddr, access, asid, root, unsafe)

    private fun sv57(
        priv: UInt,
        vaddr: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong = walk(5, priv, vaddr, access, asid, root, unsafe)

    private fun add(entry: Entry) {
        val asid = if (pteG(entry.pte)) 0UL else entry.asid

        val pgcount = entry.pgsize shr PAGE_SHIFT
        for (i in 0UL..<pgcount) {
            val key = Key(entry.vpn + i, asid)
            tlb.put(key, entry)
        }
    }

    private fun unprivileged(priv: UInt, pte: ULong): Boolean {
        val status = hart.csrFile.getdu(CSR.sstatus, priv)
        val sum = (status and CSR.STATUS_SUM) != 0UL
        val u: Boolean = pteU(pte)

        return (!sum && u && priv != CSR.CSR_U) || (!u && priv == CSR.CSR_U)
    }

    private fun inaccessible(priv: UInt, access: Access, pte: ULong): Boolean {
        val u: Boolean = pteU(pte)
        val x: Boolean = pteX(pte)
        val w: Boolean = pteW(pte)
        val r: Boolean = pteR(pte)

        return when (access) {
            Access.FETCH -> !x || (u && priv != CSR.CSR_U)
            Access.READ -> !r
            Access.WRITE -> !w
        }
    }

    private fun pageFault(
        priv: UInt,
        vaddr: ULong,
        access: Access,
        unsafe: Boolean,
        message: String,
    ) {
        if (unsafe) {
            Log.warn(
                "page fault (priv=%d, vaddr=%016x, access=%s): %s",
                priv,
                vaddr,
                access,
                message,
            )
            return
        }
        throw TrapException(
            hart.id,
            toCause(access),
            vaddr,
            "page fault (priv=%d, vaddr=%016x, access=%s): %s",
            priv,
            vaddr,
            access,
            message,
        )
    }

    companion object {
        private const val FETCH_PAGE_FAULT = 0x0CUL
        private const val READ_PAGE_FAULT = 0x0DUL
        private const val WRITE_PAGE_FAULT = 0x0FUL

        private const val PAGE_SHIFT = 12

        private fun getMode(satp: ULong): UInt = ((satp shr 60) and 0xFUL).toUInt()

        private fun getASID(satp: ULong): UInt = ((satp shr 44) and 0xFFFFUL).toUInt()

        private fun getPPN(satp: ULong): ULong = satp and 0xFFFFFFFFFFFUL

        private fun toCause(access: Access): ULong = when (access) {
            Access.FETCH -> FETCH_PAGE_FAULT
            Access.READ -> READ_PAGE_FAULT
            Access.WRITE -> WRITE_PAGE_FAULT
        }

        private fun pteV(pte: ULong): Boolean = (pte and 1UL) != 0UL

        private fun pteR(pte: ULong): Boolean = ((pte shr 1) and 1UL) != 0UL

        private fun pteW(pte: ULong): Boolean = ((pte shr 2) and 1UL) != 0UL

        private fun pteX(pte: ULong): Boolean = ((pte shr 3) and 1UL) != 0UL

        private fun pteU(pte: ULong): Boolean = ((pte shr 4) and 1UL) != 0UL

        private fun pteG(pte: ULong): Boolean = ((pte shr 5) and 1UL) != 0UL

        private fun pteA(pte: ULong): Boolean = ((pte shr 6) and 1UL) != 0UL

        private fun pteD(pte: ULong): Boolean = ((pte shr 7) and 1UL) != 0UL

        private fun vpn(vaddr: ULong, i: Int): ULong = (vaddr shr (PAGE_SHIFT + i * 9)) and 0x1FFUL

        private fun ppn(pte: ULong, i: Int): ULong = ((pte shr 10) and 0xFFFFFFFFFFFUL) shr (i * 9)

        private fun ppn(pte: ULong): ULong = (pte shr 10) and 0xFFFFFFFFFFFUL
    }
}
