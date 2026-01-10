package io.scriptor.impl.device

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import io.scriptor.util.Log.error
import io.scriptor.util.Log.format
import java.io.PrintStream

@OptIn(ExperimentalUnsignedTypes::class)
class CLINT : IODevice {

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    private var mtime: ULong = 0UL

    private val mtimecmp: ULongArray

    /**
     * machine-level external interrupt pending bit
     */
    private val meip: BooleanArray

    /**
     * machine-level software interrupt pending bit
     */
    private val msip: BooleanArray

    constructor(machine: Machine, begin: ULong) {
        this.machine = machine
        this.begin = begin
        this.end = begin + 0x10000UL

        this.mtimecmp = ULongArray(machine.harts.size)
        this.meip = BooleanArray(machine.harts.size)
        this.msip = BooleanArray(machine.harts.size)
    }

    override fun dump(out: PrintStream) {
        out.printf("clint: mtime=%x%n", mtime)
        for (id in 0..<machine.harts.size) out.printf(
            "#%-2d | mtimecmp=%x meip=%b msip=%b%n",
            id,
            mtimecmp[id],
            meip[id],
            msip[id],
        )
    }

    override fun reset() {
        mtime = 0UL
        mtimecmp.fill(0UL.inv())
        meip.fill(false)
        msip.fill(false)
    }

    override fun step() {
        ++mtime
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        val phandle = context.get(this)

        val ie = UIntArray(4 * machine.harts.size)
        for (id in 0..<machine.harts.size) {
            val cpu = context.get(machine.harts[id])
            ie[id * 4] = cpu
            ie[id * 4 + 1] = 0x03U
            ie[id * 4 + 2] = cpu
            ie[id * 4 + 3] = 0x07U
        }

        builder
            .name(format("clint@%x", begin))
            .prop { it.name("phandle").data(phandle) }
            .prop { it.name("compatible").data("riscv,clint0") }
            .prop { it.name("reg").data(begin, end - begin) }
            .prop { it.name("interrupts-extended").data(*ie) }
    }

    override fun read(offset: UInt, size: UInt): ULong {
        if (offset in MSIP_BASE..<MTIMECMP_BASE && size == 4u) {
            val hart = ((offset - MSIP_BASE) / MSIP_STRIDE).toInt()
            if (hart >= machine.harts.size) return 0U
            return if (msip[hart]) 1U else 0U
        }

        if (offset in MTIMECMP_BASE..<CONTEXT_BASE && size == 8u) {
            val hart = ((offset - MTIMECMP_BASE) / MTIMECMP_STRIDE).toInt()
            if (hart >= machine.harts.size) return 0U
            return mtimecmp[hart]
        }

        if (offset == MTIME_OFFSET && size == 8u) {
            return mtime
        }

        error("invalid clint read offset=%x, size=%d", offset, size)
        return 0U
    }

    override fun write(offset: UInt, size: UInt, value: ULong) {
        if (offset in MSIP_BASE..<MTIMECMP_BASE && size == 4u) {
            val hart = ((offset - MSIP_BASE) / MSIP_STRIDE).toInt()
            if (hart >= machine.harts.size) return
            msip[hart] = (value != 0UL)
            return
        }

        if (offset in MTIMECMP_BASE..<CONTEXT_BASE && size == 8u) {
            val hart = ((offset - MTIMECMP_BASE) / MTIMECMP_STRIDE).toInt()
            if (hart >= machine.harts.size) return
            mtimecmp[hart] = value
            return
        }

        error("invalid clint write offset=%x, size=%d, value=%x", offset, size, value)
    }

    override fun toString(): String {
        return format("clint@%x", begin)
    }

    fun mtime(): ULong {
        return mtime
    }

    fun mtimecmp(id: Int): ULong {
        return mtimecmp[id]
    }

    fun mtimecmp(id: Int, value: ULong) {
        mtimecmp[id] = value
    }

    fun meip(id: Int): Boolean {
        return meip[id]
    }

    fun mtip(id: Int): Boolean {
        return mtime >= mtimecmp[id]
    }

    fun msip(id: Int): Boolean {
        return msip[id]
    }

    fun msip(id: Int, value: Boolean) {
        msip[id] = value
    }

    companion object {
        private const val MSIP_BASE = 0x0000U
        private const val MSIP_STRIDE = 4u
        private const val MTIMECMP_BASE = 0x4000U
        private const val MTIMECMP_STRIDE = 8u
        private const val CONTEXT_BASE = 0xB000U
        private const val MTIME_OFFSET = 0xBFF8u
    }
}
