package io.scriptor.impl.device

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.PrintStream

@OptIn(ExperimentalUnsignedTypes::class)
class PLIC : IODevice {

    internal class Context {
        val enable: UIntArray = UIntArray(0x20)
        var threshold: UInt = 0U
        var claim: UInt = 0U
    }

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    private val ndev: UInt

    private val priority: UIntArray = UIntArray(SOURCE_COUNT)
    private val pending: UIntArray = UIntArray(SOURCE_COUNT ushr 2)

    private val contexts: Array<Context> = Array(CONTEXT_COUNT) { Context() }

    constructor(machine: Machine, begin: ULong, ndev: UInt) {
        this.machine = machine
        this.begin = begin
        this.ndev = ndev

        this.end = begin + 0x4000000UL
    }

    override fun dump(out: PrintStream) {
    }

    override fun reset() {
        priority.fill(0U)
        pending.fill(0U)

        for (context in contexts) {
            context.enable.fill(0U)
            context.threshold = 0U
            context.claim = 0U
        }
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        val phandle = context.get(this)

        val ie = UIntArray(2 * machine.harts.size)
        for (id in 0..<machine.harts.size) {
            val cpu = context.get(machine.harts[id])
            ie[id * 2] = cpu
            ie[id * 2 + 1] = 0x0BU
        }

        builder
            .name(format("plic@%x", begin))
            .prop { it.name("phandle").data(phandle) }
            .prop { it.name("compatible").data("riscv,plic0") }
            .prop { it.name("reg").data(begin, end - begin) }
            .prop { it.name("interrupt-controller").data() }
            .prop { it.name("#interrupt-cells").data(0x01U) }
            .prop { it.name("riscv,ndev").data(ndev) }
            .prop { it.name("interrupts-extended").data(*ie) }
    }

    override fun read(offset: UInt, size: UInt): ULong {
        if (offset in PRIORITY_BASE..<PENDING_BASE && size == 4U) {
            val index = (offset - PRIORITY_BASE) / 4U

            if (index < priority.size.toUInt())
                return priority[index.toInt()].toULong()
        }

        if (offset in PENDING_BASE..<ENABLE_BASE && size == 4U) {
            val index = (offset - PENDING_BASE) / 4U

            if (index < pending.size.toUInt())
                return pending[index.toInt()].toULong()
        }

        if (offset in ENABLE_BASE..<CONTEXT_BASE && size == 4U) {
            val base = offset - ENABLE_BASE
            val context = base / ENABLE_STRIDE
            val index = (base - context * ENABLE_STRIDE) / 4U

            if (context < contexts.size.toUInt() && index >= 0U && index < contexts[context.toInt()].enable.size.toUInt())
                return contexts[context.toInt()].enable[index.toInt()].toULong()
        }

        if (offset >= CONTEXT_BASE && size == 4U) {
            val base = offset - CONTEXT_BASE
            val context = base / CONTEXT_STRIDE
            val index = base - context * CONTEXT_STRIDE

            if (context < contexts.size.toUInt()) when (index) {
                CONTEXT_OFFSET_THRESHOLD -> {
                    return contexts[context.toInt()].threshold.toULong()
                }

                CONTEXT_OFFSET_CLAIM -> {
                    return contexts[context.toInt()].claim.toULong()
                }

                CONTEXT_OFFSET_RESERVED -> {
                    return 0UL
                }
            }
        }

        Log.error("invalid plic read offset=%x, size=%d", offset, size)
        return 0UL
    }

    override fun write(offset: UInt, size: UInt, value: ULong) {
        if (offset in PRIORITY_BASE..<PENDING_BASE && size == 4U) {
            val index = (offset - PRIORITY_BASE) / 4U

            if (index < priority.size.toUInt()) {
                priority[index.toInt()] = value.toUInt()
                return
            }
        }

        if (offset in ENABLE_BASE..<CONTEXT_BASE && size == 4U) {
            val base = offset - ENABLE_BASE
            val context = base / ENABLE_STRIDE
            val index = (base - context * ENABLE_STRIDE) / 4U

            if (context < contexts.size.toUInt() && index >= 0U && index < contexts[context.toInt()].enable.size.toUInt()) {
                contexts[context.toInt()].enable[index.toInt()] = value.toUInt()
                return
            }
        }

        if (offset >= CONTEXT_BASE && size == 4U) {
            val base = offset - CONTEXT_BASE
            val context = base / CONTEXT_STRIDE
            val index = base - context * CONTEXT_STRIDE

            if (context < contexts.size.toUInt()) {
                when (index) {
                    CONTEXT_OFFSET_THRESHOLD -> {
                        contexts[context.toInt()].threshold = value.toUInt()
                        return
                    }

                    CONTEXT_OFFSET_CLAIM -> {
                        contexts[context.toInt()].claim = value.toUInt()
                        return
                    }

                    CONTEXT_OFFSET_RESERVED -> {
                        return
                    }
                }
            }
        }

        Log.error("invalid plic write offset=%x, size=%d, value=%x", offset, size, value)
    }

    override fun toString(): String {
        return format("plic@%x", begin)
    }

    companion object {
        const val SOURCE_COUNT: Int = 0x400
        const val CONTEXT_COUNT: Int = 0x3E00

        const val PRIORITY_BASE: UInt = 0x0000U
        const val PENDING_BASE: UInt = 0x1000U
        const val ENABLE_BASE: UInt = 0x2000U
        const val ENABLE_STRIDE: UInt = 0x100U
        const val CONTEXT_BASE: UInt = 0x200000U
        const val CONTEXT_STRIDE: UInt = 0x1000U

        const val CONTEXT_OFFSET_THRESHOLD: UInt = 0x0U
        const val CONTEXT_OFFSET_CLAIM: UInt = 0x4U
        const val CONTEXT_OFFSET_RESERVED: UInt = 0x8U
    }
}
