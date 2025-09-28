package io.scriptor.impl;

import io.scriptor.isa.Instruction;
import io.scriptor.isa.Registry;
import io.scriptor.machine.*;
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
        csrFile.putd(misa,
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
        csrFile.putd(mvendorid, 0xCAFEBABEL);
        csrFile.putd(marchid, 0x1L);
        csrFile.putd(mimpid, 0x1L);
        csrFile.putd(mhartid, id);

        // machine status/control
        csrFile.putd(mstatus, 0x1800L);
        csrFile.putd(sstatus, 0L);
        csrFile.putd(medeleg, 0L);
        csrFile.putd(mideleg, 0L);

        // machine interrupt control
        csrFile.putd(mie, 0L);
        csrFile.putd(mip, 0L);
        csrFile.putd(sie, 0L);
        csrFile.putd(sip, 0L);

        // machine trap handling
        csrFile.putd(mtvec, 0L);
        csrFile.putd(stvec, 0L);
        csrFile.putd(mepc, 0L);
        csrFile.putd(sepc, 0L);
        csrFile.putd(mcause, 0L);
        csrFile.putd(scause, 0L);
        csrFile.putd(mtval, 0L);
        csrFile.putd(stval, 0L);
        csrFile.putd(mscratch, 0L);

        // machine virtual memory
        csrFile.putd(satp, 0L);

        // machine counters/timers
        csrFile.putd(time, 0L);
        csrFile.putd(cycle, 0L);
        csrFile.putd(instret, 0L);

        for (int pmpcfgx = pmpcfg0; pmpcfgx <= pmpcfg15; ++pmpcfgx)
            csrFile.putd(pmpcfgx, 0L);

        for (int pmpaddrx = pmpaddr0; pmpaddrx <= pmpaddr63; ++pmpaddrx)
            csrFile.putd(pmpaddrx, 0L);
    }

    @Override
    public void dump(final @NotNull PrintStream out) {

        final var instruction = machine.fetch(pc, true);
        out.printf("pc=%016x, instruction=%08x%n", pc, instruction);

        if (Registry.has(64, instruction)) {
            final var definition = Registry.get(64, instruction);

            final var display = new StringBuilder();
            display.append(definition.mnemonic());
            for (final var operand : definition.operands().values()) {
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
                   csrFile.getd(mstatus),
                   csrFile.getd(mtvec),
                   csrFile.getd(mcause),
                   csrFile.getd(mepc));

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
        }

        interrupt();
    }

    private void handle(final long cause, final long tval) {
        final var interrupt = (cause & (1L << 63)) != 0L;
        final var code      = (int) (cause & 0x7FFFFFFFL);

        csrFile.putd(mepc, priv, pc);
        csrFile.putd(mcause, priv, cause);
        csrFile.putd(mtval, priv, tval);

        var status = csrFile.getd(mstatus, priv);

        final var mie = (status & (1L << 3)) != 0L;
        if (mie) {
            status |= (1L << 7);
        } else {
            status &= ~(1L << 7);
        }
        status &= ~(1L << 3);
        status |= (CSR_M << 11);

        csrFile.putd(mstatus, priv, status);

        final var tvec = csrFile.getd(mtvec, priv);
        final var base = tvec & ~0b11L;
        final var mode = tvec & 0b11L;
        if (mode == 0b01 && interrupt) {
            pc = base + 4L * code;
        } else {
            pc = base;
        }
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

    @Override
    public long execute(final int instruction, final @NotNull Instruction definition) {
        if (wfi) {
            return pc;
        }

        final var ilen = definition.ilen();

        var next = pc + ilen;

        switch (definition.mnemonic()) {
            case "fence.i", "c.nop" -> {
                // noop
            }

            case "ebreak", "c.ebreak" -> {
                next = pc;
                machine.breakpoint(id);
            }

            case "addi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) + signExtend(imm, 12));
            }

            case "slti" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) < signExtend(imm, 12) ? 1L : 0L);
            }
            case "sltiu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, Long.compareUnsigned(gprFile.getd(rs1), imm) < 0 ? 1L : 0L);
            }

            case "slt" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) < gprFile.getd(rs2) ? 1L : 0L);
            }
            case "sltu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) < 0 ? 1L : 0L);
            }

            case "xori" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) ^ signExtend(imm, 12));
            }
            case "ori" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) | signExtend(imm, 12));
            }
            case "andi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) & signExtend(imm, 12));
            }

            case "xor" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) ^ gprFile.getd(rs2));
            }
            case "or" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) | gprFile.getd(rs2));
            }
            case "and" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) & gprFile.getd(rs2));
            }

            case "slli", "c.slli" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) << shamt);
            }
            case "srli" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
            }
            case "srai" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
            }

            case "addiw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getw(rs1) + signExtend(imm, 12));
            }

            case "slliw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, (long) gprFile.getw(rs1) << shamt);
            }
            case "srliw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getw(rs1) >>> shamt);
            }
            case "sraiw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getw(rs1) >> shamt);
            }

            case "add", "c.add" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) + gprFile.getd(rs2));
            }
            case "addw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getw(rs1) + gprFile.getw(rs2));
            }

            case "sub" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) - gprFile.getd(rs2));
            }
            case "subw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getw(rs1) - gprFile.getw(rs2));
            }

            case "jal" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                next = pc + signExtend(imm, 21);

                gprFile.putd(rd, pc + ilen);
            }

            case "jalr" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                next = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, pc + ilen);
            }

            case "lui" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, imm);
            }
            case "auipc" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, pc + imm);
            }

            case "beq" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprFile.getd(rs1) == gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bne" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprFile.getd(rs1) != gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "blt" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprFile.getd(rs1) < gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bge" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprFile.getd(rs1) >= gprFile.getd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bltu" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) < 0) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bgeu" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (Long.compareUnsigned(gprFile.getd(rs1), gprFile.getd(rs2)) >= 0) {
                    next = pc + signExtend(imm, 13);
                }
            }

            case "lb" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lb(address));
            }
            case "lh" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lh(address));
            }
            case "lw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lw(address));
            }
            case "ld" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.ld(address));
            }
            case "lbu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lbu(address));
            }
            case "lhu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lhu(address));
            }
            case "lwu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, machine.lwu(address));
            }

            case "sb" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sb(address, (byte) gprFile.getd(rs2));
            }
            case "sh" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sh(address, (short) gprFile.getd(rs2));
            }
            case "sw" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sw(address, gprFile.getw(rs2));
            }
            case "sd" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprFile.getd(rs1) + signExtend(imm, 12);

                machine.sd(address, gprFile.getd(rs2));
            }

            case "mulw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, (long) gprFile.getw(rs1) * gprFile.getw(rs2));
            }

            case "div" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

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
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                final var lhs = gprFile.getd(rs1);
                final var rhs = gprFile.getd(rs2);

                if (rhs == 0L) {
                    gprFile.putd(rd, ~0L);
                    break;
                }

                gprFile.putd(rd, Long.divideUnsigned(lhs, rhs));
            }

            case "csrrw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                if (rd == 0) {
                    csrFile.putd(csr, priv, gprFile.getd(rs1));
                    break;
                }

                final var value = csrFile.getd(csr, priv);
                csrFile.putd(csr, priv, gprFile.getd(rs1));
                gprFile.putd(rd, value);
            }
            case "csrrwi" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);
                final var csr  = definition.get("csr", instruction);

                if (rd == 0) {
                    csrFile.putd(csr, priv, uimm);
                    break;
                }

                final var value = csrFile.getd(csr, priv);
                csrFile.putd(csr, priv, uimm);
                gprFile.putd(rd, value);
            }
            case "csrrs" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = csrFile.getd(csr, priv);
                if (rs1 != 0) {
                    final var mask = gprFile.getd(rs1);
                    csrFile.putd(csr, priv, value & mask);
                }
                gprFile.putd(rd, value);
            }
            case "csrrsi" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);
                final var csr  = definition.get("csr", instruction);

                final var value = csrFile.getd(csr, priv);
                if (uimm != 0) {
                    csrFile.putd(csr, priv, value & uimm);
                }
                gprFile.putd(rd, value);
            }
            case "csrrc" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = csrFile.getd(csr, priv);
                if (rs1 != 0) {
                    final var mask = gprFile.getd(rs1);
                    csrFile.putd(csr, priv, value & ~mask);
                }
                gprFile.putd(rd, value);
            }
            case "csrrci" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);
                final var csr  = definition.get("csr", instruction);

                final var value = csrFile.getd(csr, priv);
                if (uimm != 0) {
                    csrFile.putd(csr, priv, value & ~uimm);
                }
                gprFile.putd(rd, value);
            }

            case "amoswap.w" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                final var address = gprFile.getd(rs1);
                final var aligned = address & ~0x3L;

                final long value;
                synchronized (machine.acquireLock(aligned)) {
                    final var source = gprFile.getw(rs2);
                    value = machine.lw(aligned);
                    machine.sw(aligned, source);
                }

                gprFile.putd(rd, signExtend(value, 32));
            }

            case "wfi" -> {
                wfi = (csrFile.getd(mip, priv) & csrFile.getd(mie, priv)) == 0;
            }

            case "c.addi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) + signExtend(imm, 6));
            }
            case "c.addiw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, gprFile.getw(rs1) + signExtend(imm, 6));
            }
            case "c.addi4spn" -> {
                final var rd   = definition.get("rdp", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                gprFile.putd(rd, gprFile.getd(0x2) + uimm);
            }
            case "c.li" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, signExtend(imm, 6));
            }
            case "c.addi16sp" -> {
                final var imm = definition.get("imm", instruction);

                gprFile.putd(0x2, gprFile.getd(0x2) + signExtend(imm, 10));
            }
            case "c.lui" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprFile.putd(rd, signExtend(imm, 18));
            }
            case "c.and" -> {
                final var rd  = definition.get("rdp", instruction) + 0x8;
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var rs2 = definition.get("rs2p", instruction) + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) & gprFile.getd(rs2));
            }
            case "c.andi" -> {
                final var rd   = definition.get("rdp", instruction) + 0x8;
                final var rs1  = definition.get("rs1p", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) & signExtend(uimm, 6));
            }
            case "c.j" -> {
                final var imm = definition.get("imm", instruction);

                next = pc + signExtend(imm, 12);
            }
            case "c.beqz" -> {
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var imm = definition.get("imm", instruction);

                if (gprFile.getd(rs1) == 0L) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.bnez" -> {
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var imm = definition.get("imm", instruction);

                if (gprFile.getd(rs1) != 0L) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.lwsp" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(0x2) + uimm;

                gprFile.putd(rd, machine.lw(address));
            }
            case "c.ldsp" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(0x2) + uimm;

                gprFile.putd(rd, machine.ld(address));
            }
            case "c.jr" -> {
                final var rs1 = definition.get("rs1", instruction);

                next = gprFile.getd(rs1);
            }
            case "c.mv" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprFile.putd(rd, gprFile.getd(rs2));
            }
            case "c.sdsp" -> {
                final var rs2  = definition.get("rs2", instruction);
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(0x2) + uimm;

                machine.sd(address, gprFile.getd(rs2));
            }
            case "c.or" -> {
                final var rd  = definition.get("rdp", instruction) + 0x8;
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var rs2 = definition.get("rs2p", instruction) + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) | gprFile.getd(rs2));
            }
            case "c.lw" -> {
                final var rd   = definition.get("rdp", instruction) + 0x8;
                final var rs1  = definition.get("rs1p", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(rs1) + uimm;

                gprFile.putd(rd, machine.lw(address));
            }
            case "c.ld" -> {
                final var rd   = definition.get("rdp", instruction) + 0x8;
                final var rs1  = definition.get("rs1p", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(rs1) + uimm;

                gprFile.putd(rd, machine.ld(address));
            }
            case "c.sw" -> {
                final var rs1  = definition.get("rs1p", instruction) + 0x8;
                final var rs2  = definition.get("rs2p", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(rs1) + uimm;

                machine.sw(address, gprFile.getw(rs2));
            }
            case "c.sd" -> {
                final var rs1  = definition.get("rs1p", instruction) + 0x8;
                final var rs2  = definition.get("rs2p", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(rs1) + uimm;

                machine.sd(address, gprFile.getd(rs2));
            }
            case "c.fld" -> {
                final var rd   = definition.get("rdp", instruction) + 0x8;
                final var rs1  = definition.get("rs1p", instruction) + 0x8;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprFile.getd(rs1) + uimm;

                fprFile.putdr(rd, machine.ld(address));
            }

            case "c.addw" -> {
                final var rd  = definition.get("rdp", instruction) + 0x8;
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var rs2 = definition.get("rs2p", instruction) + 0x8;

                gprFile.putd(rd, gprFile.getw(rs1) + gprFile.getw(rs2));
            }
            case "c.subw" -> {
                final var rd  = definition.get("rdp", instruction) + 0x8;
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var rs2 = definition.get("rs2p", instruction) + 0x8;

                gprFile.putd(rd, gprFile.getw(rs1) - gprFile.getw(rs2));
            }

            case "c.sub" -> {
                final var rd  = definition.get("rdp", instruction) + 0x8;
                final var rs1 = definition.get("rs1p", instruction) + 0x8;
                final var rs2 = definition.get("rs2p", instruction) + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) - gprFile.getd(rs2));
            }

            case "c.srli" -> {
                final var rd    = definition.get("rdp", instruction) + 0x8;
                final var rs1   = definition.get("rs1p", instruction) + 0x8;
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) >>> shamt);
            }
            case "c.srai" -> {
                final var rd    = definition.get("rdp", instruction) + 0x8;
                final var rs1   = definition.get("rs1p", instruction) + 0x8;
                final var shamt = definition.get("shamt", instruction);

                gprFile.putd(rd, gprFile.getd(rs1) >> shamt);
            }

            default -> throw new UnsupportedOperationException(definition.toString());
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
