package io.scriptor.machine

import io.scriptor.impl.MachineImpl
import io.scriptor.util.Log.format
import java.nio.ByteOrder
import java.util.function.Function

class MachineConfig {

    private var mode = 64U
    private var hartCount = 1U
    private var order: ByteOrder = ByteOrder.nativeOrder()
    private val devices: MutableList<Function<Machine, Device>> = ArrayList()

    fun mode(mode: UInt): MachineConfig {
        this.mode = mode
        return this
    }

    fun harts(hartCount: UInt): MachineConfig {
        this.hartCount = hartCount
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

    fun configure(): Machine {
        return when (mode) {
            64U -> MachineImpl(hartCount, order, devices.toTypedArray())
            else -> throw NoSuchElementException(format("mode=%d", mode))
        }
    }
}
