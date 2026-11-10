package io.scriptor.impl.v32

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.CSRFile
import io.scriptor.machine.Device
import java.io.PrintStream
import java.util.function.Consumer
import java.util.function.Supplier

class CSRFile32 : CSRFile {

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

    override fun define(addr: UInt) {
        TODO()
    }

    override fun define(addr: UInt, mask: ULong) {
        TODO()
    }

    override fun define(addr: UInt, mask: ULong, base: Int) {
        TODO()
    }

    override fun define(addr: UInt, mask: ULong, base: Int, value: ULong) {
        TODO()
    }

    override fun defineVal(addr: UInt, value: ULong) {
        TODO()
    }

    override fun define(addr: UInt, mask: ULong, get: Supplier<ULong>) {
        TODO()
    }

    override fun define(
        addr: UInt,
        mask: ULong,
        get: Supplier<ULong>,
        set: Consumer<ULong>
    ) {
        TODO()
    }

    override fun get(addr: UInt, priv: UInt): ULong {
        TODO()
    }

    override fun put(addr: UInt, priv: UInt, value: ULong) {
        TODO()
    }
}