package io.scriptor.impl;

import io.scriptor.isa.Instruction;
import io.scriptor.isa.Registry;
import io.scriptor.machine.*;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

import static io.scriptor.isa.CSR.*;
import static io.scriptor.util.ByteUtil.signExtend;

public final class Hart64 implements Hart {

    private final Machine machine;
    private final int id;

    private final GPRFile gprFile = new GPRFile64();
    private final FPRFile fprFile = new FPRFile64();
    private final CSRFile csrFile = new CSRFile64();

    private long pc;
    private int priv;
    private boolean wfi;

    public Hart64(final @NotNull Machine machine, final int id) {
        this.machine = machine;
        this.id = id;
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

            final var prev_fp = machine.read(fp, 8, true);
            final var prev_ra = machine.read(fp + 8, 8, true);

            fp = prev_fp;
            ra = prev_ra;
        }
    }


    @Override
    public void reset(final long entry) {
        gprFile.reset();
        fprFile.reset();
        csrFile.reset();

        pc = entry;

        priv = CSR_M;
        wfi = false;

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

        csrFile.define(mstatus, 0x1888L, -1, 0x1800L);
        csrFile.define(sstatus, 0x84L, mstatus);
        csrFile.define(medeleg, 0xFFFFL);
        csrFile.define(mideleg, 0xFFFFL);

        // machine interrupt control

        csrFile.define(mie, 0x88888L);
        csrFile.define(mip, 0x88888L);
        csrFile.define(sie, 0x22L);
        csrFile.define(sip, 0x22L);

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

        csrFile.define(time, () -> machine.clint().mtime());
        csrFile.define(stimecmp, () -> machine.clint().mtimecmp(id), val -> machine.clint().mtimecmp(id, val));

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
    public void dump(final @NotNull PrintStream out) {

        final var instruction = machine.fetch(pc, true);
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
            final var address = sp + offset;
            final var value   = machine.read(address, 8, true);

            out.printf("%016x : %016x%n", address, value);
        }

        printStackTrace(out);
    }

    @Override
    public void step() {
        try {
            final var instruction = machine.fetch(pc, false);
            final var definition  = Registry.get(64, instruction);
            pc = execute(instruction, definition);
        } catch (final TrapException e) {
            handle(e.getTrapCause(), e.getTrapValue());
            machine.trap(id);
        }

        interrupt();
    }

    private void handle(final long cause, final long tval) {
        final var interrupt = (cause & (1L << 63)) != 0L;
        final var code      = (int) (cause & 0x7FFFFFFFL);

        final int target;
        if (interrupt) {
            target = ((csrFile.getd(mideleg, CSR_M) & (1L << code)) != 0) ? CSR_S : CSR_M;
        } else {
            target = ((csrFile.getd(medeleg, CSR_M) & (1L << code)) != 0) ? CSR_S : CSR_M;
        }

        if (target == CSR_M) {
            csrFile.putd(mepc, CSR_M, pc);
            csrFile.putd(mcause, CSR_M, cause);
            csrFile.putd(mtval, CSR_M, tval);
        } else {
            csrFile.putd(sepc, CSR_S, pc);
            csrFile.putd(scause, CSR_S, cause);
            csrFile.putd(stval, CSR_S, tval);
        }

        var status = csrFile.getd(mstatus, CSR_M);
        if (target == CSR_M) {
            final var mie = (status >> 3) & 1L;
            status &= ~(1L << 3);
            status |= CSR_M << 11;
            status |= mie << 7;
        } else {
            final var sie = (status >> 1) & 1L;
            status &= ~(1L << 1);
            status |= CSR_S << 8;
            status |= sie << 5;
        }
        csrFile.putd(mstatus, priv, status);

        final var tvec = target == CSR_M ? csrFile.getd(mtvec, CSR_M) : csrFile.getd(stvec, CSR_S);
        final var base = tvec & ~0b11L;
        final var mode = tvec & 0b11L;

        if (mode == 0b01 && interrupt) {
            pc = base + 4L * code;
        } else {
            pc = base;
        }

        priv = target;
    }

    private void interrupt() {
        final var clint = machine.clint();

        final var mtimecmp = clint.mtimecmp(id);
        final var mtime    = clint.mtime();

        final var mie_ = csrFile.getd(mie, priv);

        if (mie_ != 0L && mtimecmp != 0L && mtime >= mtimecmp) {
            csrFile.putd(mcause, priv, 1L << 63 | 7L);
            csrFile.putd(mepc, priv, pc);
            csrFile.putd(mtval, priv, 0L);

            pc = csrFile.getd(mtvec, priv);
        }
    }

    private final int[] values = new int[3];

    @Override
    public long execute(final int instruction, final @NotNull Instruction definition) {
        if (wfi) {
            return pc;
        }

        var next = pc + definition.ilen();

        switch (definition.mnemonic()) {
            case "fence", "fence.i", "c.nop" -> {
                // noop
            }

            case "ebreak", "c.ebreak" -> {
                if (machine.breakpoint(id)) {
                    next = pc;
                }
            }

            case "mret" -> {
                var status = csrFile.getd(mstatus, priv);

                final var mpp  = (int) ((status >> 11) & 0b11L);
                final var mpie = (status & (1L << 7)) != 0L;

                priv = mpp;

                status &= ~(1L << 3);
                status |= (mpie ? (1L << 3) : 0L);
                status |= (0b11L << 11);
                status |= (1L << 7);

                csrFile.putd(mstatus, CSR_M, status);

                next = csrFile.getd(mepc, CSR_M);
            }
            case "sret" -> {
                var status = csrFile.getd(sstatus, priv);

                final var spp  = (int) ((status >> 8) & 0b1L);
                final var spie = (status & (1L << 5)) != 0L;

                priv = spp;

                status &= ~(1L << 1);
                status |= (spie ? (1L << 1) : 0L);
                status |= (1L << 8);
                status |= (1L << 5);

                csrFile.putd(sstatus, CSR_M, status);

                next = csrFile.getd(sepc, CSR_M);
            }

            case "addi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) + signExtend(imm, 12));
            }

            case "addiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) + signExtend(imm, 12));
            }

            case "slti" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) < signExtend(imm, 12) ? 1L : 0L);
            }
            case "sltiu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, Long.compareUnsigned(gprFile.getd(rs1), signExtend(imm, 12)) < 0 ? 1L : 0L);
            }

            case "slt" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) < gprFile.getd(rs2) ? 1L : 0L);
            }
            case "sltu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) < 0 ? 1L : 0L);
            }

            case "xori" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) ^ signExtend(imm, 12));
            }
            case "ori" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) | signExtend(imm, 12));
            }
            case "andi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) & signExtend(imm, 12));
            }

            case "xor" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) ^ gprFile.getd(rs2));
            }
            case "or" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) | gprFile.getd(rs2));
            }
            case "and" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) & gprFile.getd(rs2));
            }

            case "sll" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var shamt = gprFile.getd(rs2) & 0b111111;

                gprFile.putd(rd, gprFile.getd(rs1) << shamt);
            }
            case "srl" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var shamt = gprFile.getd(rs2) & 0b111111;

                gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
            }
            case "sra" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var shamt = gprFile.getd(rs2) & 0b111111;

                gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
            }

            case "sllw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var shamt = gprFile.getw(rs2) & 0b11111;

                gprFile.putw(rd, gprFile.getw(rs1) << shamt);
            }
            case "srlw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var shamt = gprFile.getw(rs2) & 0b11111;

                gprFile.putw(rd, gprFile.getw(rs1) >>> shamt);
            }
            case "sraw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var shamt = gprFile.getw(rs2) & 0b11111;

                gprFile.putw(rd, gprFile.getw(rs1) >> shamt);
            }

            case "slli", "c.slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) << shamt);
            }
            case "srli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
            }
            case "srai" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
            }

            case "slliw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) << shamt);
            }
            case "srliw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) >>> shamt);
            }
            case "sraiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) >> shamt);
            }

            case "add", "c.add" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) + gprFile.getd(rs2));
            }
            case "addw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) + gprFile.getw(rs2));
            }

            case "sub" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) - gprFile.getd(rs2));
            }
            case "subw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) - gprFile.getw(rs2));
            }

            case "jal" -> {
                definition.decode(instruction, values, "rd", "imm");

                final var rd  = values[0];
                final var imm = values[1];

                final var ra = next;

                next = pc + signExtend(imm, 21);

                gprFile.putd(rd, ra);
            }

            case "jalr" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var ra = next;

                next = (gprFile.getd(rs1) + signExtend(imm, 12)) & ~1L;

                gprFile.putd(rd, ra);
            }

            case "lui" -> {
                definition.decode(instruction, values, "rd", "imm");

                final var rd  = values[0];
                final var imm = values[1];

                gprFile.putd(rd, imm);
            }
            case "auipc" -> {
                definition.decode(instruction, values, "rd", "imm");

                final var rd  = values[0];
                final var imm = values[1];

                gprFile.putd(rd, pc + imm);
            }

            case "beq" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                if (gprFile.getd(rs1) == gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bne" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                if (gprFile.getd(rs1) != gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "blt" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                if (gprFile.getd(rs1) < gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bge" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                if (gprFile.getd(rs1) >= gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bltu" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                if (Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) < 0) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bgeu" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                if (Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) >= 0) {
                    next = pc + signExtend(imm, 13);
                }
            }

            case "lb" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lb(address));
            }
            case "lh" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lh(address));
            }
            case "lw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lw(address));
            }
            case "ld" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.ld(address));
            }
            case "lbu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lbu(address));
            }
            case "lhu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lhu(address));
            }
            case "lwu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lwu(address));
            }

            case "sb" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sb(address, (byte) gprFile.getd(rs2));
            }
            case "sh" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sh(address, (short) gprFile.getd(rs2));
            }
            case "sw" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sw(address, gprFile.getw(rs2));
            }
            case "sd" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sd(address, gprFile.getd(rs2));
            }

            case "mul" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) * gprFile.getd(rs2));
            }
            case "mulw" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) * gprFile.getw(rs2));
            }

            case "div" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var lhs = gprFile.getd(rs1);
                final var rhs = gprFile.getd(rs2);

                if (rhs == 0L) {
                    gprFile.putd(rd, -1L);
                    break;
                }

                if (lhs == Long.MIN_VALUE && rhs == -1L) {
                    gprFile.putd(rd, lhs);
                    break;
                }

                gprFile.putd(rd, lhs / rhs);
            }
            case "divu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var lhs = gprFile.getd(rs1);
                final var rhs = gprFile.getd(rs2);

                if (rhs == 0L) {
                    gprFile.putd(rd, ~0L);
                    break;
                }

                gprFile.putd(rd, Long.divideUnsigned(lhs, rhs));
            }
            case "rem" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var lhs = gprFile.getd(rs1);
                final var rhs = gprFile.getd(rs2);

                if (rhs == 0L) {
                    gprFile.putd(rd, lhs);
                    break;
                }

                if (lhs == Long.MIN_VALUE && rhs == -1L) {
                    gprFile.putd(rd, 0L);
                    break;
                }

                gprFile.putd(rd, lhs % rhs);
            }
            case "remu" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var lhs = gprFile.getd(rs1);
                final var rhs = gprFile.getd(rs2);

                if (rhs == 0L) {
                    gprFile.putd(rd, lhs);
                    break;
                }

                gprFile.putd(rd, Long.remainderUnsigned(lhs, rhs));
            }

            case "csrrw" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var csr = values[2];

                if (rd == 0) {
                    csrFile.putd(csr, priv, gprFile.getd(rs1));
                    break;
                }

                final var value = csrFile.getd(csr, priv);
                csrFile.putd(csr, priv, gprFile.getd(rs1));
                gprFile.putd(rd, value);
            }
            case "csrrwi" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr");

                final var rd   = values[0];
                final var uimm = values[1];
                final var csr  = values[2];

                if (rd == 0) {
                    csrFile.putd(csr, priv, uimm);
                    break;
                }

                final var value = csrFile.getd(csr, priv);
                csrFile.putd(csr, priv, uimm);
                gprFile.putd(rd, value);
            }
            case "csrrs" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var csr = values[2];

                final var value = csrFile.getd(csr, priv);
                if (rs1 != 0) {
                    final var mask = gprFile.getd(rs1);
                    csrFile.putd(csr, priv, value | mask);
                }
                gprFile.putd(rd, value);
            }
            case "csrrsi" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr");

                final var rd   = values[0];
                final var uimm = values[1];
                final var csr  = values[2];

                final var value = csrFile.getd(csr, priv);
                if (uimm != 0) {
                    csrFile.putd(csr, priv, value | uimm);
                }
                gprFile.putd(rd, value);
            }
            case "csrrc" -> {
                definition.decode(instruction, values, "rd", "rs1", "csr");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var csr = values[2];

                final var value = csrFile.getd(csr, priv);
                if (rs1 != 0) {
                    final var mask = gprFile.getd(rs1);
                    csrFile.putd(csr, priv, value & ~mask);
                }
                gprFile.putd(rd, value);
            }
            case "csrrci" -> {
                definition.decode(instruction, values, "rd", "uimm", "csr");

                final var rd   = values[0];
                final var uimm = values[1];
                final var csr  = values[2];

                final var value = csrFile.getd(csr, priv);
                if (uimm != 0) {
                    csrFile.putd(csr, priv, value & ~uimm);
                }
                gprFile.putd(rd, value);
            }

            case "amoswap.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var address = gprFile.getd(rs1);
                final var aligned = address & ~0x3L;

                final int value;
                synchronized (machine.acquireLock(aligned)) {
                    final var source = gprFile.getw(rs2);
                    value = machine.lw(aligned);
                    machine.sw(aligned, source);
                }

                gprFile.putw(rd, value);
            }
            case "amoswap.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var address = gprFile.getd(rs1);
                final var aligned = address & ~0x7L;

                final long value;
                synchronized (machine.acquireLock(aligned)) {
                    final var source = gprFile.getd(rs2);
                    value = machine.ld(aligned);
                    machine.sd(aligned, source);
                }

                gprFile.putd(rd, value);
            }
            case "amoadd.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var address = gprFile.getd(rs1);
                final var aligned = address & ~0x3L;

                final int value;
                synchronized (machine.acquireLock(aligned)) {
                    final var source = gprFile.getw(rs2);
                    value = machine.lw(aligned);
                    machine.sw(aligned, value + source);
                }

                gprFile.putw(rd, value);
            }
            case "amoadd.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                final var address = gprFile.getd(rs1);
                final var aligned = address & ~0x7L;

                final long value;
                synchronized (machine.acquireLock(aligned)) {
                    final var source = gprFile.getd(rs2);
                    value = machine.ld(aligned);
                    machine.sd(aligned, value + source);
                }

                gprFile.putd(rd, value);
            }

            case "wfi" -> {
                wfi = (csrFile.getd(mip, priv) & csrFile.getd(mie, priv)) == 0;
            }

            case "c.addi" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) + signExtend(imm, 6));
            }
            case "c.addiw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                gprFile.putw(rd, gprFile.getw(rs1) + signExtend(imm, 6));
            }
            case "c.addi4spn" -> {
                definition.decode(instruction, values, "rdp", "uimm");

                final var rd   = values[0] + 0x8;
                final var uimm = values[1];

                gprFile.putd(rd, gprFile.getd(0x2) + uimm);
            }
            case "c.li" -> {
                definition.decode(instruction, values, "rd", "imm");

                final var rd  = values[0];
                final var imm = values[1];

                gprFile.putd(rd, signExtend(imm, 6));
            }
            case "c.addi16sp" -> {
                definition.decode(instruction, values, "imm");

                final var imm = values[0];

                gprFile.putd(0x2, gprFile.getd(0x2) + signExtend(imm, 10));
            }
            case "c.lui" -> {
                definition.decode(instruction, values, "rd", "imm");

                final var rd  = values[0];
                final var imm = values[1];

                gprFile.putd(rd, signExtend(imm, 18));
            }
            case "c.and" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");

                final var rd  = values[0] + 0x8;
                final var rs1 = values[1] + 0x8;
                final var rs2 = values[2] + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) & gprFile.getd(rs2));
            }
            case "c.andi" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");

                final var rd   = values[0] + 0x8;
                final var rs1  = values[1] + 0x8;
                final var uimm = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) & signExtend(uimm, 6));
            }
            case "c.j" -> {
                definition.decode(instruction, values, "imm");

                final var imm = values[0];

                next = pc + signExtend(imm, 12);
            }
            case "c.beqz" -> {
                definition.decode(instruction, values, "rs1p", "imm");

                final var rs1 = values[0] + 0x8;
                final var imm = values[1];

                if (gprFile.getd(rs1) == 0L) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.bnez" -> {
                definition.decode(instruction, values, "rs1p", "imm");

                final var rs1 = values[0] + 0x8;
                final var imm = values[1];

                if (gprFile.getd(rs1) != 0L) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.lwsp" -> {
                definition.decode(instruction, values, "rd", "uimm");

                final var rd   = values[0];
                final var uimm = values[1];

                final var address = gprFile.getd(0x2) + uimm;

                gprFile.putd(rd, machine.lw(address));
            }
            case "c.ldsp" -> {
                definition.decode(instruction, values, "rd", "uimm");

                final var rd   = values[0];
                final var uimm = values[1];

                final var address = gprFile.getd(0x2) + uimm;

                gprFile.putd(rd, machine.ld(address));
            }
            case "c.jr" -> {
                definition.decode(instruction, values, "rs1");

                final var rs1 = values[0];

                next = gprFile.getd(rs1) & ~1L;
            }
            case "c.jalr" -> {
                definition.decode(instruction, values, "rs1");

                final var rs1 = values[0];

                final var ra = next;

                next = gprFile.getd(rs1) & ~1L;

                gprFile.putd(0x1, ra);
            }
            case "c.mv" -> {
                definition.decode(instruction, values, "rd", "rs2");

                final var rd  = values[0];
                final var rs2 = values[1];

                gprFile.putd(rd, gprFile.getd(rs2));
            }
            case "c.sdsp" -> {
                definition.decode(instruction, values, "rs2", "uimm");

                final var rs2  = values[0];
                final var uimm = values[1];

                final var address = gprFile.getd(0x2) + uimm;

                machine.sd(address, gprFile.getd(rs2));
            }
            case "c.or" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");

                final var rd  = values[0] + 0x8;
                final var rs1 = values[1] + 0x8;
                final var rs2 = values[2] + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) | gprFile.getd(rs2));
            }
            case "c.lw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");

                final var rd   = values[0] + 0x8;
                final var rs1  = values[1] + 0x8;
                final var uimm = values[2];

                final var address = gprFile.getd(rs1) + uimm;

                gprFile.putd(rd, machine.lw(address));
            }
            case "c.ld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");

                final var rd   = values[0] + 0x8;
                final var rs1  = values[1] + 0x8;
                final var uimm = values[2];

                final var address = gprFile.getd(rs1) + uimm;

                gprFile.putd(rd, machine.ld(address));
            }
            case "c.sw" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");

                final var rs1  = values[0] + 0x8;
                final var rs2  = values[1] + 0x8;
                final var uimm = values[2];

                final var address = gprFile.getd(rs1) + uimm;

                machine.sw(address, gprFile.getw(rs2));
            }
            case "c.sd" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");

                final var rs1  = values[0] + 0x8;
                final var rs2  = values[1] + 0x8;
                final var uimm = values[2];

                final var address = gprFile.getd(rs1) + uimm;

                machine.sd(address, gprFile.getd(rs2));
            }
            case "c.fld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");

                final var rd   = values[0] + 0x8;
                final var rs1  = values[1] + 0x8;
                final var uimm = values[2];

                final var address = gprFile.getd(rs1) + uimm;

                fprFile.putdr(rd, machine.ld(address));
            }

            case "c.addw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");

                final var rd  = values[0] + 0x8;
                final var rs1 = values[1] + 0x8;
                final var rs2 = values[2] + 0x8;

                gprFile.putw(rd, gprFile.getw(rs1) + gprFile.getw(rs2));
            }
            case "c.subw" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");

                final var rd  = values[0] + 0x8;
                final var rs1 = values[1] + 0x8;
                final var rs2 = values[2] + 0x8;

                gprFile.putw(rd, gprFile.getw(rs1) - gprFile.getw(rs2));
            }

            case "c.sub" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");

                final var rd  = values[0] + 0x8;
                final var rs1 = values[1] + 0x8;
                final var rs2 = values[2] + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) - gprFile.getd(rs2));
            }

            case "c.srli" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "shamt");

                final var rd    = values[0] + 0x8;
                final var rs1   = values[1] + 0x8;
                final var shamt = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
            }
            case "c.srai" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "shamt");

                final var rd    = values[0] + 0x8;
                final var rs1   = values[1] + 0x8;
                final var shamt = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
            }

            default -> {
                Log.error("unsupported instruction %s", definition);
                throw new TrapException(0x02, instruction);
            }
        }

        return next;
    }

    @Override
    public boolean wfi() {
        return wfi;
    }

    @Override
    public void wake() {
        wfi = false;
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
}
