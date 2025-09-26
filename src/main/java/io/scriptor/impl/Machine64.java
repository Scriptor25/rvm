package io.scriptor.impl;

import io.scriptor.elf.SymbolTable;
import io.scriptor.io.IOStream;
import io.scriptor.io.LongByteBuffer;
import io.scriptor.isa.Registry;
import io.scriptor.machine.ControlStatusFile;
import io.scriptor.machine.Machine;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.scriptor.isa.CSR.*;
import static io.scriptor.util.ByteUtil.*;

public final class Machine64 implements Machine {

    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    private final SymbolTable symbols = new SymbolTable();

    private final ControlStatusFile controlStatusFile = new ControlStatusFile64();

    private final long[] gprs;

    private final long dram = 0x80000000L;
    private final LongByteBuffer memory;

    private long entry;
    private long pc;

    private int priv;
    private boolean wfi;

    public Machine64(final long memory) {
        this.gprs = new long[32];

        this.memory = new LongByteBuffer(0x1000, memory);
    }

    private @NotNull Object acquireLock(final long address) {
        return locks.computeIfAbsent(address, _ -> new Object());
    }

    private void printStackTrace(final @NotNull PrintStream out) {
        var ra = gprs[0x1];
        var fp = gprs[0x8];

        out.printf("stack trace (pc=%016x, ra=%016x, fp=%016x):%n", pc, ra, fp);

        {
            final var symbol = symbols.resolve(pc);
            out.printf(" %016x : %s%n", pc, symbol);
        }

        while (fp != 0L) {
            final var symbol = symbols.resolve(ra);
            out.printf(" %016x : %s%n", ra, symbol);

            final var prev_fp = read(fp, 8);
            final var prev_ra = read(fp + 8, 8);

            fp = prev_fp;
            ra = prev_ra;
        }
    }

    @Override
    public long getDRAM() {
        return dram;
    }

    @Override
    public @NotNull SymbolTable getSymbols() {
        return symbols;
    }

