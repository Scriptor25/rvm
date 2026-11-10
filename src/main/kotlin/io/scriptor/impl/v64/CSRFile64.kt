package io.scriptor.impl.v64

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.impl.CSRMeta
import io.scriptor.impl.TrapException
import io.scriptor.isa.CSR
import io.scriptor.machine.CSRFile
import io.scriptor.machine.Device
import io.scriptor.util.Log.format
import java.io.PrintStream
import java.util.function.Consumer
import java.util.function.Supplier

class CSRFile64 : CSRFile {

    override val machine
        get() = hart.machine

    private val hart: Hart64
    private val metadata: MutableMap<UInt, CSRMeta> = HashMap()

    @OptIn(ExperimentalUnsignedTypes::class)
    private val values = ULongArray(0x1000)
    private val present = BooleanArray(0x1000)

    constructor(hart: Hart64) {
        this.hart = hart
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        TODO()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun dump(out: PrintStream) {
        var j = 0
        for (i in values.indices) {
            if (!present[i]) continue

            out.print(format("%03x: %016x  ", i, values[i]))

            if (++j % 4 == 0) out.println()
        }

        if (j % 4 != 0) out.println()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
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

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun define(addr: UInt, mask: ULong, base: Int, value: ULong) {
        metadata[addr] = CSRMeta(mask, base, null, null)
        present[addr.toInt()] = true
        values[addr.toInt()] = value and mask
    }

    override fun defineVal(addr: UInt, value: ULong) {
        define(addr, 0UL.inv(), -1, value)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun define(addr: UInt, mask: ULong, get: Supplier<ULong>) {
        metadata[addr] = CSRMeta(mask, -1, get, null)
        present[addr.toInt()] = true
        values[addr.toInt()] = 0UL
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun define(addr: UInt, mask: ULong, get: Supplier<ULong>, set: Consumer<ULong>) {
        metadata[addr] = CSRMeta(mask, -1, get, set)
        present[addr.toInt()] = true
        values[addr.toInt()] = 0UL
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun get(addr: UInt, priv: UInt): ULong {
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
            return meta.get.get() and mask
        }

        var base = 0
        while (present[addr.toInt()] && (metadata[addr]!!.base.also { base = it }) >= 0) {
            addr = base.toUInt()
        }

        if (!present[addr.toInt()]) error(addr.toULong(), "subsequent read csr addr=%03x: not present", addr)

        return values[addr.toInt()] and mask
    }

    @OptIn(ExperimentalUnsignedTypes::class)
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

            meta.set.accept(value and mask)
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

        values[addr.toInt()] = (values[addr.toInt()] and mask.inv()) or (value and mask)
    }

    private fun error(addr: ULong, format: String, vararg args: Any?) {
        throw TrapException(hart.id, 0x02UL, addr, format, *args)
    }
}
