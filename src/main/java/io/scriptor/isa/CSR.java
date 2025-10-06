package io.scriptor.isa;

public interface CSR {

    /**
     * read/write
     */
    int CSR_RW0 = 0b00;
    /**
     * read/write
     */
    int CSR_RW1 = 0b01;
    /**
     * read/write
     */
    int CSR_RW2 = 0b10;
    /**
     * read-only
     */
    int CSR_RO = 0b11;

    long STATUS_SUM = 18L;
    long STATUS_MXR = 19L;

    /**
     * user
     */
    int CSR_U = 0b00;
    /**
     * supervisor
     */
    int CSR_S = 0b01;
    /**
     * hypervisor
     */
    int CSR_H = 0b10;
    /**
     * machine
     */
    int CSR_M = 0b11;

    /**
     * Floating-Point Accrued Exceptions.
     */
    int fflags = 0x001;
    /**
     * Floating-Point Dynamic Rounding Mode.
     */
    int frm = 0x002;
    /**
     * Floating-Point Control and Status Register (frm + fflags).
     */
    int fcsr = 0x003;

    /**
     * Vector start position.
     */
    int vstart = 0x008;
    /**
     * Fixed-point accrued saturation flag.
     */
    int vxsat = 0x009;
    /**
     * Fixed-point rounding mode.
     */
    int vxrm = 0x00A;
    /**
     * Vector control and status register.
     */
    int vcsr = 0x00F;
    /**
     * Vector length.
     */
    int vl = 0xC20;
    /**
     * Vector data type register.
     */
    int vtype = 0xC21;
    /**
     * Vector register length in bytes.
     */
    int vlenb = 0xC22;

    /**
     * Shadow Stack Pointer.
     */
    int ssp = 0x011;

    /**
     * Seed for cryptographic random bit generators.
     */
    int seed = 0x015;

    /**
     * Table jump base vector and control register.
     */
    int jvt = 0x017;

    /**
     * Cycle counter for RDCYCLE instruction.
     */
    int cycle = 0xC00;
    /**
     * Timer for RDTIME instruction.
     */
    int time = 0xC01;
    /**
     * Instructions-retired counter for RDINSTRET instruction.
     */
    int instret = 0xC02;
    /**
     * Performance-monitoring counter.
     */
    int hpmcounter3 = 0xC03;
    /**
     * Performance-monitoring counter.
     */
    int hpmcounter31 = 0xC1F;
    /**
     * Upper 32 bits of cycle, RV32 only.
     */
    int cycleh = 0xC80;
    /**
     * Upper 32 bits of time, RV32 only.
     */
    int timeh = 0xC81;
    /**
     * Upper 32 bits of instret, RV32 only.
     */
    int instreth = 0xC82;
    /**
     * Upper 32 bits of hpmcounter3, RV32 only.
     */
    int hpmcounter3h = 0xC83;
    /**
     * Upper 32 bits of hpmcounter31, RV32 only.
     */
    int hpmcounter31h = 0xC9F;

    /**
     * Supervisor status register.
     */
    int sstatus = 0x100;
    /**
     * Supervisor exception delegation register.
     */
    int sedeleg = 0x102;
    /**
     * Supervisor interrupt delegation register.
     */
    int sideleg = 0x103;
    /**
     * Supervisor interrupt-enable register.
     */
    int sie = 0x104;
    /**
     * Supervisor trap handler base address.
     */
    int stvec = 0x105;
    /**
     * Supervisor counter enable.
     */
    int scounteren = 0x106;

    /**
     * Supervisor environment configuration register.
     */
    int senvcfg = 0x10A;

    /**
     * Supervisor counter-inhibit register.
     */
    int scountinhibit = 0x120;

    /**
     * Supervisor scratch register.
     */
    int sscratch = 0x140;
    /**
     * Supervisor exception program counter.
     */
    int sepc = 0x141;
    /**
     * Supervisor trap cause.
     */
    int scause = 0x142;
    /**
     * Supervisor trap value.
     */
    int stval = 0x143;
    /**
     * Supervisor interrupt pending.
     */
    int sip = 0x144;
    /**
     * Supervisor count overflow.
     */
    int scountovf = 0xDA0;

    /**
     * Supervisor count overflow.
     */
    int siselect = 0x150;
    /**
     * Supervisor indirect register alias.
     */
    int sireg = 0x151;
    /**
     * Supervisor indirect register alias 2.
     */
    int sireg2 = 0x152;
    /**
     * Supervisor indirect register alias 3.
     */
    int sireg3 = 0x153;
    /**
     * Supervisor indirect register alias 4.
     */
    int sireg4 = 0x155;
    /**
     * Supervisor indirect register alias 5.
     */
    int sireg5 = 0x156;
    /**
     * Supervisor indirect register alias 6.
     */
    int sireg6 = 0x157;

