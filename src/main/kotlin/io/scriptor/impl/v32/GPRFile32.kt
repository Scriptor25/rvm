package io.scriptor.impl.v32

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.GPRFile
import io.scriptor.util.Log.format
import java.io.PrintStream

class GPRFile32 : GPRFile {

    override val machine
        get() = hart.machine

    private val hart: Hart32

    @OptIn(ExperimentalUnsignedTypes::class)
    private val values = UIntArray(32)

    constructor(hart: Hart32) {
        this.hart = hart
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        TODO()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun dump(out: PrintStream) {
        for (i in values.indices) {
            out.print(format("x%-2d: %08x  ", i, values[i]))

            if ((i + 1) % 4 == 0) {
                out.println()
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun reset() {
        values.fill(0U)
    }

    override fun getdu(reg: UInt): ULong = getwu(reg).toULong()
    override fun getd(reg: UInt): Long = getw(reg).toLong()

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun getwu(reg: UInt): UInt = if (reg == 0U) 0U else values[reg.toInt()]

    override fun getw(reg: UInt): Int = getwu(reg).toInt()
    override fun gethu(reg: UInt): UShort = getwu(reg).toUShort()
    override fun geth(reg: UInt): Short = getw(reg).toShort()
    override fun getbu(reg: UInt): UByte = getwu(reg).toUByte()
    override fun getb(reg: UInt): Byte = getw(reg).toByte()

    override fun put(reg: UInt, value: ULong) = put(reg, value.toUInt())
    override fun put(reg: UInt, value: Long) = put(reg, value.toInt())

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun put(reg: UInt, value: UInt) {
        if (reg != 0U) values[reg.toInt()] = value
    }

    override fun put(reg: UInt, value: Int) = put(reg, value.toUInt())
    override fun put(reg: UInt, value: UShort) = put(reg, value.toUInt())
    override fun put(reg: UInt, value: Short) = put(reg, value.toInt())
    override fun put(reg: UInt, value: UByte) = put(reg, value.toUInt())
    override fun put(reg: UInt, value: Byte) = put(reg, value.toInt())
}