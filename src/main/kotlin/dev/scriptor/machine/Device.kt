package dev.scriptor.machine

import dev.scriptor.fdt.BuilderContext
import dev.scriptor.fdt.NodeBuilder
import java.io.PrintStream

interface Device {

    val machine: Machine

    fun build(context: BuilderContext<Device>, builder: NodeBuilder);

    fun dump(out: PrintStream)

    fun reset() {}

    fun step() {}

    fun close() {}
}
