package io.scriptor.impl;

import io.scriptor.fdt.BuilderContext;
import io.scriptor.fdt.NodeBuilder;
import io.scriptor.impl.device.CLINT;
import io.scriptor.isa.Instruction;
import io.scriptor.isa.Registry;
import io.scriptor.machine.*;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;

import static io.scriptor.isa.CSR.*;
import static io.scriptor.machine.Semihosting.*;
import static io.scriptor.util.ByteUtil.signExtend;

public final class HartImpl implements Hart {

    private final Machine machine;

    private final int id;

    private final MMU mmu;

    private final GPRFile gprFile;
    private final FPRFile fprFile;
    private final CSRFile csrFile;

    private long pc;
    private int priv;
    private boolean wfi;
    private boolean semihosting;

    public HartImpl(final @NotNull Machine machine, final int id) {
        this.machine = machine;

        this.id = id;

        this.mmu = new MMU(this);

        this.gprFile = new GPRFileImpl(this);
        this.fprFile = new FPRFileImpl(this);
        this.csrFile = new CSRFileImpl(this);
    }

    private void printStackTrace(final @NotNull PrintStream out) {
        var ra = gprFile.getd(0x1);
        var fp = gprFile.getd(0x8);

        out.printf("stack trace (pc=%016x, ra=%016x, fp=%016x):%n", pc, ra, fp);

        {
            final var symbol = machine.symbols().resolve(pc);
            out.printf(" %016x : %s%n", pc, symbol);
        }

        while (fp != 0L) {
            final var symbol = machine.symbols().resolve(ra);
            out.printf(" %016x : %s%n", ra, symbol);

            final var prev_fp = read(fp, 8, true);
            final var prev_ra = read(fp + 8L, 8, true);

            fp = prev_fp;
            ra = prev_ra;
        }
    }

    @Override
    public @NotNull Machine machine() {
        return machine;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {

        final var instruction = fetch(pc, true);
        out.printf("pc=%016x, instruction=%08x%n", pc, instruction);

        if (Registry.has(64, instruction)) {
            final var definition = Registry.get(64, instruction);

            final var display = new StringBuilder();
            display.append(definition.mnemonic());
            for (final var operand : definition.operands()) {
                display.append(", ")
                       .append(operand.label())
                       .append('=')
                       .append(Integer.toHexString(operand.extract(instruction)));
            }

            out.printf("  %s%n", display);
        }

        out.println("gpr file:");
        gprFile.dump(out);

        out.println("fpr file:");
        fprFile.dump(out);

        out.println("csr file:");
        csrFile.dump(out);

        out.printf("mstatus=%x, mtvec=%x, mcause=%x, mepc=%x%n",
                   csrFile.getd(mstatus, CSR_M),
                   csrFile.getd(mtvec, CSR_M),
                   csrFile.getd(mcause, CSR_M),
                   csrFile.getd(mepc, CSR_M));

        final var sp = gprFile.getd(0x2);
        out.printf("stack (sp=%016x):%n", sp);
        for (long offset = -0x10; offset <= 0x10; offset += 0x8) {
            final var vaddr = sp + offset;
            final var value = read(vaddr, 8, true);

            out.printf("%016x : %016x%n", vaddr, value);
        }

        printStackTrace(out);
    }

    @Override
    public void reset() {
        final var clint = machine.device(CLINT.class);

        gprFile.reset();
        fprFile.reset();
        csrFile.reset();

        pc = 0L;
        priv = CSR_M;
        wfi = false;
        semihosting = false;

        // machine isa

        csrFile.defineVal(misa,
                          1L << 63 // mxl = 64
                          | 1L << 20 // 'U' - user mode implemented
                          | 1L << 18 // 'S' - supervisor mode implemented
                          | 1L << 12 // 'M' - integer multiply/divide extension
                          | 1L << 8 // 'I' - base isa
                          | 1L << 5 // 'F' - single-precision floating-point extension
                          | 1L << 3 // 'D' - double-precision floating-point extension
                          | 1L << 2 // 'C' - compressed extension
                          | 1L // 'A' - atomic extension
        );

        // machine identification

        csrFile.defineVal(mvendorid, 0xCAFEBABEL);
        csrFile.defineVal(marchid, 0x1L);
        csrFile.defineVal(mimpid, 0x1L);
        csrFile.defineVal(mhartid, id & 0xFFFFFFFFL);

        // machine status/control

        csrFile.define(mstatus,
                       0b10000000_00000000_00000110_11111111_00000001_11111111_11111111_11101010L,
                       -1,
                       0b00000000_00000000_00000000_00000000_00000000_00000000_01111000_00000000L);
        csrFile.define(sstatus, 0x84L, mstatus);
        csrFile.define(medeleg, 0xFFFFL);
        csrFile.define(mideleg, 0xFFFFL);

        // machine interrupt control

        csrFile.define(mie, 0x2AAAL);
        csrFile.define(mip, 0x2AAAL,
                       () -> (clint.meip(id) ? 1L : 0L) << 11
                             | (clint.mtip(id) ? 1L : 0L) << 7
                             | (clint.msip(id) ? 1L : 0L) << 3,
                       value -> clint.msip(id, ((value >> 3) & 0b1L) != 0L));
        csrFile.define(sie, 0x2222L, mie);
        csrFile.define(sip, 0x2222L, mip);

        // machine trap handling

        csrFile.define(mtvec);
        csrFile.define(stvec);
        csrFile.define(mepc);
        csrFile.define(sepc);
        csrFile.define(mcause, 0x7FFFFFFFFFFFFFFFL);
        csrFile.define(scause, 0x7FFFFFFFFFFFFFFFL);
        csrFile.define(mtval);
        csrFile.define(stval);
        csrFile.define(mscratch);

        // machine virtual memory

        csrFile.define(satp);

        // machine counters/timers

        csrFile.define(time);
        csrFile.define(cycle);
        csrFile.define(instret);

        // physical memory protection

        for (int pmpcfgx = pmpcfg0; pmpcfgx <= pmpcfg15; ++pmpcfgx) {
            csrFile.define(pmpcfgx, 0xFFL);
        }

        for (int pmpaddrx = pmpaddr0; pmpaddrx <= pmpaddr63; ++pmpaddrx) {
            csrFile.define(pmpaddrx);
        }

        // supervisor exception/interrupt delegation

        csrFile.define(sedeleg);
        csrFile.define(sideleg);
        csrFile.define(sscratch);

        // user status / floating-point

        csrFile.define(fflags, 0x1FL);
        csrFile.define(frm, 0x7L);
        csrFile.define(fcsr, 0xFFL);

        // debug registers

        csrFile.define(dcsr);
        csrFile.define(dpc);
        csrFile.define(dscratch0);
        csrFile.define(dscratch1);

        // machine hardware performance counters

        for (int mhpmcounterx = mhpmcounter3; mhpmcounterx <= mhpmcounter31; ++mhpmcounterx) {
            csrFile.define(mhpmcounterx);
        }

        for (int mhpmeventx = mhpmevent3; mhpmeventx <= mhpmevent31; ++mhpmeventx) {
            csrFile.define(mhpmeventx);
        }

        // clint control/status

        csrFile.define(time, -1L, clint::mtime);
        csrFile.define(stimecmp, -1L, () -> clint.mtimecmp(id), val -> clint.mtimecmp(id, val));

        csrFile.define(mcounteren, 0xFFFFFFFFL, -1, 0xFFFFFFFFL);
        csrFile.define(mcountinhibit, 0xFFFFFFFFL, -1, 0L);
        csrFile.define(mcyclecfg);

        csrFile.define(scounteren, 0xFFFFFFFFL, -1, 0xFFFFFFFFL);
        csrFile.define(scountinhibit, 0xFFFFFFFFL, -1, 0L);
        csrFile.define(scountovf, 0xFFFFFFFFL, -1, 0L);

        csrFile.define(menvcfg);
        csrFile.define(senvcfg);

        csrFile.define(tselect);

        csrFile.define(mstateen0);
        csrFile.define(mstateen1);
        csrFile.define(mstateen2);
        csrFile.define(mstateen3);

        csrFile.define(sstateen0);
        csrFile.define(sstateen1);
        csrFile.define(sstateen2);
        csrFile.define(sstateen3);
    }

    @Override
    public void step() {
        try {
            interrupt();

            final var instruction = fetch(pc, false);
            if (!Registry.has(64, instruction))
                unsupported(Integer.toHexString(instruction));

            final var definition = Registry.get(64, instruction);
            pc = execute(instruction, definition);
        } catch (final TrapException e) {
            if (handle(e.getTrapCause(), e.getTrapValue())) {
                if (e.getId() < 0)
                    throw new TrapException(id, e);
                throw e;
            }
        }
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.get(this);

        builder.name("cpu@%d".formatted(id))
               .prop(pb -> pb.name("device_type").data("cpu"))
               .prop(pb -> pb.name("reg").data(id))
               .prop(pb -> pb.name("status").data("okay"))
               .prop(pb -> pb.name("compatible").data("riscv"))
               .prop(pb -> pb.name("riscv,isa").data("rv64imafdqc_zifencei_zicsr"))
               .prop(pb -> pb.name("riscv,mmu-type").data("riscv,sv39,sv48,sv57"))
               .node(nb -> nb
                       .name("interrupt-controller")
                       .prop(pb -> pb.name("phandle").data(phandle))
                       .prop(pb -> pb.name("#interrupt-cells").data(0x01))
                       .prop(pb -> pb.name("compatible").data("riscv,cpu-intc"))
                       .node(nb1 -> nb1.name("interrupt-controller"))
               );
    }

    private boolean handle(final long cause, final long tval) {
        final var interrupt = (cause & (1L << 63)) != 0L;
        final var code      = (int) (cause & 0x7FFFFFFFL);

        final var delegate = interrupt
                             ? ((csrFile.getd(mideleg, CSR_M) & (1L << code)) != 0L)
                             : ((csrFile.getd(medeleg, CSR_M) & (1L << code)) != 0L);

        final var target = (priv < CSR_M && delegate) ? CSR_S : CSR_M;

        var status = csrFile.getd(mstatus, CSR_M);
        if (target == CSR_M) {
            csrFile.putd(mepc, CSR_M, pc);
            csrFile.putd(mcause, CSR_M, cause);
            csrFile.putd(mtval, CSR_M, tval);

            status = (status & ~(1L << 7)) | (((status >> 3) & 1L) << 7);
            status &= ~(1L << 3);
            status = (status & ~(3L << 11)) | ((long) priv << 11);
        } else {
            csrFile.putd(sepc, CSR_S, pc);
            csrFile.putd(scause, CSR_S, cause);
            csrFile.putd(stval, CSR_S, tval);

            status = (status & ~(1L << 5)) | (((status >> 1) & 1L) << 5);
            status &= ~(1L << 1);
            status = (status & ~(1L << 8)) | ((long) priv << 8);
        }
        csrFile.putd(mstatus, CSR_M, status);

        final var tvec = target == CSR_M
                         ? csrFile.getd(mtvec, CSR_M)
                         : csrFile.getd(stvec, CSR_S);
        if (tvec == 0L)
            return true;

        final var base = tvec & ~0b11L;
        final var mode = tvec & 0b11L;

        Log.info("TRAP: priv=%d satp=%016x stvec=%016x pc=%016x",
                 priv,
                 csrFile.getd(satp, CSR_S),
                 csrFile.getd(stvec, CSR_S),
                 pc);

        pc = (interrupt && mode == 0b01)
             ? base + 4L * code
             : base;

        priv = target;
        return false;
    }

    private void interrupt() {
        final var status = csrFile.getd(mstatus, CSR_M);

        switch (priv) {
            case CSR_M -> {
                if (((status >> 3) & 1L) == 0L)
                    return;
            }
            case CSR_S -> {
                if (((status >> 1) & 1L) == 0L)
                    return;
            }
            default -> {
                return;
            }
        }

        // check if any interrupts are pending and enabled
        final var pending = csrFile.getd(mip, CSR_M) & csrFile.getd(mie, CSR_M);
        if (pending == 0L)
            return;

        final var code = Long.numberOfTrailingZeros(pending);

        final var delegate = priv < CSR_M && ((csrFile.getd(mideleg, CSR_M) & (1L << code)) != 0L);
        final var target   = delegate ? CSR_S : CSR_M;

        if (target < priv)
            return;

        throw new TrapException(id, (1L << 63) | code, 0L, "");
    }

    private final int[] values = new int[5];

    @Override
    public long execute(final int instruction, final @NotNull Instruction definition) {
        if (wfi) {
            return pc;
        }

        var next = pc + definition.ilen();

        switch (definition.mnemonic()) {
            //region PRIVILEGED

            case "sret" -> next = sret();
            case "mret" -> next = mret();
            case "mnret" -> mnret();

            case "wfi" -> wfi();

            case "sctrclr" -> sctrclr();

            case "sfence.vma" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                sfence_vma(values[0], values[1]);
            }

            case "hfence.vvma" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hfence_vvma(values[0], values[1]);
            }
            case "hfence.gvma" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hfence_gvma(values[0], values[1]);
            }

