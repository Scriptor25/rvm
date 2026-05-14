package io.scriptor.impl.v32

import io.scriptor.machine.CSRFile
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

    override fun define(addr: UInt) {
        TODO("Not yet implemented")
    }

    override fun define(addr: UInt, mask: ULong) {
        TODO("Not yet implemented")
    }

    override fun define(addr: UInt, mask: ULong, base: Int) {
        TODO("Not yet implemented")
    }

    override fun define(addr: UInt, mask: ULong, base: Int, value: ULong) {
        TODO("Not yet implemented")
    }

    override fun defineVal(addr: UInt, value: ULong) {
        TODO("Not yet implemented")
    }

    override fun define(addr: UInt, mask: ULong, get: Supplier<ULong>) {
        TODO("Not yet implemented")
    }

    override fun define(
        addr: UInt,
        mask: ULong,
        get: Supplier<ULong>,
        set: Consumer<ULong>
    ) {
        TODO("Not yet implemented")
    }

    override fun hookGet(addr: UInt, hook: Consumer<ULong>) {
        TODO("Not yet implemented")
    }

    override fun hookPut(addr: UInt, hook: Consumer<ULong>) {
        TODO("Not yet implemented")
    }

    override fun get(addr: UInt, priv: UInt): ULong {
        TODO("Not yet implemented")
    }

    override fun put(addr: UInt, priv: UInt, value: ULong) {
        TODO("Not yet implemented")
    }

    override fun dump(out: PrintStream) {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}