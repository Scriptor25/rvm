package io.scriptor.impl

import io.scriptor.isa.CSR
import io.scriptor.machine.Hart
import io.scriptor.util.Log
import io.scriptor.util.Log.format

class MMU {

    private data class Key(val vpn: ULong, val asid: ULong)

    private class Entry(
        val pteAddress: ULong,
        var pte: ULong,
        val vpn: ULong,
        val asid: ULong,
        val pgBase: ULong,
        val pgSize: ULong,
    ) {
        operator fun contains(vpn: ULong): Boolean = this.vpn <= vpn && vpn < this.vpn + (pgSize shr PAGE_SHIFT)
    }

    enum class Access {
        FETCH,
        READ,
        WRITE,
    }

    private val hart: Hart
    private val tlb: MutableMap<Key, Entry> = HashMap()

    constructor(hart: Hart) {
        this.hart = hart
    }

    fun flush(vAddress: ULong, asid: ULong) {
        if (vAddress == 0UL && asid == 0UL) {
            tlb.clear()
            return
        }

        val vpn = vAddress shr PAGE_SHIFT

        val remove = ArrayList<Key>()
        for (e in tlb) {
            val matches = (asid == 0UL || asid == e.key.asid) && (vAddress == 0UL || vpn in e.value)
            if (matches) {
                remove.add(e.key)
            }
        }

        for (key in remove) {
            tlb.remove(key)
        }
    }

    fun translate(
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        unsafe: Boolean,
    ): ULong {
        if (privilege == CSR.CSR_M) {
            return vAddress
        }

        val reg = hart.csrFile[CSR.satp, privilege]
        val mode = getMode(reg)
        val asid = getASID(reg)
        val ppn = getPPN(reg)

        return when (mode) {
            0U -> vAddress
            8U -> sv39(privilege, vAddress, access, asid.toULong(), ppn, unsafe)
            9U -> sv48(privilege, vAddress, access, asid.toULong(), ppn, unsafe)
            10U -> sv57(privilege, vAddress, access, asid.toULong(), ppn, unsafe)
            else -> {
                pageFault(privilege, vAddress, access, unsafe, format("unsupported mode %d", mode))
                0UL.inv()
            }
        }
    }

    private fun touch(
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        unsafe: Boolean,
        pteAddress: ULong,
        pte: ULong,
    ): ULong {
        var pte = pte
        if (inaccessible(privilege, access, pte)) {
            pageFault(privilege, vAddress, access, unsafe, "inaccessible")
        }

        if (unprivileged(privilege, pte)) {
            pageFault(privilege, vAddress, access, unsafe, "unprivileged")
        }

        if (!pteA(pte) || (access == Access.WRITE && !pteD(pte))) {
            pte = pte or (1UL shl 6)
            if (access == Access.WRITE) {
                pte = pte or (1UL shl 7)
            }
            hart.machine.pWrite(pteAddress, 8U, pte, unsafe)
        }

        return pte
    }

