package io.scriptor.impl

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.impl.device.CLINT
import io.scriptor.isa.CSR
import io.scriptor.isa.Instruction
import io.scriptor.isa.Registry
import io.scriptor.machine.*
import io.scriptor.util.ByteUtil.signExtend
import io.scriptor.util.ByteUtil.signExtendLong
import io.scriptor.util.Log
import io.scriptor.util.Log.error
import io.scriptor.util.Log.format
import io.scriptor.util.Log.info
import java.io.IOException
import java.io.PrintStream

class HartImpl : Hart {

    override val machine: Machine

    override val id: Int
    override var pc = 0UL

    override val gprFile: GPRFile = GPRFileImpl(this)
    override val fprFile: FPRFile = FPRFileImpl(this)
    override val csrFile: CSRFile = CSRFileImpl(this)

    private val mmu: MMU = MMU(this)

    private var ppc = 0UL
    private var priv = CSR.CSR_M
    private var wfi = false
    private var semihosting = false

    @OptIn(ExperimentalUnsignedTypes::class)
    private val values = UIntArray(5)

    constructor(machine: Machine, id: Int) {
        this.machine = machine
        this.id = id
    }

    private fun printStackTrace(out: PrintStream) {
        var ra = gprFile.getdu(0x1U)
        var fp = gprFile.getdu(0x8U)

        out.println(format("stack trace (pc=%016x, ra=%016x, fp=%016x):", pc, ra, fp))

        run {
            val symbol = machine.symbols.resolve(pc)
            out.println(format(" %016x : %s", pc, symbol))
        }

        while (fp != 0UL) {
            val symbol = machine.symbols.resolve(ra)
            out.println(format(" %016x : %s", ra, symbol))

            val prev_fp = read(fp, 8U, true)
            val prev_ra = read(fp + 8UL, 8U, true)

            fp = prev_fp
            ra = prev_ra
        }
    }

    override fun dump(out: PrintStream) {
        val instruction = fetch(pc, true)
        out.println(format("pc=%016x, instruction=%08x", pc, instruction))

        if (Registry.contains(64U, instruction)) {
            val definition = Registry[64U, instruction]

            val display = StringBuilder()
            display.append(definition.mnemonic)
            for (operand in definition.operands.values) {
                display.append(", ")
                    .append(operand.label)
                    .append('=')
                    .append(operand.decode(instruction.toInt()).toHexString())
            }

            out.println(format("  %s", display))
        }

        out.println("gpr file:")
        gprFile.dump(out)

        out.println("fpr file:")
        fprFile.dump(out)

        out.println("csr file:")
        csrFile.dump(out)

        out.println(
            format(
                "mstatus=%x, mtvec=%x, mcause=%x, mepc=%x",
                csrFile[CSR.mstatus, CSR.CSR_M],
                csrFile[CSR.mtvec, CSR.CSR_M],
                csrFile[CSR.mcause, CSR.CSR_M],
                csrFile[CSR.mepc, CSR.CSR_M],
            ),
        )

        val sp = gprFile.getdu(0x2U)
        out.println(format("stack (sp=%016x):", sp))

        var offset = -0x10L
        while (offset <= 0x10L) {
            val vaddr = (sp.toLong() + offset).toULong()
            val value = read(vaddr, 8U, true)

            out.println(format("%016x : %016x", vaddr, value))
            offset += 0x8
        }

        printStackTrace(out)
    }

    override fun reset() {
        val clint = machine[CLINT::class.java]

        gprFile.reset()
        fprFile.reset()
        csrFile.reset()

        pc = 0UL
        priv = CSR.CSR_M
        wfi = false
        semihosting = false

        // machine isa
        csrFile.defineVal(
            CSR.misa,
            1UL shl 63 // mxl = 64
                    or (1UL shl 20 // 'U' - user mode implemented
                    ) or (1UL shl 18 // 'S' - supervisor mode implemented
                    ) or (1UL shl 12 // 'M' - integer multiply/divide extension
                    ) or (1UL shl 8 // 'I' - base isa
                    ) or (1UL shl 5 // 'F' - single-precision floating-point extension
                    ) or (1UL shl 3 // 'D' - double-precision floating-point extension
                    ) or (1UL shl 2 // 'C' - compressed extension
                    ) or 1UL, // 'A' - atomic extension
        )

        // machine identification
        csrFile.defineVal(CSR.mvendorid, 0xCAFEBABEUL)
        csrFile.defineVal(CSR.marchid, 0x1UL)
        csrFile.defineVal(CSR.mimpid, 0x1UL)
        csrFile.defineVal(CSR.mhartid, id.toULong())

        // machine status/control
        csrFile.define(
            CSR.mstatus,
            0b10000000_00000000_00000110_11111111_00000001_11111111_11111111_11101010UL,
            -1,
            0b00000000_00000000_00000000_00000000_00000000_00000000_01111000_00000000UL,
        )
        csrFile.define(CSR.sstatus, 0x84UL, CSR.mstatus)
        csrFile.define(CSR.medeleg, 0xFFFFUL)
        csrFile.define(CSR.mideleg, 0xFFFFUL)

        // machine interrupt control
        csrFile.define(CSR.mie, 0x2AAAUL)
        csrFile.define(
            CSR.mip, 0x2AAAUL,
            {
                ((if (clint.meip(id)) 1UL else 0UL) shl 11 or ((if (clint.mtip(id)) 1UL else 0UL) shl 7)
                        or ((if (clint.msip(id)) 1UL else 0UL) shl 3))
            },
            { value -> clint.msip(id, ((value shr 3) and 1UL) != 0UL) },
        )
        csrFile.define(CSR.sie, 0x2222UL, CSR.mie)
        csrFile.define(CSR.sip, 0x2222UL, CSR.mip)

        // machine trap handling
        csrFile.define(CSR.mtvec)
        csrFile.define(CSR.stvec)
        csrFile.define(CSR.mepc)
        csrFile.define(CSR.sepc)
        csrFile.define(CSR.mcause, 0x7FFFFFFFFFFFFFFFUL)
        csrFile.define(CSR.scause, 0x7FFFFFFFFFFFFFFFUL)
        csrFile.define(CSR.mtval)
        csrFile.define(CSR.stval)
        csrFile.define(CSR.mscratch)

        // machine virtual memory
        csrFile.define(CSR.satp)

        // machine counters/timers
        csrFile.define(CSR.time)
        csrFile.define(CSR.cycle)
        csrFile.define(CSR.instret)

        // physical memory protection
        for (pmpcfgx in CSR.pmpcfg0..CSR.pmpcfg15) {
            csrFile.define(pmpcfgx, 0xFFUL)
        }

        for (pmpaddrx in CSR.pmpaddr0..CSR.pmpaddr63) {
            csrFile.define(pmpaddrx)
        }

        // supervisor exception/interrupt delegation
        csrFile.define(CSR.sedeleg)
        csrFile.define(CSR.sideleg)
        csrFile.define(CSR.sscratch)

        // user status / floating-point
        csrFile.define(CSR.fflags, 0x1FUL)
        csrFile.define(CSR.frm, 0x7UL)
        csrFile.define(CSR.fcsr, 0xFFUL)

        // debug registers
        csrFile.define(CSR.dcsr)
        csrFile.define(CSR.dpc)
        csrFile.define(CSR.dscratch0)
        csrFile.define(CSR.dscratch1)

        // machine hardware performance counters
        for (mhpmcounterx in CSR.mhpmcounter3..CSR.mhpmcounter31) {
            csrFile.define(mhpmcounterx)
        }

        for (mhpmeventx in CSR.mhpmevent3..CSR.mhpmevent31) {
            csrFile.define(mhpmeventx)
        }

        // clint control/status
        csrFile.define(CSR.time, 0UL.inv()) { clint.mtime() }
        csrFile.define(CSR.stimecmp, 0UL.inv(), { clint.mtimecmp(id) }, { clint.mtimecmp(id, it) })

        csrFile.define(CSR.mcounteren, 0xFFFFFFFFUL, -1, 0xFFFFFFFFUL)
        csrFile.define(CSR.mcountinhibit, 0xFFFFFFFFUL, -1, 0UL)
        csrFile.define(CSR.mcyclecfg)

        csrFile.define(CSR.scounteren, 0xFFFFFFFFUL, -1, 0xFFFFFFFFUL)
        csrFile.define(CSR.scountinhibit, 0xFFFFFFFFUL, -1, 0UL)
        csrFile.define(CSR.scountovf, 0xFFFFFFFFUL, -1, 0UL)

        csrFile.define(CSR.menvcfg)
        csrFile.define(CSR.senvcfg)

        csrFile.define(CSR.tselect)

        csrFile.define(CSR.mstateen0)
        csrFile.define(CSR.mstateen1)
        csrFile.define(CSR.mstateen2)
        csrFile.define(CSR.mstateen3)

        csrFile.define(CSR.sstateen0)
        csrFile.define(CSR.sstateen1)
        csrFile.define(CSR.sstateen2)
        csrFile.define(CSR.sstateen3)
    }