            case "hlv.b" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_b(values[0], values[1]);
            }
            case "hlv.bu" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_bu(values[0], values[1]);
            }
            case "hlv.h" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_h(values[0], values[1]);
            }
            case "hlv.hu" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_hu(values[0], values[1]);
            }
            case "hlv.w" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_w(values[0], values[1]);
            }
            case "hlvx.hu" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlvx_hu(values[0], values[1]);
            }
            case "hlvx.wu" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlvx_wu(values[0], values[1]);
            }
            case "hsv.b" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hsv_b(values[0], values[1]);
            }
            case "hsv.h" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hsv_h(values[0], values[1]);
            }
            case "hsv.w" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hsv_w(values[0], values[1]);
            }

            case "hlv.wu" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_wu(values[0], values[1]);
            }
            case "hlv.d" -> {
                definition.decode(instruction, values, "rd", "rs1");
                hlv_d(values[0], values[1]);
            }
            case "hsv.d" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hsv_d(values[0], values[1]);
            }

            case "sinval.vma" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                sinval_vma(values[0], values[1]);
            }
            case "sfence.w.inval" -> sfence_w_inval();
            case "sfence.inval.ir" -> sfence_inval_ir();
            case "hinval.vvma" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hinval_vvma(values[0], values[1]);
            }
            case "hinval.gvma" -> {
                definition.decode(instruction, values, "rs1", "rs2");
                hinval_gvma(values[0], values[1]);
            }

            //endregion

            //region RV32 ATOMIC

            case "lr.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                lr_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "sc.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                sc_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoswap.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoswap_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoadd.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoadd_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoxor.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoxor_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoand.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoand_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoor.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoor_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amomin.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amomin_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amomax.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amomax_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amominu.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amominu_w(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amomaxu.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amomaxu_w(values[0], values[1], values[2], values[3], values[4]);
            }

            //endregion

            //region RV32 SINGLE-PRECISION FLOATING-POINT

            case "flw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                flw(values[0], values[1], values[2]);
            }

            case "fsw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                fsw(values[0], values[1], values[2]);
            }

            case "fmadd.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm");
                fmadd_s(values[0], values[1], values[2], values[3], values[4]);
            }
            case "fmsub.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm");
                fmsub_s(values[0], values[1], values[2], values[3], values[4]);
            }
            case "fnmsub.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm");
                fnmsub_s(values[0], values[1], values[2], values[3], values[4]);
            }
            case "fnmadd.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rs3", "rm");
                fnmadd_s(values[0], values[1], values[2], values[3], values[4]);
            }

            case "fadd.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fadd_s(values[0], values[1], values[2], values[3]);
            }
            case "fsub.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fsub_s(values[0], values[1], values[2], values[3]);
            }
            case "fmul.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fmul_s(values[0], values[1], values[2], values[3]);
            }
            case "fdiv.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fdiv_s(values[0], values[1], values[2], values[3]);
            }
            case "fsqrt.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fsqrt_s(values[0], values[1], values[2], values[3]);
            }

            case "fsgnj.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fsgnj_s(values[0], values[1], values[2]);
            }
            case "fsgnjn.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fsgnjn_s(values[0], values[1], values[2]);
            }
            case "fsgnjx.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fsgnjx_s(values[0], values[1], values[2]);
            }
            case "fmin.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fmin_s(values[0], values[1], values[2]);
            }
            case "fmax.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fmax_s(values[0], values[1], values[2]);
            }

            case "fcvt.w.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_w_s(values[0], values[1], values[2], values[3]);
            }
            case "fcvt.wu.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_wu_s(values[0], values[1], values[2], values[3]);
            }
            case "fmv.x.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fmv_x_w(values[0], values[1], values[2]);
            }

            case "feq.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                feq_s(values[0], values[1], values[2]);
            }
            case "flt.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                flt_s(values[0], values[1], values[2]);
            }
            case "fge.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fge_s(values[0], values[1], values[2]);
            }

            case "fclass.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fclass_s(values[0], values[1], values[2]);
            }
            case "fcvt.s.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_s_w(values[0], values[1], values[2], values[3]);
            }
            case "fcvt.s.wu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_s_wu(values[0], values[1], values[2], values[3]);
            }
            case "fmv.w.x" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                fmv_w_x(values[0], values[1], values[2]);
            }

            //endregion

            //region RV32 INTEGER

            case "lui" -> {
                definition.decode(instruction, values, "rd", "imm");
                lui(values[0], values[1]);
            }
            case "auipc" -> {
                definition.decode(instruction, values, "rd", "imm");
                auipc(values[0], values[1]);
            }

            case "jal" -> {
                definition.decode(instruction, values, "rd", "imm");
                next = jal(next, values[0], values[1]);
            }

            case "jalr" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                next = jalr(next, values[0], values[1], values[2]);
            }

            case "beq" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                next = beq(next, values[0], values[1], values[2]);
            }
            case "bne" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                next = bne(next, values[0], values[1], values[2]);
            }
            case "blt" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                next = blt(next, values[0], values[1], values[2]);
            }
            case "bge" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                next = bge(next, values[0], values[1], values[2]);
            }
            case "bltu" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                next = bltu(next, values[0], values[1], values[2]);
            }
            case "bgeu" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                next = bgeu(next, values[0], values[1], values[2]);
            }

            case "lb" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                lb(values[0], values[1], values[2]);
            }
            case "lh" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                lh(values[0], values[1], values[2]);
            }
            case "lw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                lw(values[0], values[1], values[2]);
            }
            case "lbu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                lbu(values[0], values[1], values[2]);
            }
            case "lhu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                lhu(values[0], values[1], values[2]);
            }

            case "sb" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                sb(values[0], values[1], values[2]);
            }
            case "sh" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                sh(values[0], values[1], values[2]);
            }
            case "sw" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                sw(values[0], values[1], values[2]);
            }

            case "addi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                addi(values[0], values[1], values[2]);
            }
            case "slti" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                slti(values[0], values[1], values[2]);
            }
            case "sltiu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                sltiu(values[0], values[1], values[2]);
            }
            case "xori" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                xori(values[0], values[1], values[2]);
            }
            case "ori" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                ori(values[0], values[1], values[2]);
            }
            case "andi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                andi(values[0], values[1], values[2]);
            }

            case "slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                slli(values[0], values[1], values[2]);
            }
            case "srli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                srli(values[0], values[1], values[2]);
            }
            case "srai" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                srai(values[0], values[1], values[2]);
            }

            case "add" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                add(values[0], values[1], values[2]);
            }
            case "sub" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                sub(values[0], values[1], values[2]);
            }
            case "sll" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                sll(values[0], values[1], values[2]);
            }
            case "slt" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                slt(values[0], values[1], values[2]);
            }
            case "sltu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                sltu(values[0], values[1], values[2]);
            }
            case "xor" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                xor(values[0], values[1], values[2]);
            }
            case "srl" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                srl(values[0], values[1], values[2]);
            }
            case "sra" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                sra(values[0], values[1], values[2]);
            }
            case "or" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                or(values[0], values[1], values[2]);
            }
            case "and" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                and(values[0], values[1], values[2]);
            }

            case "fence" -> {
                definition.decode(instruction, values, "rd", "rs1", "fm", "pred", "succ");
                fence(values[0], values[1], values[2], values[3], values[4]);
            }
            case "fence_tso" -> fence_tso();
            case "pause" -> pause();
            case "ecall" -> ecall();
            case "ebreak" -> next = ebreak(next);

            //endregion

            //region RV32 MULTIPLY

            case "mul" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                mul(values[0], values[1], values[2]);
            }
            case "mulh" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                mulh(values[0], values[1], values[2]);
            }
            case "mulhsu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                mulhsu(values[0], values[1], values[2]);
            }
            case "mulhu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                mulhu(values[0], values[1], values[2]);
            }
            case "div" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                div(values[0], values[1], values[2]);
            }
            case "divu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                divu(values[0], values[1], values[2]);
            }
            case "rem" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                rem(values[0], values[1], values[2]);
            }
            case "remu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                remu(values[0], values[1], values[2]);
            }

            //endregion

            //region RV64 ATOMIC

            case "lr.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                lr_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "sc.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                sc_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoswap.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoswap_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoadd.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoadd_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoxor.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoxor_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoand.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoand_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amoor.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amoor_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amomin.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amomin_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amomax.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amomax_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amominu.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amominu_d(values[0], values[1], values[2], values[3], values[4]);
            }
            case "amomaxu.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "aq", "rl");
                amomaxu_d(values[0], values[1], values[2], values[3], values[4]);
            }

            //endregion

            //region RV64 SINGLE-PRECISION FLOATING-POINT

            case "fcvt.l.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_l_s(values[0], values[1], values[2], values[3]);
            }
            case "fcvt.lu.s" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_lu_s(values[0], values[1], values[2], values[3]);
            }
            case "fcvt.s.l" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_s_l(values[0], values[1], values[2], values[3]);
            }
            case "fcvt.s.lu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2", "rm");
                fcvt_s_lu(values[0], values[1], values[2], values[3]);
            }

            // endregion

            //region RV64 INTEGER

            case "lwu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                lwu(values[0], values[1], values[2]);
            }
            case "ld" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                ld(values[0], values[1], values[2]);
            }
            case "sd" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");
                sd(values[0], values[1], values[2]);
            }
            case "addiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                addiw(values[0], values[1], values[2]);
            }
            case "slliw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                slliw(values[0], values[1], values[2]);
            }
            case "srliw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                srliw(values[0], values[1], values[2]);
            }
            case "sraiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                sraiw(values[0], values[1], values[2]);
            }
            case "addw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                addw(values[0], values[1], values[2]);
            }
            case "subw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                subw(values[0], values[1], values[2]);
            }
            case "sllw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                sllw(values[0], values[1], values[2]);
            }
            case "srlw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                srlw(values[0], values[1], values[2]);
            }
            case "sraw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                sraw(values[0], values[1], values[2]);
            }

            //endregion

            //region RV64 MULTIPLY

            case "mulw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                mulw(values[0], values[1], values[2]);
            }
            case "divw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                divw(values[0], values[1], values[2]);
            }
            case "divuw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                divuw(values[0], values[1], values[2]);
            }
            case "remw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                remw(values[0], values[1], values[2]);
            }
            case "remuw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                remuw(values[0], values[1], values[2]);
            }

            //endregion

            //region COMPRESSED

            case "c.addi4spn" -> {
                definition.decode(instruction, values, "rdp", "uimm");
                c_addi4spn(values[0] + 0x8, values[1]);
            }
            case "c.fld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");
                c_fld(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.lw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");
                c_lw(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.ld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");
                c_ld(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.fsd" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");
                c_fsd(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.sw" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");
                c_sw(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.sd" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");
                c_sd(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.nop" -> c_nop();
            case "c.addi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                c_addi(values[0], values[1], values[2]);
            }
            case "c.addiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                c_addiw(values[0], values[1], values[2]);
            }
            case "c.li" -> {
                definition.decode(instruction, values, "rd", "imm");
                c_li(values[0], values[1]);
            }
            case "c.addi16sp" -> {
                definition.decode(instruction, values, "imm");
                c_addi16sp(values[0]);
            }
            case "c.lui" -> {
                definition.decode(instruction, values, "rd", "imm");
                c_lui(values[0], values[1]);
            }
            case "c.srli" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "shamt");
                c_srli(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.srai" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "shamt");
                c_srai(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.andi" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");
                c_andi(values[0] + 0x8, values[1] + 0x8, values[2]);
            }
            case "c.sub" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");
                c_sub(values[0] + 0x8, values[1] + 0x8, values[2] + 0x8);
            }
            case "c.xor" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");
                c_xor(values[0] + 0x8, values[1] + 0x8, values[2] + 0x8);
            }
            case "c.or" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");
                c_or(values[0] + 0x8, values[1] + 0x8, values[2] + 0x8);
            }
            case "c.and" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");
                c_and(values[0] + 0x8, values[1] + 0x8, values[2] + 0x8);
            }
            case "c.subw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");
                c_subw(values[0] + 0x8, values[1] + 0x8, values[2] + 0x8);
            }
            case "c.addw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");
                c_addw(values[0] + 0x8, values[1] + 0x8, values[2] + 0x8);
            }
            case "c.j" -> {
                definition.decode(instruction, values, "imm");
                next = c_j(values[0]);
            }
            case "c.beqz" -> {
                definition.decode(instruction, values, "rs1p", "imm");
                next = c_beqz(next, values[0] + 0x8, values[1]);
            }
            case "c.bnez" -> {
                definition.decode(instruction, values, "rs1p", "imm");
                next = c_bnez(next, values[0] + 0x8, values[1]);
            }
            case "c.slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");
                c_slli(values[0], values[1], values[2]);
            }
            case "c.fldsp" -> {
                definition.decode(instruction, values, "rd", "uimm");
                c_fldsp(values[0], values[1]);
            }
            case "c.lwsp" -> {
                definition.decode(instruction, values, "rd", "uimm");
                c_lwsp(values[0], values[1]);
            }
            case "c.ldsp" -> {
                definition.decode(instruction, values, "rd", "uimm");
                c_ldsp(values[0], values[1]);
            }
            case "c.jr" -> {
                definition.decode(instruction, values, "rs1");
                next = c_jr(values[0]);
            }
            case "c.mv" -> {
                definition.decode(instruction, values, "rd", "rs2");
                c_mv(values[0], values[1]);
            }
            case "c.ebreak" -> next = c_ebreak();
            case "c.jalr" -> {
                definition.decode(instruction, values, "rs1");
                next = c_jalr(next, values[0]);
            }
            case "c.add" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");
                c_add(values[0], values[1], values[2]);
            }
            case "c.fsdsp" -> {
                definition.decode(instruction, values, "rs2", "uimm");
                c_fsdsp(values[0], values[1]);
            }
            case "c.swsp" -> {
                definition.decode(instruction, values, "rs2", "uimm");
                c_swsp(values[0], values[1]);
            }
            case "c.sdsp" -> {
                definition.decode(instruction, values, "rs2", "uimm");
                c_sdsp(values[0], values[1]);
            }

            //endregion

            //region CSR

            case "csrrw" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr");
                csrrw(values[0], values[1], values[2]);
            }
            case "csrrs" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr");
                csrrs(values[0], values[1], values[2]);
            }
            case "csrrc" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr");
                csrrc(values[0], values[1], values[2]);
            }
            case "csrrwi" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr");
                csrrwi(values[0], values[1], values[2]);
            }
            case "csrrsi" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr");
                csrrsi(values[0], values[1], values[2]);
            }
            case "csrrci" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr");
                csrrci(values[0], values[1], values[2]);
            }

            //endregion

            //region EXTENSION - FENCE.I

            case "fence.i" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");
                fence_i(values[0], values[1], values[2]);
            }

            //endregion

            default -> throw new TrapException(id, 0x02L, instruction, "unsupported instruction %s", definition);
        }

        return next;
    }

    @Override
    public boolean sleeping() {
        return wfi;
    }

    @Override
    public void wake() {
        wfi = false;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public long pc() {
        return pc;
    }

    @Override
    public void pc(final long pc) {
        this.pc = pc;
    }

    @Override
    public int privilege() {
        return priv;
    }

    @Override
    public @NotNull GPRFile gprFile() {
        return gprFile;
    }

    @Override
    public @NotNull FPRFile fprFile() {
        return fprFile;
    }

    @Override
    public @NotNull CSRFile csrFile() {
        return csrFile;
    }

    @Override
    public long translate(final long vaddr, @NotNull MMU.Access access, boolean unsafe) {
        return mmu.translate(priv, vaddr, access, unsafe);
    }

    @Override
    public void close() throws Exception {
        gprFile.close();
        fprFile.close();
        csrFile.close();
    }

    private void unsupported(final @NotNull String name) {
        throw new TrapException(id, 0x02L, 0x00L, "unsupported instruction %s", name);
    }

    //region PRIVILEGED

    private long sret() {
        var status = csrFile.getd(sstatus, priv);

        final var spp  = (int) ((status >> 8) & 0b1L);
        final var spie = (status & (1L << 5)) != 0L;

        priv = spp;

        status &= ~(1L << 1);
        status |= (spie ? (1L << 1) : 0L);
        status |= (1L << 8);
        status |= (1L << 5);

        csrFile.putd(sstatus, CSR_M, status);

        return csrFile.getd(sepc, CSR_M);
    }

    private long mret() {
        var status = csrFile.getd(mstatus, priv);

        final var mpp  = (int) ((status >> 11) & 0b11L);
        final var mpie = (status & (1L << 7)) != 0L;

        priv = mpp;

        status &= ~(1L << 3);
        status |= (mpie ? (1L << 3) : 0L);
        status |= (0b11L << 11);
        status |= (1L << 7);

        csrFile.putd(mstatus, CSR_M, status);

        return csrFile.getd(mepc, CSR_M);
    }

    private void mnret() {
        unsupported("mnret");
    }

    private void wfi() {
        wfi = true;
    }

    private void sctrclr() {
        unsupported("sctrclr");
    }

    private void sfence_vma(final int rs1, final int rs2) {
        final var vaddr = gprFile.getd(rs1);
        final var asid  = gprFile.getd(rs2);

        mmu.flush(vaddr, asid);
    }

    private void hfence_vvma(final int rs1, final int rs2) {
        unsupported("hfence.vvma");
    }

    private void hfence_gvma(final int rs1, final int rs2) {
        unsupported("hfence.gvma");
    }

    private void hlv_b(final int rd, final int rs1) {
        unsupported("hlv.b");
    }

    private void hlv_bu(final int rd, final int rs1) {
        unsupported("hlv.bu");
    }

    private void hlv_h(final int rd, final int rs1) {
        unsupported("hlv.h");
    }

    private void hlv_hu(final int rd, final int rs1) {
        unsupported("hlv.hu");
    }

    private void hlv_w(final int rd, final int rs1) {
        unsupported("hlv.w");
    }

    private void hlvx_hu(final int rd, final int rs1) {
        unsupported("hlvx.hu");
    }

    private void hlvx_wu(final int rd, final int rs1) {
        unsupported("hlvx.wu");
    }

    private void hsv_b(final int rs1, final int rs2) {
        unsupported("hsv.b");
    }

    private void hsv_h(final int rs1, final int rs2) {
        unsupported("hsv.h");
    }

    private void hsv_w(final int rs1, final int rs2) {
        unsupported("hsv.w");
    }

    private void hlv_wu(final int rd, final int rs1) {
        unsupported("hlv.wu");
    }

    private void hlv_d(final int rd, final int rs1) {
        unsupported("hlv.d");
    }

    private void hsv_d(final int rs1, final int rs2) {
        unsupported("hsv.d");
    }

    private void sinval_vma(final int rs1, final int rs2) {
        unsupported("sinval.vma");
    }

    private void sfence_w_inval() {
        unsupported("sfence.w.inval");
    }

    private void sfence_inval_ir() {
        unsupported("sfence.inval.ir");
    }

    private void hinval_vvma(final int rs1, final int rs2) {
        unsupported("hinval.vvma");
    }

    private void hinval_gvma(final int rs1, final int rs2) {
        unsupported("hinval.gvma");
    }

    //endregion

    //region RV32 ATOMIC

    private void lr_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var value = lw(gprFile.getd(rs1));
        gprFile.putd(rd, value);

        // TODO: reservation
    }

    private void sc_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        sw(gprFile.getd(rs1), gprFile.getw(rs2));
        gprFile.putd(rd, 0L);

        // TODO: reservation
    }

    private void amoswap_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var vaddr = gprFile.getd(rs1);
        if ((vaddr & 0x3L) != 0L) {
            throw new TrapException(id, 0x06L, vaddr, "misaligned atomic address %x", vaddr);
        }

        final int value;
        synchronized (machine.acquireLock(vaddr)) {
            final var source = gprFile.getw(rs2);
            value = (int) lw(vaddr);
            sw(vaddr, source);
        }

        gprFile.putw(rd, value);
    }

    private void amoadd_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var vaddr = gprFile.getd(rs1);
        if ((vaddr & 0x3L) != 0L) {
            throw new TrapException(id, 0x06L, vaddr, "misaligned atomic address %x", vaddr);
        }

        final int value;
        synchronized (machine.acquireLock(vaddr)) {
            final var source = gprFile.getw(rs2);
            value = (int) lw(vaddr);
            sw(vaddr, value + source);
        }

        gprFile.putw(rd, value);
    }

    private void amoxor_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amoxor.w");
    }

    private void amoand_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amoand.w");
    }

    private void amoor_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amoor.w");
    }

    private void amomin_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amomin.w");
    }

    private void amomax_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amomax.w");
    }

    private void amominu_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amominu.w");
    }

    private void amomaxu_w(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amomaxu.w");
    }

    //endregion

    //region RV32 SINGLE-PRECISION FLOATING-POINT

    private void flw(final int rd, final int rs1, final int imm) {
        unsupported("flw");
    }

    private void fsw(final int rs1, final int rs2, final int imm) {
        unsupported("fsw");
    }

    private void fmadd_s(final int rd, final int rs1, final int rs2, final int rs3, final int rm) {
        unsupported("fmadd.s");
    }

    private void fmsub_s(final int rd, final int rs1, final int rs2, final int rs3, final int rm) {
        unsupported("fmsub.s");
    }

    private void fnmsub_s(final int rd, final int rs1, final int rs2, final int rs3, final int rm) {
        unsupported("fnmsub.s");
    }

    private void fnmadd_s(final int rd, final int rs1, final int rs2, final int rs3, final int rm) {
        unsupported("fnmadd.s");
    }

    private void fadd_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fadd.s");
    }

    private void fsub_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fsub.s");
    }

    private void fmul_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fmul.s");
    }

    private void fdiv_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fdiv.s");
    }

    private void fsqrt_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fsqrt.s");
    }

    private void fsgnj_s(final int rd, final int rs1, final int rs2) {
        unsupported("fsgnj.s");
    }

    private void fsgnjn_s(final int rd, final int rs1, final int rs2) {
        unsupported("fsgnjn.s");
    }

    private void fsgnjx_s(final int rd, final int rs1, final int rs2) {
        unsupported("fsgnjx.s");
    }

    private void fmin_s(final int rd, final int rs1, final int rs2) {
        unsupported("fmin.s");
    }

    private void fmax_s(final int rd, final int rs1, final int rs2) {
        unsupported("fmax.s");
    }

    private void fcvt_w_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.w.s");
    }

    private void fcvt_wu_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.wu.s");
    }

    private void fmv_x_w(final int rd, final int rs1, final int rs2) {
        gprFile.putw(rd, fprFile.getfr(rs1));

        // TODO: what about rs2?
    }

    private void feq_s(final int rd, final int rs1, final int rs2) {
        unsupported("feq.s");
    }

    private void flt_s(final int rd, final int rs1, final int rs2) {
        unsupported("flt.s");
    }

    private void fge_s(final int rd, final int rs1, final int rs2) {
        unsupported("fge.s");
    }

    private void fclass_s(final int rd, final int rs1, final int rs2) {
        unsupported("fclass.s");
    }

    private void fcvt_s_w(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.s.w");
    }

    private void fcvt_s_wu(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.s.wu");
    }

    private void fmv_w_x(final int rd, final int rs1, final int rs2) {
        fprFile.putfr(rd, gprFile.getw(rs1));

        // TODO: what about rs2?
    }

    //endregion

    //region RV32 INTEGER

    private void lui(final int rd, final int imm) {
        gprFile.putd(rd, imm);
    }

    private void auipc(final int rd, final int imm) {
        gprFile.putd(rd, pc + imm);
    }

    private long jal(final long next, final int rd, final int imm) {
        final var pnext = pc + signExtend(imm, 21);
        gprFile.putd(rd, next);
        return pnext;
    }

    private long jalr(final long next, final int rd, final int rs1, final int imm) {
        final var pnext = (gprFile.getd(rs1) + signExtend(imm, 12)) & ~1L;
        gprFile.putd(rd, next);
        return pnext;
    }

    private long beq(final long next, final int rs1, final int rs2, final int imm) {
        if (gprFile.getd(rs1) == gprFile.getd(rs2)) {
            return pc + signExtend(imm, 13);
        }
        return next;
    }

    private long bne(final long next, final int rs1, final int rs2, final int imm) {
        if (gprFile.getd(rs1) != gprFile.getd(rs2)) {
            return pc + signExtend(imm, 13);
        }
        return next;
    }

    private long blt(final long next, final int rs1, final int rs2, final int imm) {
        if (gprFile.getd(rs1) < gprFile.getd(rs2)) {
            return pc + signExtend(imm, 13);
        }
        return next;
    }

    private long bge(final long next, final int rs1, final int rs2, final int imm) {
        if (gprFile.getd(rs1) >= gprFile.getd(rs2)) {
            return pc + signExtend(imm, 13);
        }
        return next;
    }

    private long bltu(final long next, final int rs1, final int rs2, final int imm) {
        if (Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) < 0) {
            return pc + signExtend(imm, 13);
        }
        return next;
    }

    private long bgeu(final long next, final int rs1, final int rs2, final int imm) {
        if (Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) >= 0) {
            return pc + signExtend(imm, 13);
        }
        return next;
    }

    private void lb(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, lb(vaddr));
    }

    private void lh(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, lh(vaddr));
    }

    private void lw(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, lw(vaddr));
    }

    private void lbu(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, lbu(vaddr));
    }

    private void lhu(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, lhu(vaddr));
    }

    private void sb(final int rs1, final int rs2, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        sb(vaddr, (byte) gprFile.getd(rs2));
    }

    private void sh(final int rs1, final int rs2, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        sh(vaddr, (short) gprFile.getd(rs2));
    }

    private void sw(final int rs1, final int rs2, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        sw(vaddr, gprFile.getw(rs2));
    }

    private void addi(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, gprFile.getd(rs1) + signExtend(imm, 12));
    }

    private void slti(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, gprFile.getd(rs1) < signExtend(imm, 12) ? 1L : 0L);
    }

    private void sltiu(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, Long.compareUnsigned(gprFile.getd(rs1), signExtend(imm, 12)) < 0 ? 1L : 0L);
    }

    private void xori(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, gprFile.getd(rs1) ^ signExtend(imm, 12));
    }

    private void ori(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, gprFile.getd(rs1) | signExtend(imm, 12));
    }

    private void andi(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, gprFile.getd(rs1) & signExtend(imm, 12));
    }

    private void slli(final int rd, final int rs1, final int shamt) {
        if (rd == 0 && rs1 == 0 && shamt == 0x1F) {
            semihosting = true;
            return;
        }

        gprFile.putd(rd, gprFile.getd(rs1) << shamt);
    }

    private void srli(final int rd, final int rs1, final int shamt) {
        gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
    }

    private void srai(final int rd, final int rs1, final int shamt) {
        if (rd == 0 && rs1 == 0 && shamt == 0x07) {
            semihosting = false;
            return;
        }

        gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
    }

    private void add(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) + gprFile.getd(rs2));
    }

    private void sub(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) - gprFile.getd(rs2));
    }

    private void sll(final int rd, final int rs1, final int rs2) {
        final var shamt = gprFile.getd(rs2) & 0b111111;
        gprFile.putd(rd, gprFile.getd(rs1) << shamt);
    }

    private void slt(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) < gprFile.getd(rs2) ? 1L : 0L);
    }

    private void sltu(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) < 0 ? 1L : 0L);
    }

    private void xor(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) ^ gprFile.getd(rs2));
    }

    private void srl(final int rd, final int rs1, final int rs2) {
        final var shamt = gprFile.getd(rs2) & 0b111111;
        gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
    }

    private void sra(final int rd, final int rs1, final int rs2) {
        final var shamt = gprFile.getd(rs2) & 0b111111;
        gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
    }

    private void or(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) | gprFile.getd(rs2));
    }

    private void and(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) & gprFile.getd(rs2));
    }

    private void fence(final int rd, final int rs1, final int fm, final int pred, final int succ) {
        // TODO: instruction and io cache
    }

    private void fence_tso() {
        // TODO: instruction and io cache
    }

    private void pause() {
        // TODO: pause execution for a few steps
    }

    private void ecall() {
        switch (priv) {
            case CSR_M -> throw new TrapException(id, 0x0BL, 0, "environment call from machine mode");
            case CSR_S -> throw new TrapException(id, 0x09L, 0, "environment call from supervisor mode");
            case CSR_U -> throw new TrapException(id, 0x08L, 0, "environment call from user mode");
            default -> throw new IllegalStateException();
        }
    }

    private long ebreak(final long next) {
        if (semihosting) {
            final var sysnum = gprFile.getd(0x0A);
            final var params = gprFile.getd(0x0B);

            switch ((int) sysnum) {
                case SEMIHOSTING_SYSOPEN -> {
                    // open file
                    final var fname = lstring(ld(params));
                    final var mode  = ld(params + 0x08L);
                    final var len   = ld(params + 0x10L);

                    gprFile.putd(0x0A, fopen(machine, fname, mode, len));
                }
                case SEMIHOSTING_SYSWRITEC -> {
                    // write character stdout
                    final var ch = lb(params);

                    System.out.write((byte) ch);
                }
                case SEMIHOSTING_SYSWRITE -> {
                    // write buffer fd
                    final var fd   = ld(params);
                    final var memp = ld(params + 0x08L);
                    final var len  = ld(params + 0x16L);

                    gprFile.putd(0x0A, fwrite(machine, fd, memp, len));
                }
                case SEMIHOSTING_SYSREAD -> {
                    // read buffer fd
                    final var fd   = ld(params);
                    final var memp = ld(params + 0x08L);
                    final var len  = ld(params + 0x16L);

                    gprFile.putd(0x0A, fread(machine, fd, memp, len));
                }
                case SEMIHOSTING_SYSREADC -> {
                    // read character stdin
                    try {
                        if (System.in.available() > 0) {
                            gprFile.putw(0x0A, System.in.read());
                            break;
                        }
                    } catch (final IOException e) {
                        Log.error("sysreadc: %s", e);
                    }
                    gprFile.putw(0x0A, -1);
                }
                case SEMIHOSTING_SYSERRNO -> {
                    // last host error
                    gprFile.putd(0x0A, 0L);
                }
                default -> Log.warn("undefined semihosting call sysnum=%x, params=%x", sysnum, params);
            }
            return next;
        }

        if (machine.breakpoint(id)) {
            return pc;
        }

        throw new TrapException(id, 0x03L, pc, "breakpoint instruction");
    }

    //endregion

    //region RV32 MULTIPLY

    private static long mulhu64(final long x, final long y) {
        final var x_lo = x & 0xFFFFFFFFL;
        final var x_hi = x >>> 32;
        final var y_lo = y & 0xFFFFFFFFL;
        final var y_hi = y >>> 32;

        final var lo_lo = x_lo * y_lo;
        final var hi_lo = x_hi * y_lo;
        final var lo_hi = x_lo * y_hi;
        final var hi_hi = x_hi * y_hi;

        final var mid = (hi_lo & 0xFFFFFFFFL) + (lo_hi & 0xFFFFFFFFL) + (lo_lo >>> 32);

        return hi_hi + (hi_lo >>> 32) + (lo_hi >>> 32) + (mid >>> 32);
    }

    private static long mulh64(final long x, final long y) {
        final var neg = (x < 0) ^ (y < 0);

        final var ux = x < 0 ? -x : x;
        final var uy = y < 0 ? -y : y;

        final var high = mulhu64(ux, uy);

        return neg ? ~high + ((ux * uy != 0) ? 1 : 0) : high;
    }

    private static long mulhsu64(final long x, final long y) {
        final var neg = x < 0;

        final var ux = x < 0 ? -x : x;
        final var uy = y; // already unsigned

        final var high = mulhu64(ux, uy);

        return neg ? ~high + ((ux * uy != 0) ? 1 : 0) : high;
    }

    private void mul(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) * gprFile.getd(rs2));
    }

    private void mulh(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, mulh64(gprFile.getd(rs1), gprFile.getd(rs2)));
    }

    private void mulhsu(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, mulhsu64(gprFile.getd(rs1), gprFile.getd(rs2)));
    }

    private void mulhu(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, mulhu64(gprFile.getd(rs1), gprFile.getd(rs2)));
    }

    private void div(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getd(rs1);
        final var rhs = gprFile.getd(rs2);

        if (rhs == 0L) {
            gprFile.putd(rd, -1L);
            return;
        }

        if (lhs == Long.MIN_VALUE && rhs == -1L) {
            gprFile.putd(rd, lhs);
            return;
        }

        gprFile.putd(rd, lhs / rhs);
    }

    private void divu(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getd(rs1);
        final var rhs = gprFile.getd(rs2);

        if (rhs == 0L) {
            gprFile.putd(rd, -1L);
            return;
        }

        gprFile.putd(rd, Long.divideUnsigned(lhs, rhs));
    }

    private void rem(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getd(rs1);
        final var rhs = gprFile.getd(rs2);

        if (rhs == 0L) {
            gprFile.putd(rd, lhs);
            return;
        }

        if (lhs == Long.MIN_VALUE && rhs == -1L) {
            gprFile.putd(rd, 0L);
            return;
        }

        gprFile.putd(rd, lhs % rhs);
    }

    private void remu(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getd(rs1);
        final var rhs = gprFile.getd(rs2);

        if (rhs == 0L) {
            gprFile.putd(rd, lhs);
            return;
        }

        gprFile.putd(rd, Long.remainderUnsigned(lhs, rhs));
    }

    //endregion

    //region RV64 ATOMIC

    private void lr_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var value = ld(gprFile.getd(rs1));
        gprFile.putd(rd, value);

        // TODO: reservation
    }

    private void sc_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        sd(gprFile.getd(rs1), gprFile.getd(rs2));
        gprFile.putd(rd, 0L);

        // TODO: reservation
    }

    private void amoswap_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var vaddr = gprFile.getd(rs1);
        if ((vaddr & 0x7L) != 0L) {
            throw new TrapException(id, 0x06L, vaddr, "misaligned atomic address %x", vaddr);
        }

        final long value;
        synchronized (machine.acquireLock(vaddr)) {
            final var source = gprFile.getd(rs2);
            value = ld(vaddr);
            sd(vaddr, source);
        }

        gprFile.putd(rd, value);
    }

    private void amoadd_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var vaddr = gprFile.getd(rs1);
        if ((vaddr & 0x7L) != 0L) {
            throw new TrapException(id, 0x06L, vaddr, "misaligned atomic address %x", vaddr);
        }

        final long value;
        synchronized (machine.acquireLock(vaddr)) {
            final var source = gprFile.getd(rs2);
            value = ld(vaddr);
            sd(vaddr, value + source);
        }

        gprFile.putd(rd, value);
    }

    private void amoxor_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amoxor.d");
    }

    private void amoand_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amoand.d");
    }

    private void amoor_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        final var vaddr = gprFile.getd(rs1);
        if ((vaddr & 0x7L) != 0L) {
            throw new TrapException(id, 0x06L, vaddr, "misaligned atomic address %x", vaddr);
        }

        final long value;
        synchronized (machine.acquireLock(vaddr)) {
            final var source = gprFile.getd(rs2);
            value = ld(vaddr);
            sd(vaddr, value | source);
        }

        gprFile.putd(rd, value);
    }

    private void amomin_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amomin.d");
    }

    private void amomax_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amomax.d");
    }

    private void amominu_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amominu.d");
    }

    private void amomaxu_d(final int rd, final int rs1, final int rs2, final int aq, final int rl) {
        unsupported("amomaxu.d");
    }

    //endregion

    //region RV64 SINGLE-PRECISION FLOATING-POINT

    private void fcvt_l_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.l.s");
    }

    private void fcvt_lu_s(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.lu.s");
    }

    private void fcvt_s_l(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.s.l");
    }

    private void fcvt_s_lu(final int rd, final int rs1, final int rs2, final int rm) {
        unsupported("fcvt.s.lu");
    }

    // endregion

    //region RV64 INTEGER

    private void lwu(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, lwu(vaddr));
    }

    private void ld(final int rd, final int rs1, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        gprFile.putd(rd, ld(vaddr));
    }

    private void sd(final int rs1, final int rs2, final int imm) {
        final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);
        sd(vaddr, gprFile.getd(rs2));
    }

    private void addiw(final int rd, final int rs1, final int imm) {
        gprFile.putw(rd, gprFile.getw(rs1) + signExtend(imm, 12));
    }

    private void slliw(final int rd, final int rs1, final int shamt) {
        gprFile.putw(rd, gprFile.getw(rs1) << shamt);
    }

    private void srliw(final int rd, final int rs1, final int shamt) {
        gprFile.putw(rd, gprFile.getw(rs1) >>> shamt);
    }

    private void sraiw(final int rd, final int rs1, final int shamt) {
        gprFile.putw(rd, gprFile.getw(rs1) >> shamt);
    }

    private void addw(final int rd, final int rs1, final int rs2) {
        gprFile.putw(rd, gprFile.getw(rs1) + gprFile.getw(rs2));
    }

    private void subw(final int rd, final int rs1, final int rs2) {
        gprFile.putw(rd, gprFile.getw(rs1) - gprFile.getw(rs2));
    }

    private void sllw(final int rd, final int rs1, final int rs2) {
        final var shamt = gprFile.getw(rs2) & 0b11111;
        gprFile.putw(rd, gprFile.getw(rs1) << shamt);
    }

    private void srlw(final int rd, final int rs1, final int rs2) {
        final var shamt = gprFile.getw(rs2) & 0b11111;
        gprFile.putw(rd, gprFile.getw(rs1) >>> shamt);
    }

    private void sraw(final int rd, final int rs1, final int rs2) {
        final var shamt = gprFile.getw(rs2) & 0b11111;
        gprFile.putw(rd, gprFile.getw(rs1) >> shamt);
    }

    //endregion

    //region RV64 MULTIPLY

    private void mulw(final int rd, final int rs1, final int rs2) {
        gprFile.putw(rd, gprFile.getw(rs1) * gprFile.getw(rs2));
    }

    private void divw(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getw(rs1);
        final var rhs = gprFile.getw(rs2);

        if (rhs == 0) {
            gprFile.putw(rd, -1);
            return;
        }

        if (lhs == Integer.MIN_VALUE && rhs == -1) {
            gprFile.putw(rd, lhs);
            return;
        }

        gprFile.putw(rd, lhs / rhs);
    }

    private void divuw(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getw(rs1);
        final var rhs = gprFile.getw(rs2);

        if (rhs == 0L) {
            gprFile.putw(rd, ~0);
            return;
        }

        gprFile.putw(rd, Integer.divideUnsigned(lhs, rhs));
    }

    private void remw(final int rd, final int rs1, final int rs2) {
        final var lhs = gprFile.getw(rs1);
        final var rhs = gprFile.getw(rs2);

        if (rhs == 0) {
            gprFile.putw(rd, lhs);
            return;
        }

        if (lhs == Integer.MIN_VALUE && rhs == -1) {
            gprFile.putw(rd, 0);
            return;
        }

        gprFile.putw(rd, lhs % rhs);
    }

    private void remuw(final int rd, final int rs1, final int rs2) {
        unsupported("remuw");
    }

    //endregion

    //region COMPRESSED

    private void c_addi4spn(final int rd, final int uimm) {
        gprFile.putd(rd, gprFile.getd(0x2) + uimm);
    }

    private void c_fld(final int rd, final int rs1, final int uimm) {
        final var vaddr = gprFile.getd(rs1) + uimm;
        fprFile.putdr(rd, ld(vaddr));
    }

    private void c_lw(final int rd, final int rs1, final int uimm) {
        final var vaddr = gprFile.getd(rs1) + uimm;
        gprFile.putd(rd, lw(vaddr));
    }

    private void c_ld(final int rd, final int rs1, final int uimm) {
        final var vaddr = gprFile.getd(rs1) + uimm;
        gprFile.putd(rd, ld(vaddr));
    }

    private void c_fsd(final int rs1, final int rs2, final int uimm) {
        unsupported("c.fsd");
    }

    private void c_sw(final int rs1, final int rs2, final int uimm) {
        final var vaddr = gprFile.getd(rs1) + uimm;
        sw(vaddr, gprFile.getw(rs2));
    }

    private void c_sd(final int rs1, final int rs2, final int uimm) {
        final var vaddr = gprFile.getd(rs1) + uimm;
        sd(vaddr, gprFile.getd(rs2));
    }

    private void c_nop() {
        // noop
    }

    private void c_addi(final int rd, final int rs1, final int imm) {
        gprFile.putd(rd, gprFile.getd(rs1) + signExtend(imm, 6));
    }

    private void c_addiw(final int rd, final int rs1, final int imm) {
        gprFile.putw(rd, gprFile.getw(rs1) + signExtend(imm, 6));
    }

    private void c_li(final int rd, final int imm) {
        gprFile.putd(rd, signExtend(imm, 6));
    }

    private void c_addi16sp(final int imm) {
        gprFile.putd(0x2, gprFile.getd(0x2) + signExtend(imm, 10));
    }

    private void c_lui(final int rd, final int imm) {
        gprFile.putd(rd, signExtend(imm, 18));
    }

    private void c_srli(final int rd, final int rs1, final int shamt) {
        gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
    }

    private void c_srai(final int rd, final int rs1, final int shamt) {
        gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
    }

    private void c_andi(final int rd, final int rs1, final int uimm) {
        gprFile.putd(rd, gprFile.getd(rs1) & signExtend(uimm, 6));
    }

    private void c_sub(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) - gprFile.getd(rs2));
    }

    private void c_xor(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) ^ gprFile.getd(rs2));
    }

    private void c_or(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) | gprFile.getd(rs2));
    }

    private void c_and(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) & gprFile.getd(rs2));
    }

    private void c_subw(final int rd, final int rs1, final int rs2) {
        gprFile.putw(rd, gprFile.getw(rs1) - gprFile.getw(rs2));
    }

    private void c_addw(final int rd, final int rs1, final int rs2) {
        gprFile.putw(rd, gprFile.getw(rs1) + gprFile.getw(rs2));
    }

    private long c_j(final int imm) {
        return pc + signExtend(imm, 12);
    }

    private long c_beqz(final long next, final int rs1, final int imm) {
        if (gprFile.getd(rs1) == 0L) {
            return pc + signExtend(imm, 9);
        }
        return next;
    }

    private long c_bnez(final long next, final int rs1, final int imm) {
        if (gprFile.getd(rs1) != 0L) {
            return pc + signExtend(imm, 9);
        }
        return next;
    }

    private void c_slli(final int rd, final int rs1, final int shamt) {
        gprFile.putd(rd, gprFile.getd(rs1) << shamt);
    }

    private void c_fldsp(final int rd, final int uimm) {
        final var vaddr = gprFile.getd(0x2) + uimm;
        fprFile.putdr(rd, ld(vaddr));
    }

    private void c_lwsp(final int rd, final int uimm) {
        final var vaddr = gprFile.getd(0x2) + uimm;
        gprFile.putd(rd, lw(vaddr));
    }

    private void c_ldsp(final int rd, final int uimm) {
        final var vaddr = gprFile.getd(0x2) + uimm;
        gprFile.putd(rd, ld(vaddr));
    }

    private long c_jr(final int rs1) {
        return gprFile.getd(rs1) & ~1L;
    }

    private void c_mv(final int rd, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs2));
    }

    private long c_ebreak() {
        if (machine.breakpoint(id)) {
            return pc;
        }

        throw new TrapException(id, 0x03L, pc, "breakpoint instruction");
    }

    private long c_jalr(final long next, final int rs1) {
        final var pnext = gprFile.getd(rs1) & ~1L;
        gprFile.putd(0x1, next);
        return pnext;
    }

    private void c_add(final int rd, final int rs1, final int rs2) {
        gprFile.putd(rd, gprFile.getd(rs1) + gprFile.getd(rs2));
    }

    private void c_fsdsp(final int rs2, final int uimm) {
        unsupported("c.fsdsp");
    }

    private void c_swsp(final int rs2, final int uimm) {
        unsupported("c.swsp");
    }

    private void c_sdsp(final int rs2, final int uimm) {
        final var vaddr = gprFile.getd(0x2) + uimm;
        sd(vaddr, gprFile.getd(rs2));
    }

    //endregion

    //region EXTENSION - CSR

    private void csrrw(final int rd, final int rs1, final int csr) {
        if (rd == 0) {
            csrFile.putd(csr, priv, gprFile.getd(rs1));
            return;
        }

        final var value = csrFile.getd(csr, priv);
        csrFile.putd(csr, priv, gprFile.getd(rs1));
        gprFile.putd(rd, value);
    }

    private void csrrs(final int rd, final int rs1, final int csr) {
        final var value = csrFile.getd(csr, priv);
        if (rs1 != 0) {
            final var mask = gprFile.getd(rs1);
            csrFile.putd(csr, priv, value | mask);
        }
        gprFile.putd(rd, value);
    }

    private void csrrc(final int rd, final int rs1, final int csr) {
        final var value = csrFile.getd(csr, priv);
        if (rs1 != 0) {
            final var mask = gprFile.getd(rs1);
            csrFile.putd(csr, priv, value & ~mask);
        }
        gprFile.putd(rd, value);
    }

    private void csrrwi(final int rd, final int uimm, final int csr) {
        if (rd == 0) {
            csrFile.putd(csr, priv, uimm);
            return;
        }

        final var value = csrFile.getd(csr, priv);
        csrFile.putd(csr, priv, uimm);
        gprFile.putd(rd, value);
    }

    private void csrrsi(final int rd, final int uimm, final int csr) {
        final var value = csrFile.getd(csr, priv);
        if (uimm != 0) {
            csrFile.putd(csr, priv, value | uimm);
        }
        gprFile.putd(rd, value);
    }

    private void csrrci(final int rd, final int uimm, final int csr) {
        final var value = csrFile.getd(csr, priv);
        if (uimm != 0) {
            csrFile.putd(csr, priv, value & ~uimm);
        }
        gprFile.putd(rd, value);
    }

    //endregion

    //region EXTENSION - FENCE.I

    private void fence_i(final int rd, final int rs1, final int imm) {
        // TODO: instruction and io cache
    }

    //endregion
}