    private fun walk(
        levels: Int,
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong {
        Log.info(
            "walk(levels=%d, priv=%d, vaddr=%016x, access=%s, asid=%x, root=%x)",
            levels,
            privilege,
            vAddress,
            access,
            asid,
            root,
        )

        run {
            val vpn = vAddress shr PAGE_SHIFT
            var entry = tlb[Key(vpn, asid)]
            if (entry == null) {
                entry = tlb[Key(vpn, 0UL)]
            }
            if (entry != null && vpn in entry) {
                entry.pte = touch(privilege, vAddress, access, unsafe, entry.pteAddress, entry.pte)

                val pgSize = entry.pgSize
                val pgBase = entry.pgBase
                val pgOffset = vAddress and (pgSize - 1UL)
                val pteVpn = entry.vpn
                val pAddress = pgBase or pgOffset

                Log.info(
                    "   => found translation cache: pgsize=%x, pgbase=%016x, pgoffset=%03x, ptevpn=%x, paddr=%x",
                    pgSize,
                    pgBase,
                    pgOffset,
                    pteVpn,
                    pAddress,
                )

                return pAddress
            }
        }

        var a = root shl PAGE_SHIFT

        for (i in levels - 1 downTo 0) {
            val vpn = vpn(vAddress, i)

            Log.info("  i=%d, a=%016x, vpn=%03x", i, a, vpn)

            val pteAddress = a + vpn * 8UL

            var pte = hart.machine.pRead(pteAddress, 8U, unsafe)

            Log.info(
                "   => pteaddr=%016x, pte=%016x, V=%b, R=%b, W=%b, X=%b, U=%b, G=%b, A=%b, D=%b",
                pteAddress,
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
                pageFault(privilege, vAddress, access, unsafe, "invalid entry")
                return 0UL.inv()
            }

            if (!pteR(pte) && pteW(pte)) {
                pageFault(privilege, vAddress, access, unsafe, "reserved entry type")
                return 0UL.inv()
            }

            if (pteR(pte) || pteX(pte)) {
                pte = touch(privilege, vAddress, access, unsafe, pteAddress, pte)

                val mask = (1UL shl (i * 9)) - 1UL
                val vVpn = vAddress shr PAGE_SHIFT

                var ppn = ppn(pte, i)
                if (i > 0) {
                    ppn = ppn and mask.inv()
                    ppn = ppn or (vVpn and mask)
                }

                val pgSize = 1UL shl (PAGE_SHIFT + i * 9)
                val pgBase = ppn shl PAGE_SHIFT
                val pgOffset = vAddress and (pgSize - 1UL)
                val pAddress = pgBase or pgOffset

                val pteVpn = vVpn and mask.inv()

                add(Entry(pteAddress, pte, pteVpn, asid, pgBase, pgSize))

                Log.info(
                    "   => ppn=%x, pgsize=%x, pgbase=%016x, pgoffset=%03x, ptevpn=%x, paddr=%016x",
                    ppn,
                    pgSize,
                    pgBase,
                    pgOffset,
                    pteVpn,
                    pAddress,
                )

                return pAddress
            }

            a = ppn(pte) shl PAGE_SHIFT
        }

        pageFault(privilege, vAddress, access, unsafe, "missing entry")
        return 0UL.inv()
    }

    private fun sv39(
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong = walk(3, privilege, vAddress, access, asid, root, unsafe)

    private fun sv48(
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong = walk(4, privilege, vAddress, access, asid, root, unsafe)

    private fun sv57(
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        asid: ULong,
        root: ULong,
        unsafe: Boolean,
    ): ULong = walk(5, privilege, vAddress, access, asid, root, unsafe)

    private fun add(entry: Entry) {
        val asid = if (pteG(entry.pte)) 0UL else entry.asid

        val pgCount = entry.pgSize shr PAGE_SHIFT
        for (i in 0UL..<pgCount) {
            val key = Key(entry.vpn + i, asid)
            tlb[key] = entry
        }
    }

    private fun unprivileged(privilege: UInt, pte: ULong): Boolean {
        val status = hart.csrFile[CSR.sstatus, privilege]
        val sum = (status and CSR.STATUS_SUM) != 0UL
        val u: Boolean = pteU(pte)

        return (!sum && u && privilege != CSR.CSR_U) || (!u && privilege == CSR.CSR_U)
    }

    private fun inaccessible(privilege: UInt, access: Access, pte: ULong): Boolean {
        val u: Boolean = pteU(pte)
        val x: Boolean = pteX(pte)
        val w: Boolean = pteW(pte)
        val r: Boolean = pteR(pte)

        return when (access) {
            Access.FETCH -> !x || (u && privilege != CSR.CSR_U)
            Access.READ -> !r
            Access.WRITE -> !w
        }
    }

    private fun pageFault(
        privilege: UInt,
        vAddress: ULong,
        access: Access,
        unsafe: Boolean,
        message: String,
    ) {
        if (unsafe) {
            Log.warn(
                "page fault (priv=%d, vaddr=%016x, access=%s): %s",
                privilege,
                vAddress,
                access,
                message,
            )
            return
        }
        throw TrapException(
            hart.id,
            toCause(access),
            vAddress,
            "page fault (priv=%d, vaddr=%016x, access=%s): %s",
            privilege,
            vAddress,
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

        private fun vpn(vAddress: ULong, i: Int): ULong = (vAddress shr (PAGE_SHIFT + i * 9)) and 0x1FFUL

        private fun ppn(pte: ULong, i: Int): ULong = ((pte shr 10) and 0xFFFFFFFFFFFUL) shr (i * 9)

        private fun ppn(pte: ULong): ULong = (pte shr 10) and 0xFFFFFFFFFFFUL
    }
}