    @Override
    public void reset() {
        controlStatusFile.reset();

        Arrays.fill(gprs, 0);

        gprs[0x0A] = 0xDEADBEEFL;

        pc = entry;

        priv = CSR_M;
        wfi = false;

        // machine isa
        controlStatusFile.putd(misa,
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
        controlStatusFile.putd(mvendorid, 0xCAFEBABEL);
        controlStatusFile.putd(marchid, 0x1L);
        controlStatusFile.putd(mimpid, 0x1L);
        controlStatusFile.putd(mhartid, 0x0L);

        // machine status/control
        controlStatusFile.putd(mstatus, 0x1800L);
        controlStatusFile.putd(sstatus, 0L);
        controlStatusFile.putd(medeleg, 0L);
        controlStatusFile.putd(mideleg, 0L);

        // machine interrupt control
        controlStatusFile.putd(mie, 0L);
        controlStatusFile.putd(mip, 0L);
        controlStatusFile.putd(sie, 0L);
        controlStatusFile.putd(sip, 0L);

        // machine trap handling
        controlStatusFile.putd(mtvec, 0L);
        controlStatusFile.putd(stvec, 0L);
        controlStatusFile.putd(mepc, 0L);
        controlStatusFile.putd(sepc, 0L);
        controlStatusFile.putd(mcause, 0L);
        controlStatusFile.putd(scause, 0L);
        controlStatusFile.putd(mtval, 0L);
        controlStatusFile.putd(stval, 0L);
        controlStatusFile.putd(mscratch, 0L);

        // machine virtual memory
        controlStatusFile.putd(satp, 0L);

        // machine counters/timers
        controlStatusFile.putd(time, 0L);
        controlStatusFile.putd(cycle, 0L);
        controlStatusFile.putd(instret, 0L);

        for (int pmpcfgx = pmpcfg0; pmpcfgx <= pmpcfg15; ++pmpcfgx)
            controlStatusFile.putd(pmpcfgx, 0L);

        for (int pmpaddrx = pmpaddr0; pmpaddrx <= pmpaddr63; ++pmpaddrx)
            controlStatusFile.putd(pmpaddrx, 0L);
    }

    @Override
    public void dump(final @NotNull PrintStream out) {

        final var instruction = fetch(true);
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

        for (int i = 0; i < gprs.length; ++i) {
            out.printf("x%-2d: %016x  ", i, gprs[i]);

            if ((i + 1) % 4 == 0) {
                out.println();
            }
        }

        out.printf("mstatus=%x, mtvec=%x, mcause=%x, mepc=%x%n",
                   controlStatusFile.getd(mstatus),
                   controlStatusFile.getd(mtvec),
                   controlStatusFile.getd(mcause),
                   controlStatusFile.getd(mepc));

        final var sp = gprs[0x2];
        out.printf("stack (sp=%016x):%n", sp);
        for (long offset = -0x80; offset <= 0x80; offset += 0x08) {
            final var address = sp + offset;
            if (address < 0L) {
                continue;
            }

            final long value;
            if (address < dram) {
                value = 0L;
            } else {
                final var bytes = new byte[8];
                memory.get(address - dram, bytes, 0, 8);
                value = parseLongLE(bytes);
            }

            out.printf("%016x : %016x%n", address, value);
        }

        printStackTrace(out);
    }

    @Override
    public boolean step() {
        if (wfi) {
            // TODO: wait for interrupts; until then just end the simulation
            return false;
        }

        final var instruction = fetch(false);
        final var definition  = Registry.get(64, instruction);
        final var ilen        = definition.ilen();

        var next = pc + ilen;

        switch (definition.mnemonic()) {
            case "fence.i", "c.nop" -> {
                // noop
            }

            case "addi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, gprd(rs1) + signExtend(imm, 12));
            }
            case "slti" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, gprd(rs1) < signExtend(imm, 12) ? 1 : 0);
            }
            case "sltiu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, Long.compareUnsigned(gprd(rs1), imm) < 0 ? 1 : 0);
            }

            case "xori" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, gprd(rs1) ^ signExtend(imm, 12));
            }
            case "ori" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, gprd(rs1) | signExtend(imm, 12));
            }
            case "andi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, gprd(rs1) & signExtend(imm, 12));
            }

            case "slli", "c.slli" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprd(rd, gprd(rs1) << shamt);
            }
            case "srli" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprd(rd, gprd(rs1) >>> shamt);
            }
            case "srai" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprd(rd, gprd(rs1) >> shamt);
            }

            case "addiw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, signExtend(gprd(rs1) + signExtend(imm, 12), 32));
            }

            case "slliw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprd(rd, signExtend(gprd(rs1) << shamt, 32));
            }
            case "srliw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprd(rd, signExtend(gprd(rs1) >>> shamt, 32));
            }
            case "sraiw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gprd(rd, signExtend(gprd(rs1) >> shamt, 32));
            }

            case "add", "c.add" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprd(rd, gprd(rs1) + gprd(rs2));
            }

            case "sub" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprd(rd, gprd(rs1) - gprd(rs2));
            }

            case "subw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprd(rd, signExtend(gprd(rs1) - gprd(rs2), 32));
            }

            case "jal" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                next = pc + signExtend(imm, 21);

                gprd(rd, pc + ilen);
            }

            case "jalr" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                next = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, pc + ilen);
            }

            case "lui" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, imm);
            }
            case "auipc" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, pc + imm);
            }

            case "beq" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprd(rs1) == gprd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bne" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprd(rs1) != gprd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "blt" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprd(rs1) < gprd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bge" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gprd(rs1) >= gprd(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bltu" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (Long.compareUnsigned(gprd(rs1), gprd(rs2)) < 0) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bgeu" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (Long.compareUnsigned(gprd(rs1), gprd(rs2)) >= 0) {
                    next = pc + signExtend(imm, 13);
                }
            }

            case "lb" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, lb(address));
            }
            case "lh" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, lh(address));
            }
            case "lw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, lw(address));
            }
            case "ld" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, ld(address));
            }
            case "lbu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, lbu(address));
            }
            case "lhu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, lhu(address));
            }
            case "lwu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                gprd(rd, lwu(address));
            }

            case "sb" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                sb(address, (byte) gprd(rs2));
            }
            case "sh" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                sh(address, (short) gprd(rs2));
            }
            case "sw" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                sw(address, (int) gprd(rs2));
            }
            case "sd" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gprd(rs1) + signExtend(imm, 12);

                sd(address, gprd(rs2));
            }

            case "csrrw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = controlStatusFile.getd(csr, priv);
                controlStatusFile.putd(csr, priv, gprd(rs1));
                gprd(rd, value);
            }
            case "csrrwi" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);
                final var csr  = definition.get("csr", instruction);

                final var value = controlStatusFile.getd(csr, priv);
                controlStatusFile.putd(csr, priv, uimm);
                gprd(rd, value);
            }
            case "csrrc" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = controlStatusFile.getd(csr, priv);
                if (rs1 != 0) {
                    final var mask = gprd(rs1);
                    controlStatusFile.putd(csr, priv, value & ~mask);
                }
                gprd(rd, value);
            }

            case "amoswap.w" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                final var address = gprd(rs1);
                final var aligned = address & ~0x3L;

                final long value;
                synchronized (acquireLock(aligned)) {
                    final var source = (int) gprd(rs2);
                    value = lw(aligned);
                    sw(aligned, source);
                }

                gprd(rd, signExtend(value, 32));
            }

            case "wfi" -> {
                wfi = true;
            }

            case "c.addi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, gprd(rs1) + signExtend(imm, 6));
            }
            case "c.addiw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, signExtend(gprd(rs1) + signExtend(imm, 6), 32));
            }
            case "c.addi4spn" -> {
                final var rd   = definition.get("rdp", instruction) + 0x08;
                final var uimm = definition.get("uimm", instruction);

                gprd(rd, gprd(0x2) + uimm);
            }
            case "c.li" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, signExtend(imm, 6));
            }
            case "c.addi16sp" -> {
                final var imm = definition.get("imm", instruction);

                gprd(0x2, gprd(0x2) + signExtend(imm, 10));
            }
            case "c.lui" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gprd(rd, signExtend(imm, 18));
            }
            case "c.andi" -> {
                final var rd   = definition.get("rdp", instruction) + 0x08;
                final var rs1  = definition.get("rs1p", instruction) + 0x08;
                final var uimm = definition.get("uimm", instruction);

                gprd(rd, gprd(rs1) & signExtend(uimm, 6));
            }
            case "c.j" -> {
                final var imm = definition.get("imm", instruction);

                next = pc + signExtend(imm, 12);
            }
            case "c.beqz" -> {
                final var rs1 = definition.get("rs1p", instruction) + 0x08;
                final var imm = definition.get("imm", instruction);

                if (gprd(rs1) == 0L) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.bnez" -> {
                final var rs1 = definition.get("rs1p", instruction) + 0x08;
                final var imm = definition.get("imm", instruction);

                if (gprd(rs1) != 0L) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.ldsp" -> {
                final var rd   = definition.get("rd", instruction);
                final var uimm = definition.get("uimm", instruction);

                final var address = gprd(0x2) + uimm;

                gprd(rd, ld(address));
            }
            case "c.jr" -> {
                final var rs1 = definition.get("rs1", instruction);

                next = gprd(rs1);
            }
            case "c.mv" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gprd(rd, gprd(rs2));
            }
            case "c.sdsp" -> {
                final var rs2  = definition.get("rs2", instruction);
                final var uimm = definition.get("uimm", instruction);

                final var address = gprd(0x2) + uimm;

                sd(address, gprd(rs2));
            }
            case "c.or" -> {
                final var rd  = definition.get("rdp", instruction) + 0x08;
                final var rs1 = definition.get("rs1p", instruction) + 0x08;
                final var rs2 = definition.get("rs2p", instruction) + 0x08;

                gprd(rd, gprd(rs1) | gprd(rs2));
            }
            case "c.ld" -> {
                final var rd   = definition.get("rdp", instruction) + 0x08;
                final var rs1  = definition.get("rs1p", instruction) + 0x08;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprd(rs1) + uimm;

                gprd(rd, ld(address));
            }
            case "c.sd" -> {
                final var rs1  = definition.get("rs1p", instruction) + 0x08;
                final var rs2  = definition.get("rs2p", instruction) + 0x08;
                final var uimm = definition.get("uimm", instruction);

                final var address = gprd(rs1) + uimm;

                sd(address, gprd(rs2));
            }

            default -> throw new UnsupportedOperationException(definition.toString());
        }

        pc = next;
        return true;
    }

    @Override
    public void setEntry(final long entry) {
        if (dram <= entry && entry < dram + memory.capacity()) {
            this.entry = entry;
            return;
        }

        throw new IllegalArgumentException("entry=%016x".formatted(entry));
    }

    @Override
    public void loadDirect(final @NotNull IOStream stream, final long address, final long size, final long allocate)
            throws IOException {
        final var remainder = allocate - size;

        if (0L <= address && address + allocate < memory.capacity()) {
            stream.read(memory, address, size);
            memory.fill(address + size, remainder, (byte) 0);
            return;
        }

        throw new IllegalArgumentException("address=%016x, size=%x, allocate=%x".formatted(address, size, allocate));
    }

    @Override
    public void loadSegment(
            final @NotNull IOStream stream,
            final long address,
            final long size,
            final long allocate
    ) throws IOException {
        final var remainder = allocate - size;

        if (dram <= address && address + allocate < dram + memory.capacity()) {
            final var dst = address - dram;
            stream.read(memory, dst, size);
            memory.fill(dst + size, remainder, (byte) 0);
            return;
        }

        throw new IllegalArgumentException("address=%016x, size=%x, allocate=%x".formatted(address, size, allocate));
    }

    @Override
    public int fetch(final boolean unsafe) {
        if (dram <= pc && pc < dram + memory.capacity()) {
            final var bytes = new byte[4];
            memory.get(pc - dram, bytes, 0, 4);
            return parseIntLE(bytes);
        }

        if (unsafe) {
            return 0;
        }

        throw new IllegalStateException("fetch pc=%016x".formatted(pc));
    }

    @Override
    public int gprw(final int gpr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void gprw(final int gpr, final int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long gprd(final int gpr) {
        if (gpr == 0) {
            return 0L;
        }

        return gprs[gpr];
    }

    @Override
    public void gprd(final int gpr, final long value) {
        if (gpr == 0) {
            return;
        }

        gprs[gpr] = value;
    }

    @Override
    public long read(final long address, final int size) {
        if (dram <= address && address + size < dram + memory.capacity()) {
            final var bytes = new byte[size];
            memory.get(address - dram, bytes, 0, size);

            var value = 0L;
            for (int i = 0; i < size; ++i) {
                value |= (bytes[i] & 0xFFL) << (i * 8);
            }

            return value;
        }

        // uart rx
        if (address == 0x10000000L && size == 1) {
            try {
                return ((long) System.in.read()) & 0xFFL;
            } catch (final IOException e) {
                Log.warn("uart rx read: %s", e);
                return 0L;
            }
        }

        // uart lsr
        if (address == 0x10000005L && size == 1) {
            try {
                return System.in.available() > 0 ? 1L : 0L;
            } catch (final IOException e) {
                Log.warn("uart lsr read: %s", e);
                return 0L;
            }
        }

        dump(System.err);
        Log.warn("read [%016x:%016x]".formatted(address, address + size - 1));
        return 0L;
    }

    @Override
    public void write(final long address, final int size, final long value) {
        if (dram <= address && address + size < dram + memory.capacity()) {
            final var bytes = new byte[size];
            for (int i = 0; i < size; ++i) {
                bytes[i] = (byte) ((value >> (i * 8)) & 0xFF);
            }

            memory.put(address - dram, bytes, 0, size);
            return;
        }

        // uart rx
        if (address == 0x10000000L && size == 1) {
            System.out.print((char) (value & 0xFF));
            return;
        }

        dump(System.err);
        Log.warn("store [%016x:%016x] = %x".formatted(address, address + size - 1, value));
    }
}
