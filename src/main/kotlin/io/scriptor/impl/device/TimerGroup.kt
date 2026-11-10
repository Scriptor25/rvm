package io.scriptor.impl.device

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import java.io.PrintStream

class TimerGroup : IODevice {

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    val timers: Array<Any?>
    val watchdogs: Array<Any?>

    constructor(machine: Machine, begin: ULong, timers: Int, watchdogs: Int) {
        this.machine = machine

        this.begin = begin
        this.end = begin + 0x1000UL

        this.timers = arrayOfNulls(timers)
        this.watchdogs = arrayOfNulls(watchdogs)
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