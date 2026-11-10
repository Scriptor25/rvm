package dev.scriptor.impl.device

import dev.scriptor.fdt.BuilderContext
import dev.scriptor.fdt.NodeBuilder
import dev.scriptor.machine.Device
import dev.scriptor.machine.IODevice
import dev.scriptor.machine.Machine
import java.io.PrintStream

class RNG : IODevice {

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    constructor(machine: Machine, begin: ULong) {
        this.machine = machine

        this.begin = begin
        this.end = begin + 0x1000UL
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        TODO()
    }

    override fun dump(out: PrintStream) {
        TODO()
    }

    override fun read(offset: UInt, size: UInt): ULong? {
        TODO()
    }

    override fun write(offset: UInt, size: UInt, value: ULong): Boolean {
        TODO()
    }
}