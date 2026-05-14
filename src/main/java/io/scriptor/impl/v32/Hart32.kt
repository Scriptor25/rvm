package io.scriptor.impl.v32

import io.scriptor.impl.MMU
import io.scriptor.impl.device.CLINT
import io.scriptor.isa.CSR
import io.scriptor.isa.Instruction
import io.scriptor.machine.Hart
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

    private var priv = CSR.CSR_M
    private var wfi = false

    constructor(machine: Machine32, id: Int) {
        this.machine = machine
        this.id = id
    }

    override fun execute(instruction: UInt, definition: Instruction): ULong {
        TODO("Not yet implemented")
    }

    override fun wake() {
        TODO("Not yet implemented")
    }

    override fun translate(
        vAddress: ULong,
        access: MMU.Access,
        unsafe: Boolean
    ): ULong {
        TODO("Not yet implemented")
    }

    override fun dump(out: PrintStream) {
        TODO("Not yet implemented")
    }

    override fun reset() {
        gprFile.reset()
        fprFile.reset()
        csrFile.reset()

        pc = 0UL
        priv = CSR.CSR_M
        wfi = false
    }
}