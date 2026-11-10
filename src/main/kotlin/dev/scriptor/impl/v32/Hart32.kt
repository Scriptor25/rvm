package dev.scriptor.impl.v32

import dev.scriptor.fdt.BuilderContext
import dev.scriptor.fdt.NodeBuilder
import dev.scriptor.impl.MMU
import dev.scriptor.impl.TrapException
import dev.scriptor.isa.CSR
import dev.scriptor.isa.Instruction
import dev.scriptor.machine.Device
import dev.scriptor.machine.Hart
import java.io.PrintStream

class Hart32 : Hart {

    override val machine: Machine32

    override val id: Int
    override var pc: ULong = 0UL

    override val gprFile = GPRFile32(this)
    override val fprFile = FPRFile32(this)
    override val csrFile = CSRFile32(this)

    override val mmu = MMU(this)

    override val privilege
        get() = priv
    override val sleeping
        get() = wfi

    private var ppc = 0UL
    private var priv = CSR.CSR_M
    private var wfi = false

    @OptIn(ExperimentalUnsignedTypes::class)
    private val values = UIntArray(5)

    constructor(machine: Machine32, id: Int) {
        this.machine = machine
        this.id = id
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        TODO()
    }

    override fun dump(out: PrintStream) {
        TODO()
    }

    override fun reset() {
        gprFile.reset()
        fprFile.reset()
        csrFile.reset()

        pc = machine.entryPoint
        priv = CSR.CSR_M
        wfi = false
    }

    override fun step() {
        try {
            // TODO: interrupt()

            val instruction = fetch(pc, false)
            if (!machine.registry.contains(64U, instruction))
                unsupported(instruction.toHexString())

            val definition = machine.registry[64U, instruction]

            ppc = pc
            pc = execute(instruction, definition)
        } catch (e: TrapException) {
            TODO()
            // if (handle(e.trapCause, e.trapValue)) {
            //     if (e.id < 0) throw TrapException(id, e)
            //     throw e
            // }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun execute(instruction: UInt, definition: Instruction): ULong {
        if (wfi) {
            return pc
        }

        var next = pc + definition.ilen

        when (definition.mnemonic) {
            "c.mv" -> {
                definition.decode(instruction, values, "rd", "rs2")
                c_mv(values[0], values[1])
            }

            else -> throw TrapException(id, 0x02UL, instruction.toULong(), "unsupported instruction %s", definition)
        }

        return next
    }

    override fun wake() {
        TODO()
    }

    fun c_mv(rd: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getwu(rs2)
    }
}