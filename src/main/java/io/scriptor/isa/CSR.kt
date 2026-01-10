package io.scriptor.isa

interface CSR {
    companion object {
        fun readonly(addr: UInt): Boolean {
            return ((addr shr 10) and 3U) == 3U
        }

        fun unprivileged(addr: UInt, priv: UInt): Boolean {
            return ((addr shr 8) and 3U) > priv
        }

        /**
         * read/write
         */
        const val CSR_RW0 = 0U

        /**
         * read/write
         */
        const val CSR_RW1 = 1U

        /**
         * read/write
         */
        const val CSR_RW2 = 2U

        /**
         * read-only
         */
        const val CSR_RO = 3U

        const val STATUS_SUM = 18UL
        const val STATUS_MXR = 19UL

        /**
         * user
         */
        const val CSR_U = 0U

        /**
         * supervisor
         */
        const val CSR_S = 1U

        /**
         * hypervisor
         */
        const val CSR_H = 2U

        /**
         * machine
         */
        const val CSR_M = 3U

        /**
         * Floating-Point Accrued Exceptions.
         */
        const val fflags = 0x001U

        /**
         * Floating-Point Dynamic Rounding Mode.
         */
        const val frm = 0x002U

        /**
         * Floating-Point Control and Status Register (frm + fflags).
         */
        const val fcsr = 0x003U

        /**
         * Vector start position.
         */
        const val vstart = 0x008U

        /**
         * Fixed-point accrued saturation flag.
         */
        const val vxsat = 0x009U

        /**
         * Fixed-point rounding mode.
         */
        const val vxrm = 0x00AU

        /**
         * Vector control and status register.
         */
        const val vcsr = 0x00FU

        /**
         * Vector length.
         */
        const val vl = 0xC20U

        /**
         * Vector data type register.
         */
        const val vtype = 0xC21U

        /**
         * Vector register length in bytes.
         */
        const val vlenb = 0xC22U

        /**
         * Shadow Stack Pointer.
         */
        const val ssp = 0x011U

        /**
         * Seed for cryptographic random bit generators.
         */
        const val seed = 0x015U

        /**
         * Table jump base vector and control register.
         */
        const val jvt = 0x017U

        /**
         * Cycle counter for RDCYCLE instruction.
         */
        const val cycle = 0xC00U

        /**
         * Timer for RDTIME instruction.
         */
        const val time = 0xC01U

        /**
         * Instructions-retired counter for RDINSTRET instruction.
         */
        const val instret = 0xC02U

        /**
         * Performance-monitoring counter.
         */
        const val hpmcounter3 = 0xC03U

        /**
         * Performance-monitoring counter.
         */
        const val hpmcounter31 = 0xC1FU

        /**
         * Upper 32 bits of cycle, RV32 only.
         */
        const val cycleh = 0xC80U

        /**
         * Upper 32 bits of time, RV32 only.
         */
        const val timeh = 0xC81U

        /**
         * Upper 32 bits of instret, RV32 only.
         */
        const val instreth = 0xC82U

        /**
         * Upper 32 bits of hpmcounter3, RV32 only.
         */
        const val hpmcounter3h = 0xC83U

        /**
         * Upper 32 bits of hpmcounter31, RV32 only.
         */
        const val hpmcounter31h = 0xC9FU

        /**
         * Supervisor status register.
         */
        const val sstatus = 0x100U

        /**
         * Supervisor exception delegation register.
         */
        const val sedeleg = 0x102U

        /**
         * Supervisor interrupt delegation register.
         */
        const val sideleg = 0x103U

        /**
         * Supervisor interrupt-enable register.
         */
        const val sie = 0x104U

        /**
         * Supervisor trap handler base address.
         */
        const val stvec = 0x105U

        /**
         * Supervisor counter enable.
         */
        const val scounteren = 0x106U

        /**
         * Supervisor environment configuration register.
         */
        const val senvcfg = 0x10AU

        /**
         * Supervisor counter-inhibit register.
         */
        const val scountinhibit = 0x120U

        /**
         * Supervisor scratch register.
         */
        const val sscratch = 0x140U

        /**
         * Supervisor exception program counter.
         */
        const val sepc = 0x141U

        /**
         * Supervisor trap cause.
         */
        const val scause = 0x142U

        /**
         * Supervisor trap value.
         */
        const val stval = 0x143U

        /**
         * Supervisor interrupt pending.
         */
        const val sip = 0x144U

        /**
         * Supervisor count overflow.
         */
        const val scountovf = 0xDA0U

        /**
         * Supervisor count overflow.
         */
        const val siselect = 0x150U

        /**
         * Supervisor indirect register alias.
         */
        const val sireg = 0x151U

        /**
         * Supervisor indirect register alias 2.
         */
        const val sireg2 = 0x152U

        /**
         * Supervisor indirect register alias 3.
         */
        const val sireg3 = 0x153U

        /**
         * Supervisor indirect register alias 4.
         */
        const val sireg4 = 0x155U

        /**
         * Supervisor indirect register alias 5.
         */
        const val sireg5 = 0x156U

        /**
         * Supervisor indirect register alias 6.
         */
        const val sireg6 = 0x157U

        /**
         * Supervisor address translation and protection.
         */
        const val satp = 0x180U

        /**
         * Supervisor timer compare.
         */
        const val stimecmp = 0x14DU

        /**
         * Upper 32 bits of stimecmp, RV32 only.
         */
        const val stimecmph = 0x15DU

        /**
         * Supervisor-mode context register.
         */
        const val scontext = 0x5A8U

        /**
         * Supervisor Resource Management Configuration.
         */
        const val srmcfg = 0x181U

        /**
         * Supervisor State Enable 0 Register.
         */
        const val sstateen0 = 0x10CU

        /**
         * Supervisor State Enable 1 Register.
         */
        const val sstateen1 = 0x10DU

        /**
         * Supervisor State Enable 2 Register.
         */
        const val sstateen2 = 0x10EU

        /**
         * Supervisor State Enable 3 Register.
         */
        const val sstateen3 = 0x10FU

        /**
         * Supervisor Control Transfer Records Control Register.
         */
        const val sctrctl = 0x14EU

        /**
         * Supervisor Control Transfer Records Status Register.
         */
        const val sctrstatus = 0x14FU

        /**
         * Supervisor Control Transfer Records Depth Register.
         */
        const val sctrdepth = 0x15FU

        /**
         * Hypervisor status register.
         */
        const val hstatus = 0x600U

        /**
         * Hypervisor exception delegation register.
         */
        const val hedeleg = 0x602U

        /**
         * Hypervisor interrupt delegation register.
         */
        const val hideleg = 0x603U

        /**
         * Hypervisor interrupt-enable register.
         */
        const val hie = 0x604U

        /**
         * Hypervisor counter enable.
         */
        const val hcounteren = 0x606U

        /**
         * Hypervisor guest external interrupt-enable register.
         */
        const val hgeie = 0x607U

        /**
         * Upper 32 bits of hedeleg, RV32 only.
         */
        const val hedelegh = 0x612U

        /**
         * Hypervisor trap value.
         */
        const val htval = 0x643U

        /**
         * Hypervisor interrupt pending.
         */
        const val hip = 0x644U

        /**
         * Hypervisor virtual interrupt pending.
         */
        const val hvip = 0x645U

        /**
         * Hypervisor trap instruction (transformed).
         */
        const val htinst = 0x64AU

        /**
         * Hypervisor guest external interrupt pending.
         */
        const val hgeip = 0xE12U

        /**
         * Hypervisor environment configuration register.
         */
        const val henvcfg = 0x60AU

        /**
         * Upper 32 bits of henvcfg, RV32 only.
         */
        const val henvcfgh = 0x61AU

        /**
         * Hypervisor guest address translation and protection.
         */
        const val hgatp = 0x680U

        /**
         * Hypervisor-mode context register.
         */
        const val hcontext = 0x6A8U

        /**
         * Delta for VS/VU-mode timer.
         */
        const val htimedelta = 0x605U

        /**
         * Upper 32 bits of htimedelta, RV32 only.
         */
        const val htimedeltah = 0x615U

        /**
         * Hypervisor State Enable 0 Register.
         */
        const val hstateen0 = 0x60CU

        /**
         * Hypervisor State Enable 1 Register.
         */
        const val hstateen1 = 0x60DU

        /**
         * Hypervisor State Enable 2 Register.
         */
        const val hstateen2 = 0x60EU

        /**
         * Hypervisor State Enable 3 Register.
         */
        const val hstateen3 = 0x60FU

        /**
         * Upper 32 bits of Hypervisor State Enable 0 Register, RV32 only.
         */
        const val hstateen0h = 0x61CU

        /**
         * Upper 32 bits of Hypervisor State Enable 1 Register, RV32 only.
         */
        const val hstateen1h = 0x61DU

        /**
         * Upper 32 bits of Hypervisor State Enable 2 Register, RV32 only.
         */
        const val hstateen2h = 0x61EU

        /**
         * Upper 32 bits of Hypervisor State Enable 3 Register, RV32 only.
         */
        const val hstateen3h = 0x61FU

        /**
         * Virtual supervisor status register.
         */
        const val vsstatus = 0x200U

        /**
         * Virtual supervisor interrupt-enable register.
         */
        const val vsie = 0x204U

        /**
         * Virtual supervisor trap handler base address.
         */
        const val vstvec = 0x205U

        /**
         * Virtual supervisor scratch register.
         */
        const val vsscratch = 0x240U

        /**
         * Virtual supervisor exception program counter.
         */
        const val vsepc = 0x241U

        /**
         * Virtual supervisor trap cause.
         */
        const val vscause = 0x242U

        /**
         * Virtual supervisor trap value.
         */
        const val vstval = 0x243U

        /**
         * Virtual supervisor interrupt pending.
         */
        const val vsip = 0x244U

        /**
         * Virtual supervisor address translation and protection.
         */
        const val vsatp = 0x280U

        /**
         * Virtual supervisor indirect register select.
         */
        const val vsiselect = 0x250U

        /**
         * Virtual supervisor indirect register alias.
         */
        const val vsireg = 0x251U

        /**
         * Virtual supervisor indirect register alias 2.
         */
        const val vsireg2 = 0x252U

        /**
         * Virtual supervisor indirect register alias 3.
         */
        const val vsireg3 = 0x253U

        /**
         * Virtual supervisor indirect register alias 4.
         */
        const val vsireg4 = 0x255U

        /**
         * Virtual supervisor indirect register alias 5.
         */
        const val vsireg5 = 0x256U

        /**
         * Virtual supervisor indirect register alias 6.
         */
        const val vsireg6 = 0x257U

        /**
         * Virtual supervisor timer compare.
         */
        const val vstimecmp = 0x24DU

        /**
         * Upper 32 bits of vstimecmp, RV32 only.
         */
        const val vstimecmph = 0x25DU

        /**
         * Virtual Supervisor Control Transfer Records Control Register.
         */
        const val vsctrctl = 0x24EU

        /**
         * Vendor ID.
         */
        const val mvendorid = 0xF11U

        /**
         * Architecture ID.
         */
        const val marchid = 0xF12U

        /**
         * Implementation ID.
         */
        const val mimpid = 0xF13U

        /**
         * Hardware thread ID.
         */
        const val mhartid = 0xF14U

        /**
         * Pointer to configuration data structure.
         */
        const val mconfigptr = 0xF15U

        /**
         * Machine status register.
         */
        const val mstatus = 0x300U

        /**
         * ISA and extensions
         */
        const val misa = 0x301U

        /**
         * Machine exception delegation register.
         */
        const val medeleg = 0x302U

        /**
         * Machine interrupt delegation register.
         */
        const val mideleg = 0x303U

        /**
         * Machine interrupt-enable register.
         */
        const val mie = 0x304U

        /**
         * Machine trap-handler base address.
         */
        const val mtvec = 0x305U

        /**
         * Machine counter enable.
         */
        const val mcounteren = 0x306U

        /**
         * Additional machine status register, RV32 only.
         */
        const val mstatush = 0x310U

        /**
         * Upper 32 bits of medeleg, RV32 only.
         */
        const val medelegh = 0x12U

        /**
         * Machine cycle counter configuration register.
         */
        const val mcyclecfg = 0x321U

        /**
         * Machine instret counter configuration register.
         */
        const val minstretcfg = 0x322U

        /**
         * Upper 32 bits of mcyclecfg, RV32 only.
         */
        const val mcyclecfgh = 0x721U

        /**
         * Upper 32 bits of minstretcfg, RV32 only.
         */
        const val minstretcfgh = 0x722U

        /**
         * Machine scratch register.
         */
        const val mscratch = 0x340U

        /**
         * Machine exception program counter.
         */
        const val mepc = 0x341U

        /**
         * Machine trap cause.
         */
        const val mcause = 0x342U

        /**
         * Machine trap value.
         */
        const val mtval = 0x343U

        /**
         * Machine interrupt pending.
         */
        const val mip = 0x344U

        /**
         * Machine trap instruction (transformed).
         */
        const val mtinst = 0x34AU

        /**
         * Machine second trap value.
         */
        const val mtval2 = 0x34BU

        /**
         * Machine indirect register select.
         */
        const val miselect = 0x350U

        /**
         * Machine indirect register alias.
         */
        const val mireg = 0x351U

        /**
         * Machine indirect register alias 2.
         */
        const val mireg2 = 0x352U

        /**
         * Machine indirect register alias 3.
         */
        const val mireg3 = 0x353U

        /**
         * Machine indirect register alias 4.
         */
        const val mireg4 = 0x355U

        /**
         * Machine indirect register alias 5.
         */
        const val mireg5 = 0x356U

        /**
         * Machine indirect register alias 6.
         */
        const val mireg6 = 0x357U

        /**
         * Machine environment configuration register.
         */
        const val menvcfg = 0x30AU

        /**
         * Upper 32 bits of menvcfg, RV32 only.
         */
        const val menvcfgh = 0x31AU

        /**
         * Machine security configuration register.
         */
        const val mseccfg = 0x747U

        /**
         * Upper 32 bits of mseccfg, RV32 only.
         */
        const val mseccfgh = 0x757U

        /**
         * Physical memory protection configuration.
         */
        const val pmpcfg0 = 0x3A0U

        /**
         * Physical memory protection configuration, RV32 only.
         */
        const val pmpcfg15 = 0x3AFU

        /**
         * Physical memory protection address register.
         */
        const val pmpaddr0 = 0x3B0U

        /**
         * Physical memory protection address register.
         */
        const val pmpaddr63 = 0x3EFU

        /**
         * Machine State Enable 0 Register.
         */
        const val mstateen0 = 0x30CU

        /**
         * Machine State Enable 1 Register.
         */
        const val mstateen1 = 0x30DU

        /**
         * Machine State Enable 2 Register.
         */
        const val mstateen2 = 0x30EU

        /**
         * Machine State Enable 3 Register.
         */
        const val mstateen3 = 0x30FU

        /**
         * Upper 32 bits of Machine State Enable 0 Register, RV32 only.
         */
        const val mstateen0h = 0x31CU

        /**
         * Upper 32 bits of Machine State Enable 1 Register, RV32 only.
         */
        const val mstateen1h = 0x31DU

        /**
         * Upper 32 bits of Machine State Enable 2 Register, RV32 only.
         */
        const val mstateen2h = 0x31EU

        /**
         * Upper 32 bits of Machine State Enable 3 Register, RV32 only.
         */
        const val mstateen3h = 0x31FU

        /**
         * Resumable NMI scratch register.
         */
        const val mnscratch = 0x740U

        /**
         * Resumable NMI program counter.
         */
        const val mnepc = 0x741U

        /**
         * Resumable NMI cause.
         */
        const val mncause = 0x742U

        /**
         * Resumable NMI status.
         */
        const val mnstatus = 0x744U

        /**
         * Machine cycle counter.
         */
        const val mcycle = 0xB00U

        /**
         * Machine instructions-retired counter.
         */
        const val minstret = 0xB02U

        /**
         * Machine performance-monitoring counter.
         */
        const val mhpmcounter3 = 0xB03U

        /**
         * Machine performance-monitoring counter.
         */
        const val mhpmcounter31 = 0xB1FU

        /**
         * Upper 32 bits of mcycle, RV32 only.
         */
        const val mcycleh = 0xB80U

        /**
         * Upper 32 bits of minstret, RV32 only.
         */
        const val minstreth = 0xB82U

        /**
         * Upper 32 bits of mhpmcounter3, RV32 only.
         */
        const val mhpmcounter3h = 0xB83U

        /**
         * Upper 32 bits of mhpmcounter31, RV32 only.
         */
        const val mhpmcounter31h = 0xB9FU

        /**
         * Machine counter-inhibit register.
         */
        const val mcountinhibit = 0x320U

        /**
         * Machine performance-monitoring event selector.
         */
        const val mhpmevent3 = 0x323U

        /**
         * Machine performance-monitoring event selector.
         */
        const val mhpmevent31 = 0x33FU

        /**
         * Upper 32 bits of mhpmevent3, RV32 only.
         */
        const val mhpmevent3h = 0x723U

        /**
         * Upper 32 bits of mhpmevent31, RV32 only.
         */
        const val mhpmevent31h = 0x73FU

        /**
         * Machine Control Transfer Records Control Register.
         */
        const val mctrctl = 0x34EU

        /**
         * Debug/Trace trigger register select.
         */
        const val tselect = 0x7A0U

        /**
         * First Debug/Trace trigger data register.
         */
        const val tdata1 = 0x7A1U

        /**
         * Second Debug/Trace trigger data register.
         */
        const val tdata2 = 0x7A2U

        /**
         * Third Debug/Trace trigger data register.
         */
        const val tdata3 = 0x7A3U

        /**
         * Machine-mode context register.
         */
        const val mcontext = 0x7A8U

        /**
         * Debug control and status register.
         */
        const val dcsr = 0x7B0U

        /**
         * Debug program counter.
         */
        const val dpc = 0x7B1U

        /**
         * Debug scratch register 0.
         */
        const val dscratch0 = 0x7B2U

        /**
         * Debug scratch register 1.
         */
        const val dscratch1 = 0x7B3U
    }
}
