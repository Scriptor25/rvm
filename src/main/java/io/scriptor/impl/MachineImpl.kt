package io.scriptor.impl

import io.scriptor.elf.SymbolTable
import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.FDT
import io.scriptor.fdt.TreeBuilder
import io.scriptor.impl.device.Memory
import io.scriptor.impl.device.UART
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

class MachineImpl : Machine {

    override val machine: Machine
        get() = this

    override val order: ByteOrder
    override val symbols = SymbolTable()
    override val harts: Array<Hart>

    private val devices: Array<Device>
    private val dt: Memory

    private var once = false
    private var active = false

    private var breakpointHandler: IntConsumer? = null
    private val locks: MutableMap<ULong, Any> = HashMap()

    constructor(hartCount: UInt, order: ByteOrder, devices: Array<Function<Machine, Device>>) {
        this.order = order
        this.harts = Array(hartCount.toInt()) { HartImpl(this, it) }
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

        this.dt = Memory(this, 0x100000000UL, 0x2000U, true)
        generateDeviceTree(this.dt.buffer())
    }

    override fun <T : Device> device(type: Class<T>): T {
        for (device in devices) {
            if (type.isInstance(device)) {
                return type.cast(device)
            }
        }
        throw NoSuchElementException()
    }

    override fun <T : Device> device(type: Class<T>, index: Int): T {
        var index = index
        for (device in devices) {
            if (type.isInstance(device)
                && index-- <= 0
            ) {
                return type.cast(device)
            }
        }
        throw NoSuchElementException()
    }

    override fun <T : Device> device(type: Class<T>, predicate: Predicate<T>) {
        for (device in devices) {
            if (type.isInstance(device)
                && predicate.test(type.cast(device))
            ) {
                return
            }
        }
    }

    override fun <T : IODevice> device(type: Class<T>, address: ULong): T? {
        for (device in devices) {
            if (device is IODevice
                && address in device.begin..<device.end
                && type.isInstance(device)
            ) {
                return type.cast(device)
            }
        }
        return null
    }

    override fun <T : IODevice> device(
        type: Class<T>,
        address: ULong,
        capacity: UInt,
    ): T? {
        for (device in devices) {
            if (device is IODevice
                && address in device.begin..<device.end
                && capacity <= (device.end - device.begin).toUInt()
                && type.isInstance(device)
            ) {
                return type.cast(device)
            }
        }
        return null
    }

    override fun dump(out: PrintStream) {
        for (device in devices)
            device.dump(out)

        for (hart in harts)
            hart.dump(out)

        dump(out, 0x80007000UL, 0x1000UL)
    }

    override fun dump(out: PrintStream, paddr: ULong, length: ULong) {
        if (length == 0UL) {
            out.println("<empty>")
            return
        }

        val CHUNK = 0x20

        var allZero = false
        var allZeroBegin = 0UL

        var i = 0UL
        while (i < length) {
            val chunk = min(length - i, CHUNK.toULong()).toInt()
            if (chunk <= 0) break

            val buffer = ByteArray(chunk)
            pDirect(buffer, paddr + i, false)

            var allZeroP = true
            for (j in 0..<chunk)
                if (buffer[j].toInt() != 0) {
                    allZeroP = false
                    break
                }

            if (allZero && !allZeroP) {
                allZero = false
                out.println(format("%016x - %016x", allZeroBegin, i - 1U))
            } else if (!allZero && allZeroP) {
                allZero = true
                allZeroBegin = i
                i += CHUNK.toULong()
                continue
            } else if (allZero) {
                i += CHUNK.toULong()
                continue
            }

            out.print(format("%016x |", paddr + i))

            for (j in 0..<chunk) out.print(format(" %02x", buffer[j]))
            for (j in chunk..<CHUNK) out.print(" 00")

            out.print(" | ")

            for (j in 0..<chunk) out.print(if (buffer[j] >= 0x20) Char(buffer[j].toUShort()) else '.')
            for (j in chunk..<CHUNK) out.print('.')

            out.println()
            i += CHUNK.toULong()
        }
        out.println("(END)")
    }

    override fun reset() {
        once = false
        active = false

        for (device in devices) {
            device.reset()
        }

        for (hart in harts) {
            hart.reset()
            hart.gprFile[0x0AU] = hart.id.toUInt() // boot hart id
            hart.gprFile[0x0BU] = dt.begin       // device tree address
        }
    }

    override fun step() {
        if (!active)
            return

        for (device in devices)
            device.step()

        for (hart in harts)
            hart.step()

        if (once) {
            active = false
            handleBreakpoint(-1)
        }
    }

    override fun spinOnce() {
        once = true
        active = true
    }

    override fun spin() {
        once = false
        active = true
    }

    override fun pause() {
        once = false
        active = false
    }

    override fun setBreakpointHandler(handler: IntConsumer) {
        breakpointHandler = handler
    }

    override fun handleBreakpoint(id: Int): Boolean {
        if (breakpointHandler != null) {
            breakpointHandler!!.accept(id)
            return true
        }
        return false
    }

    override fun acquireLock(address: ULong): Any {
        if (address in locks)
            return locks[address]!!

        val lock = Any()
        locks[address] = lock
        return lock
    }

    override fun pRead(paddr: ULong, size: UInt, unsafe: Boolean): ULong {
        if (dt.begin <= paddr && paddr + size <= dt.end)
            return dt.read((paddr - dt.begin).toUInt(), size)

        for (device in devices)
            if (device is IODevice)
                if (device.begin <= paddr && paddr + size <= device.end)
                    return device.read((paddr - device.begin).toUInt(), size)

        if (unsafe) {
            Log.warn("read invalid address: address=%x, size=%d", paddr, size)
            return 0UL
        }

        throw TrapException(-1, 0x05UL, paddr, "read invalid address: address=%x, size=%d", paddr, size)
    }

    override fun pWrite(paddr: ULong, size: UInt, value: ULong, unsafe: Boolean) {
        if (dt.begin <= paddr && paddr + size <= dt.end) {
            dt.write((paddr - dt.begin).toUInt(), size, value)
            return
        }

        for (device in devices)
            if (device is IODevice)
                if (device.begin <= paddr && paddr + size <= device.end) {
                    device.write((paddr - device.begin).toUInt(), size, value)
                    return
                }

        if (unsafe) {
            Log.warn("write invalid address: address=%x, size=%d, value=%x", paddr, size, value)
            return
        }

        throw TrapException(
            -1,
            0x07UL,
            paddr,
            "write invalid address: address=%x, size=%d, value=%x",
            paddr,
            size,
            value,
        )
    }

    override fun pDirect(data: ByteArray, paddr: ULong, write: Boolean) {
        if (dt.begin <= paddr && paddr + data.size.toUInt() <= dt.end) {
            dt.direct(data, (paddr - dt.begin).toUInt(), write)
            return
        }

        for (device in devices)
            if (device is Memory)
                if (device.begin <= paddr && paddr + data.size.toUInt() <= device.end) {
                    device.direct(data, (paddr - device.begin).toUInt(), write)
                    return
                }

        Log.warn("direct read/write invalid address: address=%x, length=%d", paddr, data.size)
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
                            .prop { it.name("stdout-path").data("/soc/${this[UART::class.java]}") }
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
                                            .prop { it.name("devices").data(context.get(this[UART::class.java])) }
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

    override fun close() {
        for (hart in harts) hart.close()
        for (device in devices) device.close()
    }
}
