package dev.scriptor.impl.v64

import dev.scriptor.elf.SymbolTable
import dev.scriptor.fdt.BuilderContext
import dev.scriptor.fdt.FDT
import dev.scriptor.fdt.tree
import dev.scriptor.impl.device.UART
import dev.scriptor.isa.Registry
import dev.scriptor.machine.Device
import dev.scriptor.machine.Hart
import dev.scriptor.machine.Machine
import dev.scriptor.util.checkDeviceOverlap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Function
import java.util.function.IntConsumer

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

    override var entryPoint: ULong = 0UL

    constructor(registry: Registry, order: ByteOrder, harts: Int, devices: Array<Function<Machine, Device>>) {
        this.registry = registry
        this.order = order
        this.harts = Array(harts) { Hart64(this, it) }
        this.devices = devices.map { it.apply(this) }.toTypedArray()

        checkDeviceOverlap(this.devices)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun generateDeviceTree(buffer: ByteBuffer) {
        val context = BuilderContext<Device>()

        val tree = tree {
            prop("#address-cells") { data(0x02) }
            prop("#size-cells") { data(0x02) }
            prop("compatible") { data("rvm,riscv-virt") }
            prop("model") { data("RVM") }
            node("chosen") {
                prop(UART::class in this@Machine64, "stdout-path") {
                    data("/soc/${this@Machine64[UART::class]}")
                }
                node("opensbi-domains") {
                    prop("compatible") { data("opensbi,domain,config") }
                    node("tmemory") {
                        prop("phandle") { data(-0x2) }
                        prop("compatible") { data("opensbi,domain,memregion") }
                        prop("base") { data(0x80000000L) }
                        prop("order") { data(20) }
                    }
                    node("umemory") {
                        prop("phandle") { data(-0x3) }
                        prop("compatible") { data("opensbi,domain,memregion") }
                        prop("base") { data(0x0L) }
                        prop("order") { data(64) }
                    }
                    node("tuart") {
                        prop("phandle") { data(-0x4) }
                        prop("compatible") { data("opensbi,domain,memregion") }
                        prop("base") { data(0x20000000L) }
                        prop("order") { data(12) }
                        prop("mmio") { data() }
                        prop(UART::class in this@Machine64, "devices") {
                            data(context.get(this@Machine64[UART::class]))
                        }
                    }
                    node("tdomain") {
                        prop("phandle") { data(-0x5) }
                        prop("compatible") { data("opensbi,domain,instance") }
                        prop("possible-harts") { data(context.get(harts[0])) }
                        prop("regions") { data(-0x2, 0x3F, -0x4, 0x3F) }
                        prop("boot-hart") { data(context.get(harts[0])) }
                        prop("next-arg1") { data(0L) }
                        prop("next-addr") { data(0x80000000L) }
                        prop("next-mode") { data(1) }
                        prop("system-reset-allowed") { data() }
                        prop("system-suspend-allowed") { data() }
                    }
                    node("udomain") {
                        prop("phandle") { data(-0x6) }
                        prop("compatible") { data("opensbi,domain,instance") }
                        prop("possible-harts") { data(context.get(harts[0])) }
                        prop("regions") { data(-0x2, 0x00, -0x4, 0x00, -0x3, 0x3F) }
                    }
                }
            }
            node("cpus") {
                prop("#address-cells") { data(0x01) }
                prop("#size-cells") { data(0x00) }
                prop("timebase-frequency") { data(0x989680) }

                for (hart in harts) {
                    node {
                        prop("opensbi-domain") { data(-0x5) }

                        hart.build(context, this)
                    }
                }
            }
            node("soc") {
                prop("#address-cells") { data(0x02) }
                prop("#size-cells") { data(0x02) }
                prop("compatible") { data("simple-bus") }
                prop("ranges") { data() }

                for (device in devices) {
                    node { device.build(context, this) }
                }
            }
        }

        FDT.write(tree, buffer)
    }
}
