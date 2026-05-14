package io.scriptor.impl.v32

import io.scriptor.elf.SymbolTable
import io.scriptor.impl.device.Memory
import io.scriptor.isa.Registry
import io.scriptor.machine.Device
import io.scriptor.machine.Hart
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import io.scriptor.util.Log
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Function
import java.util.function.IntConsumer
import java.util.function.Predicate
import kotlin.reflect.KClass

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

        for (j in this.devices.indices) {
            val b = this.devices[j]
            if (b is IODevice) for (i in j + 1..<this.devices.size) {
                val a = this.devices[i]
                if (a is IODevice) {
                    if (a.begin < b.end && b.begin < a.end) {
                        Log.warn(
                            "device map overlap: %s [%08x;%08x] and %s [%08x;%08x]",
                            b,
                            b.begin,
                            b.end,
                            a,
                            a.begin,
                            a.end,
                        )
                    }
                }
            }
        }
    }

    override fun generateDeviceTree(buffer: ByteBuffer) {
    }
}
