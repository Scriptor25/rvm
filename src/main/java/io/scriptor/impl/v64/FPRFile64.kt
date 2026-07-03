package io.scriptor.impl.v64

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.FPRFile
import io.scriptor.util.Log.format
import java.io.PrintStream

class FPRFile64 : FPRFile {

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
            out.print(format("f%-2d: %016x ", i, values[i]))

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
    override fun getfr(reg: UInt): UInt = if (reg == 0U) 0U else values[reg.toInt()].toUInt()

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun getdr(reg: UInt): ULong = if (reg == 0U) 0UL else values[reg.toInt()]

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun put(reg: UInt, value: UInt) {
        if (reg != 0U) values[reg.toInt()] = value.toULong()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun put(reg: UInt, value: ULong) {
        if (reg != 0U) values[reg.toInt()] = value
    }
}