    /**
     * Supervisor address translation and protection.
     */
    int satp = 0x180;

    /**
     * Supervisor timer compare.
     */
    int stimecmp = 0x14D;
    /**
     * Upper 32 bits of stimecmp, RV32 only.
     */
    int stimecmph = 0x15D;

    /**
     * Supervisor-mode context register.
     */
    int scontext = 0x5A8;

    /**
     * Supervisor Resource Management Configuration.
     */
    int srmcfg = 0x181;

    /**
     * Supervisor State Enable 0 Register.
     */
    int sstateen0 = 0x10C;
    /**
     * Supervisor State Enable 1 Register.
     */
    int sstateen1 = 0x10D;
    /**
     * Supervisor State Enable 2 Register.
     */
    int sstateen2 = 0x10E;
    /**
     * Supervisor State Enable 3 Register.
     */
    int sstateen3 = 0x10F;

    /**
     * Supervisor Control Transfer Records Control Register.
     */
    int sctrctl = 0x14E;
    /**
     * Supervisor Control Transfer Records Status Register.
     */
    int sctrstatus = 0x14F;
    /**
     * Supervisor Control Transfer Records Depth Register.
     */
    int sctrdepth = 0x15F;

    /**
     * Hypervisor status register.
     */
    int hstatus = 0x600;
    /**
     * Hypervisor exception delegation register.
     */
    int hedeleg = 0x602;
    /**
     * Hypervisor interrupt delegation register.
     */
    int hideleg = 0x603;
    /**
     * Hypervisor interrupt-enable register.
     */
    int hie = 0x604;
    /**
     * Hypervisor counter enable.
     */
    int hcounteren = 0x606;
    /**
     * Hypervisor guest external interrupt-enable register.
     */
    int hgeie = 0x607;
    /**
     * Upper 32 bits of hedeleg, RV32 only.
     */
    int hedelegh = 0x612;

    /**
     * Hypervisor trap value.
     */
    int htval = 0x643;
    /**
     * Hypervisor interrupt pending.
     */
    int hip = 0x644;
    /**
     * Hypervisor virtual interrupt pending.
     */
    int hvip = 0x645;
    /**
     * Hypervisor trap instruction (transformed).
     */
    int htinst = 0x64A;
    /**
     * Hypervisor guest external interrupt pending.
     */
    int hgeip = 0xE12;

    /**
     * Hypervisor environment configuration register.
     */
    int henvcfg = 0x60A;
    /**
     * Upper 32 bits of henvcfg, RV32 only.
     */
    int henvcfgh = 0x61A;

    /**
     * Hypervisor guest address translation and protection.
     */
    int hgatp = 0x680;

    /**
     * Hypervisor-mode context register.
     */
    int hcontext = 0x6A8;

    /**
     * Delta for VS/VU-mode timer.
     */
    int htimedelta = 0x605;
    /**
     * Upper 32 bits of htimedelta, RV32 only.
     */
    int htimedeltah = 0x615;

    /**
     * Hypervisor State Enable 0 Register.
     */
    int hstateen0 = 0x60C;
    /**
     * Hypervisor State Enable 1 Register.
     */
    int hstateen1 = 0x60D;
    /**
     * Hypervisor State Enable 2 Register.
     */
    int hstateen2 = 0x60E;
    /**
     * Hypervisor State Enable 3 Register.
     */
    int hstateen3 = 0x60F;
    /**
     * Upper 32 bits of Hypervisor State Enable 0 Register, RV32 only.
     */
    int hstateen0h = 0x61C;
    /**
     * Upper 32 bits of Hypervisor State Enable 1 Register, RV32 only.
     */
    int hstateen1h = 0x61D;
    /**
     * Upper 32 bits of Hypervisor State Enable 2 Register, RV32 only.
     */
    int hstateen2h = 0x61E;
    /**
     * Upper 32 bits of Hypervisor State Enable 3 Register, RV32 only.
     */
    int hstateen3h = 0x61F;

