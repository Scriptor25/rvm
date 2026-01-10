package io.scriptor.impl

import io.scriptor.machine.FPRFile
import io.scriptor.machine.Hart
import io.scriptor.machine.Machine
import java.io.PrintStream

@OptIn(ExperimentalUnsignedTypes::class)
class FPRFileImpl : FPRFile {

    override val machine: Machine
        get() = hart.machine

    private val hart: Hart
    private val values = ULongArray(32)

    constructor(hart: Hart) {
        this.hart = hart
    }

    override fun dump(out: PrintStream) {
        for (i in values.indices) {
            out.printf("f%-2d: %016x ", i, values[i])

            if ((i + 1) % 4 == 0) {
                out.println()
            }
        }
    }

    override fun reset() {
        values.fill(0UL)
    }

    override fun getfr(reg: UInt): UInt {
        if (reg == 0U) {
            return 0U
        }
        return values[reg.toInt()].toUInt()
    }

    override fun getdr(reg: UInt): ULong {
        if (reg == 0U) {
            return 0UL
        }
        return values[reg.toInt()]
    }

    override fun put(reg: UInt, value: UInt) {
        if (reg == 0U) {
            return
        }
        values[reg.toInt()] = value.toULong()
    }

    override fun put(reg: UInt, value: ULong) {
        if (reg == 0U) {
            return
        }
        values[reg.toInt()] = value
    }
}
