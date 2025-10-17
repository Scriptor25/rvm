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

    private final GeneralPurposeRegisterFile gprFile;
    private final FloatingPointRegisterFile fprFile;
    private final ControlStatusRegisterFile csrFile;

    private long pc;
    private int priv;
    private boolean wfi;
    private boolean semihosting;

    public HartImpl(final @NotNull Machine machine, final int id) {
        this.machine = machine;

        this.id = id;

        this.mmu = new MMU(this);

        this.gprFile = new GeneralPurposeRegisterFileImpl(this);
        this.fprFile = new FloatingPointRegisterFileImpl(this);
        this.csrFile = new ControlStatusRegisterFileImpl(this);
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
    public void reset(final long entry) {
        gprFile.reset();
        fprFile.reset();
        csrFile.reset();

        pc = entry;
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

        final var clint = machine.device(CLINT.class);

        csrFile.define(time, clint::mtime);
        csrFile.define(stimecmp, () -> clint.mtimecmp(id), val -> clint.mtimecmp(id, val));

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
            final var instruction = fetch(pc, false);
            final var definition  = Registry.get(64, instruction);
            pc = execute(instruction, definition);
        } catch (final TrapException e) {
            if (handle(e.getTrapCause(), e.getTrapValue())) {
                if (e.getId() < 0) {
                    throw new TrapException(id, e);
                }
                throw e;
            }
        }

        interrupt();
    }

    @Override
    public void build(final @NotNull BuilderContext<Device> context, final @NotNull NodeBuilder builder) {
        final var phandle = context.push(this);

        builder.name("cpu@%d".formatted(id))
               .prop(pb -> pb.name("device_type").data("cpu"))
               .prop(pb -> pb.name("reg").data(0x00))
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
        csrFile.putd(mstatus, CSR_M, status);

        final var tvec = target == CSR_M ? csrFile.getd(mtvec, CSR_M) : csrFile.getd(stvec, CSR_S);
        if (tvec == 0L) {
            return true;
        }

        final var base = tvec & ~0b11L;
        final var mode = tvec & 0b11L;

        if (mode == 0b01 && interrupt) {
            pc = base + 4L * code;
        } else {
            pc = base;
        }

        priv = target;
        return false;
    }

    private void interrupt() {
        final var clint = machine.device(CLINT.class);

        if (csrFile.getd(mie, CSR_M) != 0L && clint.mtimecmp(id) != 0L
            && Long.compareUnsigned(clint.mtimecmp(id), clint.mtime()) <= 0) {
            csrFile.putd(mcause, CSR_M, 1L << 63 | 7L);
            csrFile.putd(mepc, CSR_M, pc);
            csrFile.putd(mtval, CSR_M, 0L);

            pc = csrFile.getd(mtvec, CSR_M);
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

            case "ebreak" -> {
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
                    break;
                }

                if (machine.breakpoint(id)) {
                    next = pc;
                    break;
                }

                throw new TrapException(id, 0x03L, pc, "breakpoint instruction");
            }

            case "ecall" -> {
                switch (priv) {
                    case CSR_M -> throw new TrapException(id, 0x0BL, 0, "environment call from machine mode");
                    case CSR_S -> throw new TrapException(id, 0x09L, 0, "environment call from supervisor mode");
                    case CSR_U -> throw new TrapException(id, 0x08L, 0, "environment call from user mode");
                    default -> throw new IllegalStateException();
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

            case "slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                if (rd == 0 && rs1 == 0 && shamt == 0x1F) {
                    semihosting = true;
                    break;
                }

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

                if (rd == 0 && rs1 == 0 && shamt == 0x07) {
                    semihosting = false;
                    break;
                }

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

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, lb(vaddr));
            }
            case "lh" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, lh(vaddr));
            }
            case "lw" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, lw(vaddr));
            }
            case "ld" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, ld(vaddr));
            }
            case "lbu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, lbu(vaddr));
            }
            case "lhu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, lhu(vaddr));
            }
            case "lwu" -> {
                definition.decode(instruction, values, "rd", "rs1", "imm");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                gprFile.putd(rd, lwu(vaddr));
            }

            case "sb" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                sb(vaddr, (byte) gprFile.getd(rs2));
            }
            case "sh" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                sh(vaddr, (short) gprFile.getd(rs2));
            }
            case "sw" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                sw(vaddr, gprFile.getw(rs2));
            }
            case "sd" -> {
                definition.decode(instruction, values, "rs1", "rs2", "imm");

                final var rs1 = values[0];
                final var rs2 = values[1];
                final var imm = values[2];

                final var vaddr = gprFile.getd(rs1) + signExtend(imm, 12);

                sd(vaddr, gprFile.getd(rs2));
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
            case "lr.w" -> {
                definition.decode(instruction, values, "rd", "rs1");

                final var rd  = values[0];
                final var rs1 = values[1];

                final var value = lw(gprFile.getd(rs1));
                gprFile.putd(rd, value);

                // TODO: reservation
            }
            case "sc.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                sw(gprFile.getd(rs1), gprFile.getw(rs2));
                gprFile.putd(rd, 0L);

                // TODO: reservation
            }
            case "lr.d" -> {
                definition.decode(instruction, values, "rd", "rs1");

                final var rd  = values[0];
                final var rs1 = values[1];

                final var value = ld(gprFile.getd(rs1));
                gprFile.putd(rd, value);

                // TODO: reservation
            }
            case "sc.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

                sd(gprFile.getd(rs1), gprFile.getd(rs2));
                gprFile.putd(rd, 0L);

                // TODO: reservation
            }
            case "amoswap.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

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
            case "amoswap.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

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
            case "amoadd.w" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

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
            case "amoadd.d" -> {
                definition.decode(instruction, values, "rd", "rs1", "rs2");

                final var rd  = values[0];
                final var rs1 = values[1];
                final var rs2 = values[2];

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
            case "c.xor" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "rs2p");

                final var rd  = values[0] + 0x8;
                final var rs1 = values[1] + 0x8;
                final var rs2 = values[2] + 0x8;

                gprFile.putd(rd, gprFile.getd(rs1) ^ gprFile.getd(rs2));
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

                final var vaddr = gprFile.getd(0x2) + uimm;

                gprFile.putd(rd, lw(vaddr));
            }
            case "c.ldsp" -> {
                definition.decode(instruction, values, "rd", "uimm");

                final var rd   = values[0];
                final var uimm = values[1];

                final var vaddr = gprFile.getd(0x2) + uimm;

                gprFile.putd(rd, ld(vaddr));
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

                final var vaddr = gprFile.getd(0x2) + uimm;

                sd(vaddr, gprFile.getd(rs2));
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

                final var vaddr = gprFile.getd(rs1) + uimm;

                gprFile.putd(rd, lw(vaddr));
            }
            case "c.ld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");

                final var rd   = values[0] + 0x8;
                final var rs1  = values[1] + 0x8;
                final var uimm = values[2];

                final var vaddr = gprFile.getd(rs1) + uimm;

                gprFile.putd(rd, ld(vaddr));
            }
            case "c.sw" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");

                final var rs1  = values[0] + 0x8;
                final var rs2  = values[1] + 0x8;
                final var uimm = values[2];

                final var vaddr = gprFile.getd(rs1) + uimm;

                sw(vaddr, gprFile.getw(rs2));
            }
            case "c.sd" -> {
                definition.decode(instruction, values, "rs1p", "rs2p", "uimm");

                final var rs1  = values[0] + 0x8;
                final var rs2  = values[1] + 0x8;
                final var uimm = values[2];

                final var vaddr = gprFile.getd(rs1) + uimm;

                sd(vaddr, gprFile.getd(rs2));
            }
            case "c.fld" -> {
                definition.decode(instruction, values, "rdp", "rs1p", "uimm");

                final var rd   = values[0] + 0x8;
                final var rs1  = values[1] + 0x8;
                final var uimm = values[2];

                final var vaddr = gprFile.getd(rs1) + uimm;

                fprFile.putdr(rd, ld(vaddr));
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

            case "c.slli" -> {
                definition.decode(instruction, values, "rd", "rs1", "shamt");

                final var rd    = values[0];
                final var rs1   = values[1];
                final var shamt = values[2];

                gprFile.putd(rd, gprFile.getd(rs1) << shamt);
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
            case "c.ebreak" -> {
                if (machine.breakpoint(id)) {
                    next = pc;
                    break;
                }

                throw new TrapException(id, 0x03L, pc, "breakpoint instruction");
            }
            case "c.fldsp" -> {
                definition.decode(instruction, values, "rd", "uimm");

                final var rd   = values[0];
                final var uimm = values[1];

                final var vaddr = gprFile.getd(0x2) + uimm;

                fprFile.putdr(rd, ld(vaddr));
            }

            case "fmv.w.x" -> {
                definition.decode(instruction, values, "rd", "rs1");

                final var rd  = values[0];
                final var rs1 = values[1];

                fprFile.putfr(rd, gprFile.getw(rs1));
            }
            case "fmv.x.w" -> {
                definition.decode(instruction, values, "rd", "rs1");

                final var rd  = values[0];
                final var rs1 = values[1];

                gprFile.putw(rd, fprFile.getfr(rs1));
            }

            case "sfence.vma" -> {
                definition.decode(instruction, values, "rs1", "rs2");

                final var rs1 = values[0];
                final var rs2 = values[1];

                final var vaddr = gprFile.getd(rs1);
                final var asid  = gprFile.getd(rs2);

                mmu.flush(vaddr, asid);
            }

            default -> throw new TrapException(id, 0x02L, instruction, "unsupported instruction %s", definition);
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
    public @NotNull GeneralPurposeRegisterFile gprFile() {
        return gprFile;
    }

    @Override
    public @NotNull FloatingPointRegisterFile fprFile() {
        return fprFile;
    }

    @Override
    public @NotNull ControlStatusRegisterFile csrFile() {
        return csrFile;
    }

    @Override
    public long translate(final long vaddr, @NotNull MMU.Access access, boolean unsafe) {
        return mmu.translate(priv, vaddr, access, unsafe);
    }
}
