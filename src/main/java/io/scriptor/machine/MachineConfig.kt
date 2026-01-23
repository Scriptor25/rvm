package io.scriptor.machine

import io.scriptor.impl.MachineImpl
import io.scriptor.isa.Registry
import io.scriptor.util.Log.format
import java.nio.ByteOrder
import java.util.function.Function

class MachineConfig {

    private var mode = 64U
    private var harts = 1U
    private var order: ByteOrder = ByteOrder.nativeOrder()
    private val devices: MutableList<Function<Machine, Device>> = ArrayList()

    fun mode(mode: UInt): MachineConfig {
        this.mode = mode
        return this
    }

    fun harts(harts: UInt): MachineConfig {
        this.harts = harts
        return this
    }

    fun order(order: ByteOrder): MachineConfig {
        this.order = order
        return this
    }

    fun device(generator: Function<Machine, Device>): MachineConfig {
        this.devices.add(generator)
        return this
    }

    fun configure(registry: Registry): Machine {
        return when (mode) {
            64U -> MachineImpl(registry, order, harts.toInt(), devices.toTypedArray())
            else -> throw NoSuchElementException(format("mode=%d", mode))
        }
    }
}
