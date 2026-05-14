package io.scriptor.impl.v32

import io.scriptor.machine.FPRFile
import java.io.PrintStream

class FPRFile32 : FPRFile {

    override val machine
        get() = hart.machine

    private val hart: Hart32

    constructor(hart: Hart32) {
        this.hart = hart
    }

    override fun getfr(reg: UInt): UInt {
        TODO("Not yet implemented")
    }

    override fun getdr(reg: UInt): ULong {
        TODO("Not yet implemented")
    }

    override fun put(reg: UInt, value: UInt) {
        TODO("Not yet implemented")
    }

    override fun put(reg: UInt, value: ULong) {
        TODO("Not yet implemented")
    }

    override fun dump(out: PrintStream) {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}