    /**
     * Virtual supervisor status register.
     */
    int vsstatus = 0x200;
    /**
     * Virtual supervisor interrupt-enable register.
     */
    int vsie = 0x204;
    /**
     * Virtual supervisor trap handler base address.
     */
    int vstvec = 0x205;
    /**
     * Virtual supervisor scratch register.
     */
    int vsscratch = 0x240;
    /**
     * Virtual supervisor exception program counter.
     */
    int vsepc = 0x241;
    /**
     * Virtual supervisor trap cause.
     */
    int vscause = 0x242;
    /**
     * Virtual supervisor trap value.
     */
    int vstval = 0x243;
    /**
     * Virtual supervisor interrupt pending.
     */
    int vsip = 0x244;
    /**
     * Virtual supervisor address translation and protection.
     */
    int vsatp = 0x280;

    /**
     * Virtual supervisor indirect register select.
     */
    int vsiselect = 0x250;
    /**
     * Virtual supervisor indirect register alias.
     */
    int vsireg = 0x251;
    /**
     * Virtual supervisor indirect register alias 2.
     */
    int vsireg2 = 0x252;
    /**
     * Virtual supervisor indirect register alias 3.
     */
    int vsireg3 = 0x253;
    /**
     * Virtual supervisor indirect register alias 4.
     */
    int vsireg4 = 0x255;
    /**
     * Virtual supervisor indirect register alias 5.
     */
    int vsireg5 = 0x256;
    /**
     * Virtual supervisor indirect register alias 6.
     */
    int vsireg6 = 0x257;

    /**
     * Virtual supervisor timer compare.
     */
    int vstimecmp = 0x24D;
    /**
     * Upper 32 bits of vstimecmp, RV32 only.
     */
    int vstimecmph = 0x25D;

    /**
     * Virtual Supervisor Control Transfer Records Control Register.
     */
    int vsctrctl = 0x24E;

    /**
     * Vendor ID.
     */
    int mvendorid = 0xF11;
    /**
     * Architecture ID.
     */
    int marchid = 0xF12;
    /**
     * Implementation ID.
     */
    int mimpid = 0xF13;
    /**
     * Hardware thread ID.
     */
    int mhartid = 0xF14;
    /**
     * Pointer to configuration data structure.
     */
    int mconfigptr = 0xF15;

    /**
     * Machine status register.
     */
    int mstatus = 0x300;
    /**
     * ISA and extensions
     */
    int misa = 0x301;
    /**
     * Machine exception delegation register.
     */
    int medeleg = 0x302;
    /**
     * Machine interrupt delegation register.
     */
    int mideleg = 0x303;
    /**
     * Machine interrupt-enable register.
     */
    int mie = 0x304;
    /**
     * Machine trap-handler base address.
     */
    int mtvec = 0x305;
    /**
     * Machine counter enable.
     */
    int mcounteren = 0x306;
    /**
     * Additional machine status register, RV32 only.
     */
    int mstatush = 0x310;
    /**
     * Upper 32 bits of medeleg, RV32 only.
     */
    int medelegh = 0x12;

    /**
     * Machine cycle counter configuration register.
     */
    int mcyclecfg = 0x321;
    /**
     * Machine instret counter configuration register.
     */
    int minstretcfg = 0x322;
    /**
     * Upper 32 bits of mcyclecfg, RV32 only.
     */
    int mcyclecfgh = 0x721;
    /**
     * Upper 32 bits of minstretcfg, RV32 only.
     */
    int minstretcfgh = 0x722;

    /**
     * Machine scratch register.
     */
    int mscratch = 0x340;
    /**
     * Machine exception program counter.
     */
    int mepc = 0x341;
    /**
     * Machine trap cause.
     */
    int mcause = 0x342;
    /**
     * Machine trap value.
     */
    int mtval = 0x343;
    /**
     * Machine interrupt pending.
     */
    int mip = 0x344;
    /**
     * Machine trap instruction (transformed).
     */
    int mtinst = 0x34A;
    /**
     * Machine second trap value.
     */
    int mtval2 = 0x34B;

    /**
     * Machine indirect register select.
     */
    int miselect = 0x350;
    /**
     * Machine indirect register alias.
     */
    int mireg = 0x351;
    /**
     * Machine indirect register alias 2.
     */
    int mireg2 = 0x352;
    /**
     * Machine indirect register alias 3.
     */
    int mireg3 = 0x353;
    /**
     * Machine indirect register alias 4.
     */
    int mireg4 = 0x355;
    /**
     * Machine indirect register alias 5.
     */
    int mireg5 = 0x356;
    /**
     * Machine indirect register alias 6.
     */
    int mireg6 = 0x357;

