package io.scriptor.impl.v32

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.FPRFile
import java.io.PrintStream

class FPRFile32 : FPRFile {

    override val machine
        get() = hart.machine

    private val hart: Hart32

    constructor(hart: Hart32) {
        this.hart = hart
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        TODO()
    }

    override fun dump(out: PrintStream) {
        TODO()
    }

    override fun getfr(reg: UInt): UInt {
        TODO()
    }

    override fun getdr(reg: UInt): ULong {
        TODO()
    }

    override fun put(reg: UInt, value: UInt) {
        TODO()
    }

    override fun put(reg: UInt, value: ULong) {
        TODO()
    }
}