package io.scriptor.impl.v32

import io.scriptor.elf.SymbolTable
import io.scriptor.isa.Registry
import io.scriptor.machine.Device
import io.scriptor.machine.Hart
import io.scriptor.machine.Machine
import io.scriptor.util.checkDeviceOverlap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Function
import java.util.function.IntConsumer

class Machine32 : Machine {

    override val registry: Registry
    override val order: ByteOrder

    override val symbols = SymbolTable()
    override val harts: Array<Hart>

    override val devices: Array<Device>

    override var once = false
    override var active = false

    override var breakpointHandler: IntConsumer? = null
    override val locks: MutableMap<ULong, Any> = HashMap()

    constructor(registry: Registry, order: ByteOrder, harts: Int, devices: Array<Function<Machine, Device>>) {
        this.registry = registry
        this.order = order
        this.harts = Array(harts) { Hart32(this, it) }
        this.devices = devices.map { it.apply(this) }.toTypedArray()

        checkDeviceOverlap(this.devices)
    }

    override fun generateDeviceTree(buffer: ByteBuffer) {
    }
}
