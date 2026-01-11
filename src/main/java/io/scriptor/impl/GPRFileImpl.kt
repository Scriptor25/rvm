package io.scriptor.impl

import io.scriptor.machine.GPRFile
import io.scriptor.machine.Hart
import io.scriptor.machine.Machine
import io.scriptor.util.Log.format
import java.io.PrintStream

class GPRFileImpl : GPRFile {

    override val machine: Machine
        get() = hart.machine

    private val hart: Hart

    @OptIn(ExperimentalUnsignedTypes::class)
    private val values = ULongArray(32)

    constructor(hart: Hart) {
        this.hart = hart
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

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun put(reg: UInt, value: ULong) {
        if (reg != 0U) values[reg.toInt()] = value
    }
}
