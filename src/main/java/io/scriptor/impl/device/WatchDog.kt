package io.scriptor.impl.device

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import java.io.PrintStream

class WatchDog : IODevice {

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