    /**
     * Machine environment configuration register.
     */
    int menvcfg = 0x30A;
    /**
     * Upper 32 bits of menvcfg, RV32 only.
     */
    int menvcfgh = 0x31A;
    /**
     * Machine security configuration register.
     */
    int mseccfg = 0x747;
    /**
     * Upper 32 bits of mseccfg, RV32 only.
     */
    int mseccfgh = 0x757;

    /**
     * Physical memory protection configuration.
     */
    int pmpcfg0 = 0x3A0;
    /**
     * Physical memory protection configuration, RV32 only.
     */
    int pmpcfg15 = 0x3AF;
    /**
     * Physical memory protection address register.
     */
    int pmpaddr0 = 0x3B0;
    /**
     * Physical memory protection address register.
     */
    int pmpaddr63 = 0x3EF;

    /**
     * Machine State Enable 0 Register.
     */
    int mstateen0 = 0x30C;
    /**
     * Machine State Enable 1 Register.
     */
    int mstateen1 = 0x30D;
    /**
     * Machine State Enable 2 Register.
     */
    int mstateen2 = 0x30E;
    /**
     * Machine State Enable 3 Register.
     */
    int mstateen3 = 0x30F;
    /**
     * Upper 32 bits of Machine State Enable 0 Register, RV32 only.
     */
    int mstateen0h = 0x31C;
    /**
     * Upper 32 bits of Machine State Enable 1 Register, RV32 only.
     */
    int mstateen1h = 0x31D;
    /**
     * Upper 32 bits of Machine State Enable 2 Register, RV32 only.
     */
    int mstateen2h = 0x31E;
    /**
     * Upper 32 bits of Machine State Enable 3 Register, RV32 only.
     */
    int mstateen3h = 0x31F;

    /**
     * Resumable NMI scratch register.
     */
    int mnscratch = 0x740;
    /**
     * Resumable NMI program counter.
     */
    int mnepc = 0x741;
    /**
     * Resumable NMI cause.
     */
    int mncause = 0x742;
    /**
     * Resumable NMI status.
     */
    int mnstatus = 0x744;

    /**
     * Machine cycle counter.
     */
    int mcycle = 0xB00;
    /**
     * Machine instructions-retired counter.
     */
    int minstret = 0xB02;
    /**
     * Machine performance-monitoring counter.
     */
    int mhpmcounter3 = 0xB03;
    /**
     * Machine performance-monitoring counter.
     */
    int mhpmcounter31 = 0xB1F;
    /**
     * Upper 32 bits of mcycle, RV32 only.
     */
    int mcycleh = 0xB80;
    /**
     * Upper 32 bits of minstret, RV32 only.
     */
    int minstreth = 0xB82;
    /**
     * Upper 32 bits of mhpmcounter3, RV32 only.
     */
    int mhpmcounter3h = 0xB83;
    /**
     * Upper 32 bits of mhpmcounter31, RV32 only.
     */
    int mhpmcounter31h = 0xB9F;

    /**
     * Machine counter-inhibit register.
     */
    int mcountinhibit = 0x320;
    /**
     * Machine performance-monitoring event selector.
     */
    int mhpmevent3 = 0x323;
    /**
     * Machine performance-monitoring event selector.
     */
    int mhpmevent31 = 0x33F;
    /**
     * Upper 32 bits of mhpmevent3, RV32 only.
     */
    int mhpmevent3h = 0x723;
    /**
     * Upper 32 bits of mhpmevent31, RV32 only.
     */
    int mhpmevent31h = 0x73F;

    /**
     * Machine Control Transfer Records Control Register.
     */
    int mctrctl = 0x34E;

    /**
     * Debug/Trace trigger register select.
     */
    int tselect = 0x7A0;
    /**
     * First Debug/Trace trigger data register.
     */
    int tdata1 = 0x7A1;
    /**
     * Second Debug/Trace trigger data register.
     */
    int tdata2 = 0x7A2;
    /**
     * Third Debug/Trace trigger data register.
     */
    int tdata3 = 0x7A3;
    /**
     * Machine-mode context register.
     */
    int mcontext = 0x7A8;

    /**
     * Debug control and status register.
     */
    int dcsr = 0x7B0;
    /**
     * Debug program counter.
     */
    int dpc = 0x7B1;
    /**
     * Debug scratch register 0.
     */
    int dscratch0 = 0x7B2;
    /**
     * Debug scratch register 1.
     */
    int dscratch1 = 0x7B3;

    static boolean readonly(final int addr) {
        return ((addr >>> 10) & 0b11) == 0b11;
    }

    static boolean unprivileged(final int addr, final int priv) {
        return ((addr >>> 8) & 0b11) > priv;
    }
}
