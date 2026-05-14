package io.scriptor.impl.v64

import io.scriptor.elf.SymbolTable
import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.FDT
import io.scriptor.fdt.TreeBuilder
import io.scriptor.impl.TrapException
import io.scriptor.impl.device.Memory
import io.scriptor.impl.device.UART
import io.scriptor.isa.Registry
import io.scriptor.machine.Device
import io.scriptor.machine.Hart
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Function
import java.util.function.IntConsumer
import java.util.function.Predicate
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.cast

class Machine64 : Machine {

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
        this.harts = Array(harts) { Hart64(this, it) }
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

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun generateDeviceTree(buffer: ByteBuffer) {
        val context = BuilderContext<Device>()

        TreeBuilder()
            .root {
                it
                    .name("")
                    .prop { it.name("#address-cells").data(0x02) }
                    .prop { it.name("#size-cells").data(0x02) }
                    .prop { it.name("compatible").data("rvm,riscv-virt") }
                    .prop { it.name("model").data("RVM") }
                    .node {
                        it
                            .name("chosen")
                            .prop(UART::class in this) {
                                it.name("stdout-path").data("/soc/${this[UART::class]}")
                            }
                            .node {
                                it
                                    .name("opensbi-domains")
                                    .prop { it.name("compatible").data("opensbi,domain,config") }
                                    .node {
                                        it
                                            .name("tmemory")
                                            .prop { it.name("phandle").data(-0x2) }
                                            .prop { it.name("compatible").data("opensbi,domain,memregion") }
                                            .prop { it.name("base").data(0x80000000L) }
                                            .prop { it.name("order").data(20) }
                                    }
                                    .node {
                                        it
                                            .name("umemory")
                                            .prop { it.name("phandle").data(-0x3) }
                                            .prop { it.name("compatible").data("opensbi,domain,memregion") }
                                            .prop { it.name("base").data(0x0L) }
                                            .prop { it.name("order").data(64) }
                                    }
                                    .node {
                                        it
                                            .name("tuart")
                                            .prop { it.name("phandle").data(-0x4) }
                                            .prop { it.name("compatible").data("opensbi,domain,memregion") }
                                            .prop { it.name("base").data(0x20000000L) }
                                            .prop { it.name("order").data(12) }
                                            .prop { it.name("mmio").data() }
                                            .prop(UART::class in this) {
                                                it.name("devices").data(context.get(this[UART::class]))
                                            }
                                    }
                                    .node {
                                        it
                                            .name("tdomain")
                                            .prop { it.name("phandle").data(-0x5) }
                                            .prop { it.name("compatible").data("opensbi,domain,instance") }
                                            .prop { it.name("possible-harts").data(context.get(harts[0])) }
                                            .prop { it.name("regions").data(-0x2, 0x3F, -0x4, 0x3F) }
                                            .prop { it.name("boot-hart").data(context.get(harts[0])) }
                                            .prop { it.name("next-arg1").data(0L) }
                                            .prop { it.name("next-addr").data(0x80000000L) }
                                            .prop { it.name("next-mode").data(1) }
                                            .prop { it.name("system-reset-allowed").data() }
                                            .prop { it.name("system-suspend-allowed").data() }
                                    }
                                    .node {
                                        it
                                            .name("udomain")
                                            .prop { pb -> pb.name("phandle").data(-0x6) }
                                            .prop { pb -> pb.name("compatible").data("opensbi,domain,instance") }
                                            .prop { pb -> pb.name("possible-harts").data(context.get(harts[0])) }
                                            .prop { pb -> pb.name("regions").data(-0x2, 0x00, -0x4, 0x00, -0x3, 0x3F) }
                                    }
                            }
                    }
                    .node {
                        it.name("cpus")
                            .prop { it.name("#address-cells").data(0x01) }
                            .prop { it.name("#size-cells").data(0x00) }
                            .prop { it.name("timebase-frequency").data(0x989680) }

                        for (hart in harts) {
                            it.node { builder ->
                                hart.build(
                                    context,
                                    builder.prop { it.name("opensbi-domain").data(-0x5) },
                                )
                            }
                        }
                    }
                    .node {
                        it
                            .name("soc")
                            .prop { it.name("#address-cells").data(0x02) }
                            .prop { it.name("#size-cells").data(0x02) }
                            .prop { it.name("compatible").data("simple-bus") }
                            .prop { it.name("ranges").data() }

                        for (device in devices) {
                            it.node { builder ->
                                device.build(context, builder)
                            }
                        }
                    }
            }
            .build { tree -> FDT.write(tree, buffer) }
    }
}
