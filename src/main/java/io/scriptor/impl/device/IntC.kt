package io.scriptor.impl.device

import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import java.io.PrintStream

class IntC : IODevice {

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    constructor(machine: Machine, begin: ULong) {
        this.machine = machine

        this.begin = begin
        this.end = begin + 0x1000UL
    }

    override fun dump(out: PrintStream) {
    }

    override fun reset() {
    }

    override fun read(offset: UInt, size: UInt): ULong? {
        return null
    }

    override fun write(offset: UInt, size: UInt, value: ULong): Boolean {
        return false
    }
}