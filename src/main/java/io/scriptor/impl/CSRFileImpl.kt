package io.scriptor.impl

import io.scriptor.isa.CSR
import io.scriptor.machine.CSRFile
import io.scriptor.machine.Hart
import io.scriptor.machine.Machine
import java.io.PrintStream
import java.util.function.Consumer
import java.util.function.Supplier

@OptIn(ExperimentalUnsignedTypes::class)
class CSRFileImpl : CSRFile {

    override val machine: Machine
        get() = hart.machine

    private val hart: Hart
    private val metadata: MutableMap<UInt, CSRMeta> = HashMap()

    private val values = ULongArray(0x1000)
    private val present = BooleanArray(0x1000)

    constructor(hart: Hart) {
        this.hart = hart
    }

    override fun dump(out: PrintStream) {
        var j = 0
        for (i in values.indices) {
            if (!present[i]) continue

            out.printf("%03x: %016x  ", i, values[i])

            if (++j % 4 == 0) out.println()
        }

        if (j % 4 != 0) out.println()
    }

    override fun reset() {
        values.fill(0UL)
        present.fill(false)
    }

    override fun define(addr: UInt) {
        define(addr, 0UL.inv(), -1, 0UL)
    }

    override fun define(addr: UInt, mask: ULong) {
        define(addr, mask, -1, 0UL)
    }

    override fun define(addr: UInt, mask: ULong, base: Int) {
        define(addr, mask, base, 0UL)
    }

    override fun define(addr: UInt, mask: ULong, base: Int, value: ULong) {
        metadata[addr] = CSRMeta(mask, base, null, null, ArrayList(), ArrayList())
        present[addr.toInt()] = true
        values[addr.toInt()] = value and mask
    }

    override fun defineVal(addr: UInt, value: ULong) {
        define(addr, 0UL.inv(), -1, value)
    }

    override fun define(addr: UInt, mask: ULong, get: Supplier<ULong>) {
        metadata[addr] = CSRMeta(mask, -1, get, null, ArrayList(), ArrayList())
        present[addr.toInt()] = true
        values[addr.toInt()] = 0UL
    }

    override fun define(addr: UInt, mask: ULong, get: Supplier<ULong>, set: Consumer<ULong>) {
        metadata[addr] = CSRMeta(mask, -1, get, set, ArrayList(), ArrayList())
        present[addr.toInt()] = true
        values[addr.toInt()] = 0UL
    }

    override fun hookGet(addr: UInt, hook: Consumer<ULong>) {
        metadata[addr]!!.getHooks.add(hook)
    }

    override fun hookPut(addr: UInt, hook: Consumer<ULong>) {
        metadata[addr]!!.putHooks.add(hook)
    }

    override fun getdu(addr: UInt, priv: UInt): ULong {
        var addr = addr
        if (!present[addr.toInt()]) {
            error(addr.toULong(), "read csr addr=%03x, priv=%x: not present", addr, priv)
        }

        if (CSR.unprivileged(addr, priv)) {
            error(addr.toULong(), "read csr addr=%03x, priv=%x: unprivileged", addr, priv)
        }

        val meta = metadata[addr]!!
        val mask = meta.mask

        if (meta.get != null) {
            val value = meta.get.get() and mask
            for (hook in meta.getHooks) hook.accept(value)
            return value
        }

        var base = 0
        while (present[addr.toInt()] && (metadata[addr]!!.base.also { base = it }) >= 0) {
            addr = base.toUInt()
        }

        if (!present[addr.toInt()]) error(addr.toULong(), "subsequent read csr addr=%03x: not present", addr)
        val value = values[addr.toInt()] and mask
        for (hook in meta.getHooks) hook.accept(value)

        return value
    }

    override fun put(addr: UInt, priv: UInt, value: ULong) {
        var addr = addr
        if (!present[addr.toInt()]) {
            error(
                addr.toULong(),
                "write csr addr=%03x, priv=%x, val=%x: not present",
                addr,
                priv,
                value,
            )
        }

        if (CSR.unprivileged(addr, priv)) {
            error(
                addr.toULong(),
                "write csr addr=%03x, priv=%x, val=%x: unprivileged",
                addr,
                priv,
                value,
            )
        }

        if (CSR.readonly(addr)) {
            error(
                addr.toULong(),
                "write csr addr=%03x, priv=%x, val=%x: read-only",
                addr,
                priv,
                value,
            )
        }

        val meta = metadata[addr]
        val mask = meta!!.mask

        if (meta.get != null) {
            if (meta.set == null) {
                throw TrapException(
                    hart.id,
                    0x02UL,
                    addr.toULong(),
                    "write csr addr=%03x, priv=%x, val=%x: read-only",
                    addr,
                    priv,
                    value,
                )
            }

            val value = value and mask
            for (hook in meta.putHooks) {
                hook.accept(value)
            }

            meta.set.accept(value)
            return
        }

        var base = 0
        while (present[addr.toInt()] && (metadata[addr]!!.base.also { base = it }) >= 0) {
            addr = base.toUInt()
        }

        if (!present[addr.toInt()]) {
            error(
                addr.toULong(),
                "subsequent write csr addr=%03x, val=%x: not present",
                addr,
                value,
            )
        }

        val value = (values[addr.toInt()] and mask.inv()) or (value and mask)
        for (hook in meta.putHooks) {
            hook.accept(value)
        }

        values[addr.toInt()] = value
    }

    private fun error(addr: ULong, format: String, vararg args: Any) {
        throw TrapException(hart.id, 0x02UL, addr, format, args)
    }
}
