package io.scriptor.impl.v64

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.GPRFile
import io.scriptor.util.Log.format
import java.io.PrintStream

class GPRFile64 : GPRFile {

    override val machine
        get() = hart.machine

    private val hart: Hart64

    @OptIn(ExperimentalUnsignedTypes::class)
    private val values = ULongArray(32)

    constructor(hart: Hart64) {
        this.hart = hart
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        TODO()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun dump(out: PrintStream) {
        for (i in values.indices) {
            out.print(format("x%-2d: %016x  ", i, values[i]))

            if ((i + 1) % 4 == 0) {
                out.println()
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun reset() {
        values.fill(0UL)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun getdu(reg: UInt): ULong = if (reg == 0U) 0UL else values[reg.toInt()]
    override fun getd(reg: UInt): Long = getdu(reg).toLong()
    override fun getwu(reg: UInt): UInt = getdu(reg).toUInt()
    override fun getw(reg: UInt): Int = getd(reg).toInt()
    override fun gethu(reg: UInt): UShort = getdu(reg).toUShort()
    override fun geth(reg: UInt): Short = getd(reg).toShort()
    override fun getbu(reg: UInt): UByte = getdu(reg).toUByte()
    override fun getb(reg: UInt): Byte = getd(reg).toByte()

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun put(reg: UInt, value: ULong) {
        if (reg != 0U) values[reg.toInt()] = value
    }

    override fun put(reg: UInt, value: Long) = put(reg, value.toULong())
    override fun put(reg: UInt, value: UInt) = put(reg, value.toULong())
    override fun put(reg: UInt, value: Int) = put(reg, value.toLong())
    override fun put(reg: UInt, value: UShort) = put(reg, value.toULong())
    override fun put(reg: UInt, value: Short) = put(reg, value.toLong())
    override fun put(reg: UInt, value: UByte) = put(reg, value.toULong())
    override fun put(reg: UInt, value: Byte) = put(reg, value.toLong())
}