    override fun step() {
        try {
            interrupt()

            val instruction = fetch(pc, false)
            if (!Registry.contains(64U, instruction))
                unsupported(instruction.toHexString())

            val definition = Registry[64U, instruction]

            ppc = pc
            pc = execute(instruction, definition)
        } catch (e: TrapException) {
            if (handle(e.trapCause, e.trapValue)) {
                if (e.id < 0) throw TrapException(id, e)
                throw e
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        val phandle = context.get(this)

        builder
            .name(format("cpu@%d", id))
            .prop { it.name("device_type").data("cpu") }
            .prop { it.name("reg").data(id) }
            .prop { it.name("status").data("okay") }
            .prop { it.name("compatible").data("riscv") }
            .prop { it.name("riscv,isa").data("rv64imafdqc_zifencei_zicsr") }
            .prop { it.name("riscv,mmu-type").data("riscv,sv39,sv48,sv57") }
            .node {
                it
                    .name("interrupt-controller")
                    .prop { it.name("phandle").data(phandle) }
                    .prop { it.name("#interrupt-cells").data(0x01) }
                    .prop { it.name("compatible").data("riscv,cpu-intc") }
                    .node { it.name("interrupt-controller") }
            }
    }

    private fun handle(cause: ULong, tval: ULong): Boolean {
        val interrupt = (cause and (1UL shl 63)) != 0UL

        val delegate = if (interrupt)
            ((csrFile[CSR.mideleg, CSR.CSR_M] and (1UL shl cause.toInt())) != 0UL)
        else
            ((csrFile[CSR.medeleg, CSR.CSR_M] and (1UL shl cause.toInt())) != 0UL)

        val target = if (priv < CSR.CSR_M && delegate) CSR.CSR_S else CSR.CSR_M

        var status = csrFile[CSR.mstatus, CSR.CSR_M]
        if (target == CSR.CSR_M) {
            csrFile[CSR.mepc, CSR.CSR_M] = pc
            csrFile[CSR.mcause, CSR.CSR_M] = cause
            csrFile[CSR.mtval, CSR.CSR_M] = tval

            status = (status and (1UL shl 7).inv()) or (((status shr 3) and 1UL) shl 7)
            status = status and (1UL shl 3).inv()
            status = (status and (3UL shl 11).inv()) or (priv.toULong() shl 11)
        } else {
            csrFile[CSR.sepc, CSR.CSR_S] = pc
            csrFile[CSR.scause, CSR.CSR_S] = cause
            csrFile[CSR.stval, CSR.CSR_S] = tval

            status = (status and (1UL shl 5).inv()) or (((status shr 1) and 1UL) shl 5)
            status = status and (1UL shl 1).inv()
            status = (status and (1UL shl 8).inv()) or (priv.toULong() shl 8)
        }
        csrFile[CSR.mstatus, CSR.CSR_M] = status

        val tvec = if (target == CSR.CSR_M)
            csrFile[CSR.mtvec, CSR.CSR_M]
        else
            csrFile[CSR.stvec, CSR.CSR_S]
        if (tvec == 0UL) {
            return true
        }

        val base = tvec and 3UL.inv()
        val mode = tvec and 3UL

        val pcp = if (interrupt && mode == 1UL) base + 4UL * cause else base

        info(
            "handle trap cause=%016x tval=%016x priv=%d satp=%016x mtvec=%016x stvec=%016x pc=%016x pc'=%016x",
            cause,
            tval,
            priv,
            csrFile[CSR.satp, CSR.CSR_M],
            csrFile[CSR.mtvec, CSR.CSR_M],
            csrFile[CSR.stvec, CSR.CSR_M],
            pc,
            pcp,
        )

        pc = pcp
        priv = target

        return false
    }

    private fun interrupt() {
        val status = csrFile[CSR.mstatus, CSR.CSR_M]

        when (priv) {
            CSR.CSR_M -> {
                if (((status shr 3) and 1UL) == 0UL) {
                    return
                }
            }

            CSR.CSR_S -> {
                if (((status shr 1) and 1UL) == 0UL) {
                    return
                }
            }

            else -> {
                return
            }
        }

        // check if any interrupts are pending and enabled
        val pending = csrFile[CSR.mip, CSR.CSR_M] and csrFile[CSR.mie, CSR.CSR_M]
        if (pending == 0UL) {
            return
        }

        val code = pending.countTrailingZeroBits().toULong()

        val delegate = priv < CSR.CSR_M && ((csrFile[CSR.mideleg, CSR.CSR_M] and (1UL shl code.toInt())) != 0UL)
        val target = if (delegate) CSR.CSR_S else CSR.CSR_M

        if (target < priv) return

        throw TrapException(id, (1UL shl 63) or code, 0UL, "")
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun execute(instruction: UInt, definition: Instruction): ULong {
        if (wfi) {
            return pc
        }

        var next = pc + definition.ilen

        when (definition.mnemonic) {
            "sret" -> next = sret()
            "mret" -> next = mret()
            "mnret" -> mnret()
            "wfi" -> wfi()
            "sctrclr" -> sctrclr()
            "sfence.vma" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                sfence_vma(values[0], values[1])
            }

            "hfence.vvma" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hfence_vvma(values[0], values[1])
            }

            "hfence.gvma" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hfence_gvma(values[0], values[1])
            }

            "hlv.b" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_b(values[0], values[1])
            }

            "hlv.bu" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_bu(values[0], values[1])
            }

