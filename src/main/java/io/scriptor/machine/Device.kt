package io.scriptor.machine

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import java.io.PrintStream

interface Device {

    val machine: Machine

    fun dump(out: PrintStream)

    fun reset()

    fun step() {
    }

    fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
    }

    fun close() {
    }
}
