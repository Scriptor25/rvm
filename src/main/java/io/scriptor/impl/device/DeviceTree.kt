package io.scriptor.impl.device

import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import java.io.PrintStream

class DeviceTree : IODevice {

    override val machine: Machine

    override val begin: ULong
        get() = memory.begin
    override val end: ULong
        get() = memory.end

    val memory: Memory

    constructor(machine: Machine, begin: ULong) {
        this.machine = machine

        this.memory = Memory(machine, begin, 0x2000U, true)
        machine.generateDeviceTree(this.memory.buffer())
    }

    override fun read(offset: UInt, size: UInt): ULong? {
        return memory.read(offset, size)
    }

    override fun write(offset: UInt, size: UInt, value: ULong): Boolean {
        return memory.write(offset, size, value)
    }

    override fun dump(out: PrintStream) {
        memory.dump(out)
    }

    override fun reset() {
        memory.reset()
        machine.generateDeviceTree(this.memory.buffer())
    }
}