            "hlv.h" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_h(values[0], values[1])
            }

            "hlv.hu" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_hu(values[0], values[1])
            }

            "hlv.w" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_w(values[0], values[1])
            }

            "hlvx.hu" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlvx_hu(values[0], values[1])
            }

            "hlvx.wu" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlvx_wu(values[0], values[1])
            }

            "hsv.b" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hsv_b(values[0], values[1])
            }

            "hsv.h" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hsv_h(values[0], values[1])
            }

            "hsv.w" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hsv_w(values[0], values[1])
            }

            "hlv.wu" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_wu(values[0], values[1])
            }

            "hlv.d" -> {
                definition.decode(instruction, values, "rd", "rs1")
                hlv_d(values[0], values[1])
            }

            "hsv.d" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hsv_d(values[0], values[1])
            }

            "sinval.vma" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                sinval_vma(values[0], values[1])
            }

            "sfence.w.inval" -> sfence_w_inval()
            "sfence.inval.ir" -> sfence_inval_ir()
            "hinval.vvma" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hinval_vvma(values[0], values[1])
            }

            "hinval.gvma" -> {
                definition.decode(instruction, values, "rs1", "rs2")
                hinval_gvma(values[0], values[1])
            }

            "lr.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                lr_w(values[0], values[1], values[2], values[3], values[4])
            }

            "sc.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                sc_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amoswap.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoswap_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amoadd.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoadd_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amoxor.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoxor_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amoand.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoand_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amoor.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoor_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amomin.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amomin_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amomax.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amomax_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amominu.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amominu_w(values[0], values[1], values[2], values[3], values[4])
            }

            "amomaxu.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amomaxu_w(values[0], values[1], values[2], values[3], values[4])
            }

            "flw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                flw(values[0], values[1], values[2])
            }

            "fsw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                fsw(values[0], values[1], values[2])
            }

            "fmadd.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm")
                fmadd_s(values[0], values[1], values[2], values[3], values[4])
            }

            "fmsub.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm")
                fmsub_s(values[0], values[1], values[2], values[3], values[4])
            }

            "fnmsub.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm")
                fnmsub_s(values[0], values[1], values[2], values[3], values[4])
            }

            "fnmadd.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm")
                fnmadd_s(values[0], values[1], values[2], values[3], values[4])
            }

            "fadd.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fadd_s(values[0], values[1], values[2], values[3])
            }

            "fsub.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fsub_s(values[0], values[1], values[2], values[3])
            }

            "fmul.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fmul_s(values[0], values[1], values[2], values[3])
            }

            "fdiv.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fdiv_s(values[0], values[1], values[2], values[3])
            }

            "fsqrt.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fsqrt_s(values[0], values[1], values[2], values[3])
            }

            "fsgnj.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fsgnj_s(values[0], values[1], values[2])
            }

            "fsgnjn.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fsgnjn_s(values[0], values[1], values[2])
            }

            "fsgnjx.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fsgnjx_s(values[0], values[1], values[2])
            }

            "fmin.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fmin_s(values[0], values[1], values[2])
            }

            "fmax.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fmax_s(values[0], values[1], values[2])
            }

            "fcvt.w.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_w_s(values[0], values[1], values[2], values[3])
            }

            "fcvt.wu.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_wu_s(values[0], values[1], values[2], values[3])
            }

            "fmv.x.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fmv_x_w(values[0], values[1], values[2])
            }

            "feq.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                feq_s(values[0], values[1], values[2])
            }

            "flt.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                flt_s(values[0], values[1], values[2])
            }

            "fge.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fge_s(values[0], values[1], values[2])
            }

            "fclass.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fclass_s(values[0], values[1], values[2])
            }

            "fcvt.s.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_s_w(values[0], values[1], values[2], values[3])
            }

            "fcvt.s.wu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_s_wu(values[0], values[1], values[2], values[3])
            }

            "fmv.w.x" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                fmv_w_x(values[0], values[1], values[2])
            }

            "lui" -> {
                definition.decode(instruction, values, "rd", "imm")
                lui(values[0], values[1])
            }

            "auipc" -> {
                definition.decode(instruction, values, "rd", "imm")
                auipc(values[0], values[1])
            }

            "jal" -> {
                definition.decode(instruction, values, "rd", "imm")
                next = jal(next, values[0], values[1])
            }

            "jalr" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                next = jalr(next, values[0], values[1], values[2])
            }

            "beq" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                next = beq(next, values[0], values[1], values[2])
            }

            "bne" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                next = bne(next, values[0], values[1], values[2])
            }

            "blt" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                next = blt(next, values[0], values[1], values[2])
            }

            "bge" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                next = bge(next, values[0], values[1], values[2])
            }

            "bltu" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                next = bltu(next, values[0], values[1], values[2])
            }

            "bgeu" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                next = bgeu(next, values[0], values[1], values[2])
            }

            "lb" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                lb(values[0], values[1], values[2])
            }

            "lh" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                lh(values[0], values[1], values[2])
            }

            "lw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                lw(values[0], values[1], values[2])
            }

            "lbu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                lbu(values[0], values[1], values[2])
            }

            "lhu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                lhu(values[0], values[1], values[2])
            }

            "sb" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                sb(values[0], values[1], values[2])
            }

            "sh" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                sh(values[0], values[1], values[2])
            }

            "sw" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                sw(values[0], values[1], values[2])
            }

            "addi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                addi(values[0], values[1], values[2])
            }

            "slti" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                slti(values[0], values[1], values[2])
            }

            "sltiu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                sltiu(values[0], values[1], values[2])
            }

            "xori" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                xori(values[0], values[1], values[2])
            }

            "ori" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                ori(values[0], values[1], values[2])
            }

            "andi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                andi(values[0], values[1], values[2])
            }

            "slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                slli(values[0], values[1], values[2])
            }

            "srli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                srli(values[0], values[1], values[2])
            }

            "srai" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                srai(values[0], values[1], values[2])
            }

            "add" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                add(values[0], values[1], values[2])
            }

            "sub" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                sub(values[0], values[1], values[2])
            }

            "sll" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                sll(values[0], values[1], values[2])
            }

            "slt" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                slt(values[0], values[1], values[2])
            }

            "sltu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                sltu(values[0], values[1], values[2])
            }

            "xor" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                xor(values[0], values[1], values[2])
            }

            "srl" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                srl(values[0], values[1], values[2])
            }

            "sra" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                sra(values[0], values[1], values[2])
            }

            "or" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                or(values[0], values[1], values[2])
            }

            "and" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                and(values[0], values[1], values[2])
            }

            "fence" -> {
                definition.decode(instruction, values, "rd", "rs1", "fm", "pred", "succ")
                fence(values[0], values[1], values[2], values[3], values[4])
            }

            "fence_tso" -> fence_tso()
            "pause" -> pause()
            "ecall" -> ecall()
            "ebreak" -> next = ebreak(next)
            "mul" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                mul(values[0], values[1], values[2])
            }

            "mulh" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                mulh(values[0], values[1], values[2])
            }

            "mulhsu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                mulhsu(values[0], values[1], values[2])
            }

            "mulhu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                mulhu(values[0], values[1], values[2])
            }

            "div" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                div(values[0], values[1], values[2])
            }

            "divu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                divu(values[0], values[1], values[2])
            }

            "rem" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                rem(values[0], values[1], values[2])
            }

            "remu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                remu(values[0], values[1], values[2])
            }

            "lr.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                lr_d(values[0], values[1], values[2], values[3], values[4])
            }

            "sc.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                sc_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amoswap.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoswap_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amoadd.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoadd_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amoxor.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoxor_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amoand.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoand_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amoor.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amoor_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amomin.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amomin_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amomax.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amomax_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amominu.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amominu_d(values[0], values[1], values[2], values[3], values[4])
            }

            "amomaxu.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl")
                amomaxu_d(values[0], values[1], values[2], values[3], values[4])
            }

            "fcvt.l.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_l_s(values[0], values[1], values[2], values[3])
            }

            "fcvt.lu.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_lu_s(values[0], values[1], values[2], values[3])
            }

            "fcvt.s.l" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_s_l(values[0], values[1], values[2], values[3])
            }

            "fcvt.s.lu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm")
                fcvt_s_lu(values[0], values[1], values[2], values[3])
            }

            "lwu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                lwu(values[0], values[1], values[2])
            }

            "ld" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                ld(values[0], values[1], values[2])
            }

            "sd" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm")
                sd(values[0], values[1], values[2])
            }

            "addiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                addiw(values[0], values[1], values[2])
            }

            "slliw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                slliw(values[0], values[1], values[2])
            }

            "srliw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                srliw(values[0], values[1], values[2])
            }

            "sraiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                sraiw(values[0], values[1], values[2])
            }

            "addw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                addw(values[0], values[1], values[2])
            }

            "subw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                subw(values[0], values[1], values[2])
            }

            "sllw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                sllw(values[0], values[1], values[2])
            }

            "srlw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                srlw(values[0], values[1], values[2])
            }

            "sraw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                sraw(values[0], values[1], values[2])
            }

            "mulw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                mulw(values[0], values[1], values[2])
            }

            "divw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                divw(values[0], values[1], values[2])
            }

            "divuw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                divuw(values[0], values[1], values[2])
            }

            "remw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                remw(values[0], values[1], values[2])
            }

            "remuw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                remuw(values[0], values[1], values[2])
            }

            "c.addi4spn" -> {
                definition.decode(instruction, values, "rdp", "uimm")
                c_addi4spn(values[0] + 0x8U, values[1])
            }

            "c.fld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm")
                c_fld(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.lw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm")
                c_lw(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.ld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm")
                c_ld(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.fsd" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm")
                c_fsd(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.sw" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm")
                c_sw(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.sd" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm")
                c_sd(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.nop" -> c_nop()
            "c.addi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                c_addi(values[0], values[1], values[2])
            }

            "c.addiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                c_addiw(values[0], values[1], values[2])
            }

            "c.li" -> {
                definition.decode(instruction, values, "rd", "imm")
                c_li(values[0], values[1])
            }

            "c.addi16sp" -> {
                definition.decode(instruction, values, "imm")
                c_addi16sp(values[0])
            }

            "c.lui" -> {
                definition.decode(instruction, values, "rd", "imm")
                c_lui(values[0], values[1])
            }

            "c.srli" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "shamt")
                c_srli(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.srai" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "shamt")
                c_srai(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.andi" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm")
                c_andi(values[0] + 0x8U, values[1] + 0x8U, values[2])
            }

            "c.sub" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p")
                c_sub(values[0] + 0x8U, values[1] + 0x8U, values[2] + 0x8U)
            }

            "c.xor" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p")
                c_xor(values[0] + 0x8U, values[1] + 0x8U, values[2] + 0x8U)
            }

            "c.or" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p")
                c_or(values[0] + 0x8U, values[1] + 0x8U, values[2] + 0x8U)
            }

            "c.and" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p")
                c_and(values[0] + 0x8U, values[1] + 0x8U, values[2] + 0x8U)
            }

            "c.subw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p")
                c_subw(values[0] + 0x8U, values[1] + 0x8U, values[2] + 0x8U)
            }

            "c.addw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p")
                c_addw(values[0] + 0x8U, values[1] + 0x8U, values[2] + 0x8U)
            }

            "c.j" -> {
                definition.decode(instruction, values, "imm")
                next = c_j(values[0])
            }

            "c.beqz" -> {
                definition.decode(instruction, values, "rs1p", "imm")
                next = c_beqz(next, values[0] + 0x8U, values[1])
            }

            "c.bnez" -> {
                definition.decode(instruction, values, "rs1p", "imm")
                next = c_bnez(next, values[0] + 0x8U, values[1])
            }

            "c.slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt")
                c_slli(values[0], values[1], values[2])
            }

            "c.fldsp" -> {
                definition.decode(instruction, values, "rd", "uimm")
                c_fldsp(values[0], values[1])
            }

            "c.lwsp" -> {
                definition.decode(instruction, values, "rd", "uimm")
                c_lwsp(values[0], values[1])
            }

            "c.ldsp" -> {
                definition.decode(instruction, values, "rd", "uimm")
                c_ldsp(values[0], values[1])
            }

            "c.jr" -> {
                definition.decode(instruction, values, "rs1")
                next = c_jr(values[0])
            }

            "c.mv" -> {
                definition.decode(instruction, values, "rd", "rs2")
                c_mv(values[0], values[1])
            }

            "c.ebreak" -> next = c_ebreak()
            "c.jalr" -> {
                definition.decode(instruction, values, "rs1")
                next = c_jalr(next, values[0])
            }

            "c.add" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2")
                c_add(values[0], values[1], values[2])
            }

            "c.fsdsp" -> {
                definition.decode(instruction, values, "rs2", "uimm")
                c_fsdsp(values[0], values[1])
            }

            "c.swsp" -> {
                definition.decode(instruction, values, "rs2", "uimm")
                c_swsp(values[0], values[1])
            }

            "c.sdsp" -> {
                definition.decode(instruction, values, "rs2", "uimm")
                c_sdsp(values[0], values[1])
            }

            "csrrw" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr")
                csrrw(values[0], values[1], values[2])
            }

            "csrrs" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr")
                csrrs(values[0], values[1], values[2])
            }

            "csrrc" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr")
                csrrc(values[0], values[1], values[2])
            }

            "csrrwi" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr")
                csrrwi(values[0], values[1], values[2])
            }

            "csrrsi" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr")
                csrrsi(values[0], values[1], values[2])
            }

            "csrrci" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr")
                csrrci(values[0], values[1], values[2])
            }

            "fence.i" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm")
                fence_i(values[0], values[1], values[2])
            }

            else -> throw TrapException(id, 0x02UL, instruction.toULong(), "unsupported instruction %s", definition)
        }

        return next
    }

    override fun sleeping(): Boolean {
        return wfi
    }

    override fun wake() {
        wfi = false
    }

    override fun privilege(): UInt {
        return priv
    }

    override fun translate(vaddr: ULong, access: MMU.Access, unsafe: Boolean): ULong {
        return mmu.translate(priv, vaddr, access, unsafe)
    }

    override fun close() {
        gprFile.close()
        fprFile.close()
        csrFile.close()
    }

    private fun unsupported(name: String) {
        throw TrapException(id, 0x02UL, 0x00UL, "unsupported instruction %s", name)
    }

    //region PRIVILEGED

    private fun sret(): ULong {
        var status = csrFile[CSR.sstatus, priv]

        val spp = ((status shr 8) and 1UL).toUInt()
        val spie = (status and (1UL shl 5)) != 0UL

        priv = spp

        status = status and (1UL shl 1).inv()
        status = status or (if (spie) (1UL shl 1) else 0UL)
        status = status or (1UL shl 8)
        status = status or (1UL shl 5)

        csrFile[CSR.sstatus, CSR.CSR_M] = status

        return csrFile[CSR.sepc, CSR.CSR_M]
    }

    private fun mret(): ULong {
        var status = csrFile[CSR.mstatus, priv]

        val mpp = ((status shr 11) and 3UL).toUInt()
        val mpie = (status and (1UL shl 7)) != 0UL

        priv = mpp

        status = status and (1UL shl 3).inv()
        status = status or (if (mpie) (1UL shl 3) else 0UL)
        status = status or (3UL shl 11)
        status = status or (1UL shl 7)

        csrFile[CSR.mstatus, CSR.CSR_M] = status

        return csrFile[CSR.mepc, CSR.CSR_M]
    }

    private fun mnret() {
        unsupported("mnret")
    }

    private fun wfi() {
        wfi = true
    }

    private fun sctrclr() {
        unsupported("sctrclr")
    }

    private fun sfence_vma(rs1: UInt, rs2: UInt) {
        val vaddr = gprFile.getdu(rs1)
        val asid = gprFile.getdu(rs2)

        mmu.flush(vaddr, asid)
    }

    private fun hfence_vvma(rs1: UInt, rs2: UInt) {
        unsupported("hfence.vvma")
    }

    private fun hfence_gvma(rs1: UInt, rs2: UInt) {
        unsupported("hfence.gvma")
    }

    private fun hlv_b(rd: UInt, rs1: UInt) {
        unsupported("hlv.b")
    }

    private fun hlv_bu(rd: UInt, rs1: UInt) {
        unsupported("hlv.bu")
    }

    private fun hlv_h(rd: UInt, rs1: UInt) {
        unsupported("hlv.h")
    }

    private fun hlv_hu(rd: UInt, rs1: UInt) {
        unsupported("hlv.hu")
    }

    private fun hlv_w(rd: UInt, rs1: UInt) {
        unsupported("hlv.w")
    }

    private fun hlvx_hu(rd: UInt, rs1: UInt) {
        unsupported("hlvx.hu")
    }

    private fun hlvx_wu(rd: UInt, rs1: UInt) {
        unsupported("hlvx.wu")
    }

    private fun hsv_b(rs1: UInt, rs2: UInt) {
        unsupported("hsv.b")
    }

    private fun hsv_h(rs1: UInt, rs2: UInt) {
        unsupported("hsv.h")
    }

    private fun hsv_w(rs1: UInt, rs2: UInt) {
        unsupported("hsv.w")
    }

    private fun hlv_wu(rd: UInt, rs1: UInt) {
        unsupported("hlv.wu")
    }

    private fun hlv_d(rd: UInt, rs1: UInt) {
        unsupported("hlv.d")
    }

    private fun hsv_d(rs1: UInt, rs2: UInt) {
        unsupported("hsv.d")
    }

    private fun sinval_vma(rs1: UInt, rs2: UInt) {
        unsupported("sinval.vma")
    }

    private fun sfence_w_inval() {
        unsupported("sfence.w.inval")
    }

    private fun sfence_inval_ir() {
        unsupported("sfence.inval.ir")
    }

    private fun hinval_vvma(rs1: UInt, rs2: UInt) {
        unsupported("hinval.vvma")
    }

    private fun hinval_gvma(rs1: UInt, rs2: UInt) {
        unsupported("hinval.gvma")
    }

    //endregion

    //region RV32 ATOMIC

    private fun lr_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val value = lw(gprFile.getdu(rs1))
        gprFile[rd] = value

        // TODO: reservation
    }

    private fun sc_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        sw(gprFile.getdu(rs1), gprFile.getw(rs2))
        gprFile[rd] = 0L

        // TODO: reservation
    }

    private fun amoswap_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val vaddr = gprFile.getdu(rs1)
        if ((vaddr and 0x3UL) != 0UL) {
            throw TrapException(id, 0x06UL, vaddr, "misaligned atomic address %x", vaddr)
        }

        val value: Int
        synchronized(machine.acquireLock(vaddr)) {
            val source = gprFile.getw(rs2)
            value = lw(vaddr)
            sw(vaddr, source)
        }

        gprFile[rd] = value
    }

    private fun amoadd_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val vaddr = gprFile.getdu(rs1)
        if ((vaddr and 0x3UL) != 0UL) {
            throw TrapException(id, 0x06UL, vaddr, "misaligned atomic address %x", vaddr)
        }

        val value: Int
        synchronized(machine.acquireLock(vaddr)) {
            val source = gprFile.getw(rs2)
            value = lw(vaddr)
            sw(vaddr, value + source)
        }

        gprFile[rd] = value
    }

    private fun amoxor_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amoxor.w")
    }

    private fun amoand_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amoand.w")
    }

    private fun amoor_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amoor.w")
    }

    private fun amomin_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amomin.w")
    }

    private fun amomax_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amomax.w")
    }

    private fun amominu_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amominu.w")
    }

    private fun amomaxu_w(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amomaxu.w")
    }

    //endregion

    //region RV32 SINGLE-PRECISION FLOATING-POINT

    private fun flw(rd: UInt, rs1: UInt, imm: UInt) {
        unsupported("flw")
    }

    private fun fsw(rs1: UInt, rs2: UInt, imm: UInt) {
        unsupported("fsw")
    }

    private fun fmadd_s(rd: UInt, rs1: UInt, rs2: UInt, rs3: UInt, rm: UInt) {
        unsupported("fmadd.s")
    }

    private fun fmsub_s(rd: UInt, rs1: UInt, rs2: UInt, rs3: UInt, rm: UInt) {
        unsupported("fmsub.s")
    }

    private fun fnmsub_s(rd: UInt, rs1: UInt, rs2: UInt, rs3: UInt, rm: UInt) {
        unsupported("fnmsub.s")
    }

    private fun fnmadd_s(rd: UInt, rs1: UInt, rs2: UInt, rs3: UInt, rm: UInt) {
        unsupported("fnmadd.s")
    }

    private fun fadd_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fadd.s")
    }

    private fun fsub_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fsub.s")
    }

    private fun fmul_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fmul.s")
    }

    private fun fdiv_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fdiv.s")
    }

    private fun fsqrt_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fsqrt.s")
    }

    private fun fsgnj_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fsgnj.s")
    }

    private fun fsgnjn_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fsgnjn.s")
    }

    private fun fsgnjx_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fsgnjx.s")
    }

    private fun fmin_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fmin.s")
    }

    private fun fmax_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fmax.s")
    }

    private fun fcvt_w_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.w.s")
    }

    private fun fcvt_wu_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.wu.s")
    }

    private fun fmv_x_w(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = fprFile.getfr(rs1)

        // TODO: what about rs2?
    }

    private fun feq_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("feq.s")
    }

    private fun flt_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("flt.s")
    }

    private fun fge_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fge.s")
    }

    private fun fclass_s(rd: UInt, rs1: UInt, rs2: UInt) {
        unsupported("fclass.s")
    }

    private fun fcvt_s_w(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.s.w")
    }

    private fun fcvt_s_wu(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.s.wu")
    }

    private fun fmv_w_x(rd: UInt, rs1: UInt, rs2: UInt) {
        fprFile[rd] = gprFile.getwu(rs1)

        // TODO: what about rs2?
    }

    //endregion

    //region RV32 INTEGER

    private fun lui(rd: UInt, uimm: UInt) {
        gprFile[rd] = signExtendLong(uimm, 32)
    }

    private fun auipc(rd: UInt, uimm: UInt) {
        gprFile[rd] = pc.toLong() + signExtendLong(uimm, 32)
    }

    private fun jal(next: ULong, rd: UInt, imm: UInt): ULong {
        val pnext = (pc.toLong() + signExtendLong(imm, 21)).toULong()
        gprFile[rd] = next
        return pnext
    }

    private fun jalr(next: ULong, rd: UInt, rs1: UInt, imm: UInt): ULong {
        val pnext = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong() and 1UL.inv()
        gprFile[rd] = next
        return pnext
    }

    private fun beq(next: ULong, rs1: UInt, rs2: UInt, imm: UInt): ULong {
        if (gprFile.getdu(rs1) == gprFile.getdu(rs2)) {
            return (pc.toLong() + signExtendLong(imm, 13)).toULong()
        }
        return next
    }

    private fun bne(next: ULong, rs1: UInt, rs2: UInt, imm: UInt): ULong {
        if (gprFile.getdu(rs1) != gprFile.getdu(rs2)) {
            return (pc.toLong() + signExtendLong(imm, 13)).toULong()
        }
        return next
    }

    private fun blt(next: ULong, rs1: UInt, rs2: UInt, imm: UInt): ULong {
        if (gprFile.getd(rs1) < gprFile.getd(rs2)) {
            return (pc.toLong() + signExtendLong(imm, 13)).toULong()
        }
        return next
    }

    private fun bge(next: ULong, rs1: UInt, rs2: UInt, imm: UInt): ULong {
        if (gprFile.getd(rs1) >= gprFile.getd(rs2)) {
            return (pc.toLong() + signExtendLong(imm, 13)).toULong()
        }
        return next
    }

    private fun bltu(next: ULong, rs1: UInt, rs2: UInt, imm: UInt): ULong {
        if (gprFile.getdu(rs1) < gprFile.getdu(rs2)) {
            return (pc.toLong() + signExtendLong(imm, 13)).toULong()
        }
        return next
    }

    private fun bgeu(next: ULong, rs1: UInt, rs2: UInt, imm: UInt): ULong {
        if (gprFile.getdu(rs1) >= gprFile.getdu(rs2)) {
            return (pc.toLong() + signExtendLong(imm, 13)).toULong()
        }
        return next
    }

    private fun lb(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = lb(vaddr)
    }

    private fun lh(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = lh(vaddr)
    }

    private fun lw(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = lw(vaddr)
    }

    private fun lbu(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = lbu(vaddr)
    }

    private fun lhu(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = lhu(vaddr)
    }

    private fun sb(rs1: UInt, rs2: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        sb(vaddr, gprFile.getb(rs2))
    }

    private fun sh(rs1: UInt, rs2: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        sh(vaddr, gprFile.geth(rs2))
    }

    private fun sw(rs1: UInt, rs2: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        sw(vaddr, gprFile.getw(rs2))
    }

    private fun addi(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getd(rs1) + signExtendLong(imm, 12)
    }

    private fun slti(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = if (gprFile.getd(rs1) < signExtendLong(imm, 12)) 1UL else 0UL
    }

    private fun sltiu(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = if (gprFile.getdu(rs1) < signExtendLong(imm, 12).toULong()) 1UL else 0UL
    }

    private fun xori(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getdu(rs1) xor signExtendLong(imm, 12).toULong()
    }

    private fun ori(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getdu(rs1) or signExtendLong(imm, 12).toULong()
    }

    private fun andi(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getdu(rs1) and signExtendLong(imm, 12).toULong()
    }

    private fun slli(rd: UInt, rs1: UInt, shamt: UInt) {
        if (rd == 0U && rs1 == 0U && shamt == 0x1FU) {
            semihosting = true
            return
        }

        gprFile[rd] = gprFile.getdu(rs1) shl shamt.toInt()
    }

    private fun srli(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getd(rs1) ushr shamt.toInt()
    }

    private fun srai(rd: UInt, rs1: UInt, shamt: UInt) {
        if (rd == 0U && rs1 == 0U && shamt == 0x07U) {
            semihosting = false
            return
        }

        gprFile[rd] = gprFile.getd(rs1) shr shamt.toInt()
    }

    private fun add(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) + gprFile.getd(rs2)
    }

    private fun sub(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) - gprFile.getd(rs2)
    }

    private fun sll(rd: UInt, rs1: UInt, rs2: UInt) {
        val shamt = gprFile.getd(rs2) and 63
        gprFile[rd] = gprFile.getdu(rs1) shl shamt.toInt()
    }

    private fun slt(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = if (gprFile.getd(rs1) < gprFile.getd(rs2)) 1UL else 0UL
    }

    private fun sltu(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = if (gprFile.getdu(rs1) < gprFile.getdu(rs2)) 1UL else 0UL
    }

    private fun xor(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getdu(rs1) xor gprFile.getdu(rs2)
    }

    private fun srl(rd: UInt, rs1: UInt, rs2: UInt) {
        val shamt = gprFile.getd(rs2) and 63
        gprFile[rd] = gprFile.getd(rs1) ushr shamt.toInt()
    }

    private fun sra(rd: UInt, rs1: UInt, rs2: UInt) {
        val shamt = gprFile.getd(rs2) and 63
        gprFile[rd] = gprFile.getd(rs1) shr shamt.toInt()
    }

    private fun or(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getdu(rs1) or gprFile.getdu(rs2)
    }

    private fun and(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getdu(rs1) and gprFile.getdu(rs2)
    }

    private fun fence(rd: UInt, rs1: UInt, fm: UInt, pred: UInt, succ: UInt) {
        // TODO: instruction and io cache
    }

    private fun fence_tso() {
        // TODO: instruction and io cache
    }

    private fun pause() {
        // TODO: pause execution for a few steps
    }

    private fun ecall() {
        when (priv) {
            CSR.CSR_M -> throw TrapException(id, 0x0BUL, 0UL, "environment call from machine mode")
            CSR.CSR_S -> throw TrapException(id, 0x09UL, 0UL, "environment call from supervisor mode")
            CSR.CSR_U -> throw TrapException(id, 0x08UL, 0UL, "environment call from user mode")
            else -> throw IllegalStateException()
        }
    }

    private fun ebreak(next: ULong): ULong {
        if (semihosting) {
            val sysnum = gprFile.getdu(0x0AU)
            val params = gprFile.getdu(0x0BU)

            when (sysnum) {
                Semihosting.SEMIHOSTING_SYSOPEN -> {
                    // open file
                    val fname = lstring(ldu(params))
                    val mode = ldu(params + 0x08UL)
                    val len = ldu(params + 0x10UL)

                    gprFile[0x0AU] = Semihosting.fopen(machine, fname, mode, len)
                }

                Semihosting.SEMIHOSTING_SYSWRITEC -> {
                    // write character stdout
                    val ch = lbu(params)

                    System.out.write(ch.toInt())
                }

                Semihosting.SEMIHOSTING_SYSWRITE -> {
                    // write buffer fd
                    val fd = ldu(params)
                    val memp = ldu(params + 0x08UL)
                    val len = ldu(params + 0x16UL)

                    gprFile[0x0AU] = Semihosting.fwrite(machine, fd, memp, len)
                }

                Semihosting.SEMIHOSTING_SYSREAD -> {
                    // read buffer fd
                    val fd = ldu(params)
                    val memp = ldu(params + 0x08UL)
                    val len = ldu(params + 0x16UL)

                    gprFile[0x0AU] = Semihosting.fread(machine, fd, memp, len)
                }

                Semihosting.SEMIHOSTING_SYSREADC -> {
                    // read character stdin
                    try {
                        if (System.`in`.available() > 0) {
                            gprFile[0x0AU] = System.`in`.read().toLong()
                        } else {
                            gprFile[0x0AU] = -1L
                        }
                    } catch (e: IOException) {
                        error("sysreadc: %s", e)
                        gprFile[0x0AU] = -1L
                    }
                }

                Semihosting.SEMIHOSTING_SYSERRNO -> {
                    // last host error
                    gprFile[0x0AU] = 0UL
                }

                else -> Log.warn("undefined semihosting call sysnum=%x, params=%x", sysnum, params)
            }
            return next
        }

        if (machine.handleBreakpoint(id)) {
            return pc
        }

        throw TrapException(id, 0x03UL, pc, "breakpoint instruction")
    }

    //endregion

    //region RV32 MULTIPLY

    private fun mul(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) * gprFile.getd(rs2)
    }

    private fun mulh(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = mulh64(gprFile.getd(rs1), gprFile.getd(rs2))
    }

    private fun mulhsu(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = mulhsu64(gprFile.getd(rs1), gprFile.getd(rs2))
    }

    private fun mulhu(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = mulhu64(gprFile.getd(rs1), gprFile.getd(rs2))
    }

    private fun div(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getd(rs1)
        val rhs = gprFile.getd(rs2)

        if (rhs == 0L) {
            gprFile[rd] = -1L
            return
        }

        if (lhs == Long.MIN_VALUE && rhs == -1L) {
            gprFile[rd] = lhs
            return
        }

        gprFile[rd] = lhs / rhs
    }

    private fun divu(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getdu(rs1)
        val rhs = gprFile.getdu(rs2)

        if (rhs == 0UL) {
            gprFile[rd] = 0UL.inv()
            return
        }

        gprFile[rd] = lhs / rhs
    }

    private fun rem(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getd(rs1)
        val rhs = gprFile.getd(rs2)

        if (rhs == 0L) {
            gprFile[rd] = lhs
            return
        }

        if (lhs == Long.MIN_VALUE && rhs == -1L) {
            gprFile[rd] = 0L
            return
        }

        gprFile[rd] = lhs % rhs
    }

    private fun remu(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getdu(rs1)
        val rhs = gprFile.getdu(rs2)

        if (rhs == 0UL) {
            gprFile[rd] = lhs
            return
        }

        gprFile[rd] = lhs % rhs
    }

    //endregion

    //region RV64 ATOMIC

    private fun lr_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val value = ldu(gprFile.getdu(rs1))
        gprFile[rd] = value

        // TODO: reservation
    }

    private fun sc_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        sd(gprFile.getdu(rs1), gprFile.getdu(rs2))
        gprFile[rd] = 0UL

        // TODO: reservation
    }

    private fun amoswap_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val vaddr = gprFile.getdu(rs1)
        if ((vaddr and 0x7UL) != 0UL) {
            throw TrapException(id, 0x06UL, vaddr, "misaligned atomic address %x", vaddr)
        }

        val value: ULong
        synchronized(machine.acquireLock(vaddr)) {
            val source = gprFile.getdu(rs2)
            value = ldu(vaddr)
            sd(vaddr, source)
        }

        gprFile[rd] = value
    }

    private fun amoadd_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val vaddr = gprFile.getdu(rs1)
        if ((vaddr and 0x7UL) != 0UL) {
            throw TrapException(id, 0x06UL, vaddr, "misaligned atomic address %x", vaddr)
        }

        val value: Long
        synchronized(machine.acquireLock(vaddr)) {
            val source = gprFile.getd(rs2)
            value = ld(vaddr)
            sd(vaddr, value + source)
        }

        gprFile[rd] = value
    }

    private fun amoxor_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amoxor.d")
    }

    private fun amoand_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amoand.d")
    }

    private fun amoor_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        val vaddr = gprFile.getdu(rs1)
        if ((vaddr and 0x7UL) != 0UL) {
            throw TrapException(id, 0x06UL, vaddr, "misaligned atomic address %x", vaddr)
        }

        val value: ULong
        synchronized(machine.acquireLock(vaddr)) {
            val source = gprFile.getdu(rs2)
            value = ldu(vaddr)
            sd(vaddr, value or source)
        }

        gprFile[rd] = value
    }

    private fun amomin_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amomin.d")
    }

    private fun amomax_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amomax.d")
    }

    private fun amominu_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amominu.d")
    }

    private fun amomaxu_d(rd: UInt, rs1: UInt, rs2: UInt, aq: UInt, rl: UInt) {
        unsupported("amomaxu.d")
    }

    //endregion

    //region RV64 SINGLE-PRECISION FLOATING-POINT

    private fun fcvt_l_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.l.s")
    }

    private fun fcvt_lu_s(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.lu.s")
    }

    private fun fcvt_s_l(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.s.l")
    }

    private fun fcvt_s_lu(rd: UInt, rs1: UInt, rs2: UInt, rm: UInt) {
        unsupported("fcvt.s.lu")
    }

    // endregion

    //region RV64 INTEGER

    private fun lwu(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = lwu(vaddr)
    }

    private fun ld(rd: UInt, rs1: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        gprFile[rd] = ldu(vaddr)
    }

    private fun sd(rs1: UInt, rs2: UInt, imm: UInt) {
        val vaddr = (gprFile.getd(rs1) + signExtendLong(imm, 12)).toULong()
        sd(vaddr, gprFile.getdu(rs2))
    }

    private fun addiw(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getw(rs1) + signExtend(imm, 12)
    }

    private fun slliw(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getw(rs1) shl shamt.toInt()
    }

    private fun srliw(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getw(rs1) ushr shamt.toInt()
    }

    private fun sraiw(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getw(rs1) shr shamt.toInt()
    }

    private fun addw(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getw(rs1) + gprFile.getw(rs2)
    }

    private fun subw(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getw(rs1) - gprFile.getw(rs2)
    }

    private fun sllw(rd: UInt, rs1: UInt, rs2: UInt) {
        val shamt = gprFile.getw(rs2) and 31
        gprFile[rd] = gprFile.getw(rs1) shl shamt
    }

    private fun srlw(rd: UInt, rs1: UInt, rs2: UInt) {
        val shamt = gprFile.getw(rs2) and 31
        gprFile[rd] = gprFile.getw(rs1) ushr shamt
    }

    private fun sraw(rd: UInt, rs1: UInt, rs2: UInt) {
        val shamt = gprFile.getw(rs2) and 31
        gprFile[rd] = gprFile.getw(rs1) shr shamt
    }

    //endregion

    //region RV64 MULTIPLY

    private fun mulw(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getw(rs1) * gprFile.getw(rs2)
    }

    private fun divw(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getw(rs1)
        val rhs = gprFile.getw(rs2)

        if (rhs == 0) {
            gprFile[rd] = -1
            return
        }

        if (lhs == Int.MIN_VALUE && rhs == -1) {
            gprFile[rd] = lhs
            return
        }

        gprFile[rd] = lhs / rhs
    }

    private fun divuw(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getwu(rs1)
        val rhs = gprFile.getwu(rs2)

        if (rhs == 0U) {
            gprFile[rd] = -1
            return
        }

        gprFile[rd] = lhs / rhs
    }

    private fun remw(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getw(rs1)
        val rhs = gprFile.getw(rs2)

        if (rhs == 0) {
            gprFile[rd] = lhs
            return
        }

        if (lhs == Int.MIN_VALUE && rhs == -1) {
            gprFile[rd] = 0
            return
        }

        gprFile[rd] = lhs % rhs
    }

    private fun remuw(rd: UInt, rs1: UInt, rs2: UInt) {
        val lhs = gprFile.getwu(rs1)
        val rhs = gprFile.getwu(rs2)

        if (rhs == 0U) {
            gprFile[rd] = lhs
            return
        }

        gprFile[rd] = lhs % rhs
    }

    //endregion

    //region COMPRESSED

    private fun c_addi4spn(rd: UInt, uimm: UInt) {
        gprFile[rd] = gprFile.getdu(0x2U) + uimm
    }

    private fun c_fld(rd: UInt, rs1: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(rs1) + uimm
        fprFile[rd] = ldu(vaddr)
    }

    private fun c_lw(rd: UInt, rs1: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(rs1) + uimm
        gprFile[rd] = lw(vaddr).toLong()
    }

    private fun c_ld(rd: UInt, rs1: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(rs1) + uimm
        gprFile[rd] = ld(vaddr)
    }

    private fun c_fsd(rs1: UInt, rs2: UInt, uimm: UInt) {
        unsupported("c.fsd")
    }

    private fun c_sw(rs1: UInt, rs2: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(rs1) + uimm
        sw(vaddr, gprFile.getwu(rs2))
    }

    private fun c_sd(rs1: UInt, rs2: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(rs1) + uimm
        sd(vaddr, gprFile.getdu(rs2))
    }

    private fun c_nop() {
        // noop
    }

    private fun c_addi(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getd(rs1) + signExtendLong(imm, 6)
    }

    private fun c_addiw(rd: UInt, rs1: UInt, imm: UInt) {
        gprFile[rd] = gprFile.getw(rs1) + signExtend(imm, 6)
    }

    private fun c_li(rd: UInt, imm: UInt) {
        gprFile[rd] = signExtendLong(imm, 6)
    }

    private fun c_addi16sp(imm: UInt) {
        gprFile[0x2U] = gprFile.getd(0x2U) + signExtendLong(imm, 10)
    }

    private fun c_lui(rd: UInt, imm: UInt) {
        gprFile[rd] = signExtendLong(imm, 18)
    }

    private fun c_srli(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getd(rs1) ushr shamt.toInt()
    }

    private fun c_srai(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getd(rs1) shr shamt.toInt()
    }

    private fun c_andi(rd: UInt, rs1: UInt, uimm: UInt) {
        gprFile[rd] = gprFile.getd(rs1) and signExtendLong(uimm, 6)
    }

    private fun c_sub(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) - gprFile.getd(rs2)
    }

    private fun c_xor(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) xor gprFile.getd(rs2)
    }

    private fun c_or(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) or gprFile.getd(rs2)
    }

    private fun c_and(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) and gprFile.getd(rs2)
    }

    private fun c_subw(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getw(rs1) - gprFile.getw(rs2)
    }

    private fun c_addw(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getw(rs1) + gprFile.getw(rs2)
    }

    private fun c_j(imm: UInt): ULong {
        return (pc.toLong() + signExtendLong(imm, 12)).toULong()
    }

    private fun c_beqz(next: ULong, rs1: UInt, imm: UInt): ULong {
        if (gprFile.getdu(rs1) == 0UL) {
            return (pc.toLong() + signExtendLong(imm, 9)).toULong()
        }
        return next
    }

    private fun c_bnez(next: ULong, rs1: UInt, imm: UInt): ULong {
        if (gprFile.getdu(rs1) != 0UL) {
            return (pc.toLong() + signExtendLong(imm, 9)).toULong()
        }
        return next
    }

    private fun c_slli(rd: UInt, rs1: UInt, shamt: UInt) {
        gprFile[rd] = gprFile.getd(rs1) shl shamt.toInt()
    }

    private fun c_fldsp(rd: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(0x2U) + uimm
        fprFile[rd] = ldu(vaddr)
    }

    private fun c_lwsp(rd: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(0x2U) + uimm
        gprFile[rd] = lw(vaddr).toLong()
    }

    private fun c_ldsp(rd: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(0x2U) + uimm
        gprFile[rd] = ld(vaddr)
    }

    private fun c_jr(rs1: UInt): ULong {
        return gprFile.getdu(rs1) and 1UL.inv()
    }

    private fun c_mv(rd: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getdu(rs2)
    }

    private fun c_ebreak(): ULong {
        if (machine.handleBreakpoint(id)) {
            return pc
        }

        throw TrapException(id, 0x03UL, pc, "breakpoint instruction")
    }

    private fun c_jalr(next: ULong, rs1: UInt): ULong {
        val pnext = gprFile.getdu(rs1) and 1UL.inv()
        gprFile[0x1U] = next
        return pnext
    }

    private fun c_add(rd: UInt, rs1: UInt, rs2: UInt) {
        gprFile[rd] = gprFile.getd(rs1) + gprFile.getd(rs2)
    }

    private fun c_fsdsp(rs2: UInt, uimm: UInt) {
        unsupported("c.fsdsp")
    }

    private fun c_swsp(rs2: UInt, uimm: UInt) {
        unsupported("c.swsp")
    }

    private fun c_sdsp(rs2: UInt, uimm: UInt) {
        val vaddr = gprFile.getdu(0x2U) + uimm
        sd(vaddr, gprFile.getdu(rs2))
    }

    //endregion

    //region EXTENSION - CSR

    private fun csrrw(rd: UInt, rs1: UInt, csr: UInt) {
        if (rd == 0U) {
            csrFile[csr, priv] = gprFile.getdu(rs1)
            return
        }

        val value = csrFile[csr, priv]
        csrFile[csr, priv] = gprFile.getdu(rs1)
        gprFile[rd] = value
    }

    private fun csrrs(rd: UInt, rs1: UInt, csr: UInt) {
        val value = csrFile[csr, priv]
        if (rs1 != 0U) {
            val mask = gprFile.getdu(rs1)
            csrFile[csr, priv] = value or mask
        }
        gprFile[rd] = value
    }

    private fun csrrc(rd: UInt, rs1: UInt, csr: UInt) {
        val value = csrFile[csr, priv]
        if (rs1 != 0U) {
            val mask = gprFile.getdu(rs1)
            csrFile[csr, priv] = value and mask.inv()
        }
        gprFile[rd] = value
    }

    private fun csrrwi(rd: UInt, uimm: UInt, csr: UInt) {
        if (rd == 0U) {
            csrFile[csr, priv] = uimm.toULong()
            return
        }

        val value = csrFile[csr, priv]
        csrFile[csr, priv] = uimm.toULong()
        gprFile[rd] = value
    }

    private fun csrrsi(rd: UInt, uimm: UInt, csr: UInt) {
        val value = csrFile[csr, priv]
        if (uimm != 0U) {
            csrFile[csr, priv] = value or uimm.toULong()
        }
        gprFile[rd] = value
    }

    private fun csrrci(rd: UInt, uimm: UInt, csr: UInt) {
        val value = csrFile[csr, priv]
        if (uimm != 0U) {
            csrFile[csr, priv] = value and uimm.inv().toULong()
        }
        gprFile[rd] = value
    }

    //endregion

    //region EXTENSION - FENCE.I

    private fun fence_i(rd: UInt, rs1: UInt, imm: UInt) {
        // TODO: instruction and io cache
    }

    //endregion

    companion object {
        private fun mulhu64(x: Long, y: Long): Long {
            val x_lo = x and 0xFFFFFFFFL
            val x_hi = x ushr 32
            val y_lo = y and 0xFFFFFFFFL
            val y_hi = y ushr 32

            val lo_lo = x_lo * y_lo
            val hi_lo = x_hi * y_lo
            val lo_hi = x_lo * y_hi
            val hi_hi = x_hi * y_hi

            val mid = (hi_lo and 0xFFFFFFFFL) + (lo_hi and 0xFFFFFFFFL) + (lo_lo ushr 32)

            return hi_hi + (hi_lo ushr 32) + (lo_hi ushr 32) + (mid ushr 32)
        }

        private fun mulh64(x: Long, y: Long): Long {
            val neg = (x < 0) xor (y < 0)

            val ux = if (x < 0) -x else x
            val uy = if (y < 0) -y else y

            val high: Long = mulhu64(ux, uy)

            return if (neg) high.inv() + (if (ux * uy != 0L) 1 else 0) else high
        }

        private fun mulhsu64(x: Long, y: Long): Long {
            val neg = x < 0

            val ux = if (x < 0) -x else x
            val uy = y // already unsigned

            val high: Long = mulhu64(ux, uy)

            return if (neg) high.inv() + (if (ux * uy != 0L) 1 else 0) else high
        }
    }
}
