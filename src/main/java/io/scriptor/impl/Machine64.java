package io.scriptor.impl;

import io.scriptor.Log;
import io.scriptor.Machine;
import io.scriptor.MachineLayout;
import io.scriptor.io.IOStream;
import io.scriptor.io.LongByteBuffer;
import io.scriptor.isa.Registry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.scriptor.ByteUtil.*;

public final class Machine64 implements Machine {

    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private final long[] gprs;
    private final long[] csrs;
    private final long pdram;
    private final LongByteBuffer memory;
    private long entry;
    private long pc;
    private long next;
    private boolean wfi;

    public Machine64(final @NotNull MachineLayout layout) {
        this.gprs = new long[layout.gprs()];
        this.csrs = new long[layout.csrs()];
        this.pdram = layout.pdram();
        this.memory = new LongByteBuffer(0x1000, layout.memsz());
    }

    @Override
    public void reset() {
        Arrays.fill(gprs, 0);
        pc = entry;
        next = entry;
        wfi = false;
    }

    @Override
    public void dump(final @NotNull PrintStream out) {

        final var instruction = fetch(true);
        out.printf("pc=%016x, instruction=%08x%n", pc, instruction);

        if (Registry.has(instruction))
            out.printf("  %s%n", Registry.get(instruction));

        for (int i = 0; i < gprs.length; ++i) {
            out.printf("x%-2d: %016x  ", i, gprs[i]);

            if ((i + 1) % 4 == 0) {
                out.println();
            }
        }

        out.printf("mstatus=%x, mtvec=%x, mcause=%x, mepc=%x%n", csrs[0x300], csrs[0x305], csrs[0x342], csrs[0x341]);

        final var sp = gprs[0x2];
        out.printf("stack (sp=%016x):%n", sp);
        for (long offset = -0x10; offset <= 0x10; offset += 0x08) {
            final var address = sp + offset;
            if (address < 0L) {
                continue;
            }

            final long value;
            if (address < pdram) {
                value = 0L;
            } else {
                final var bytes = new byte[8];
                memory.get(address - pdram, bytes, 0, 8);
                value = parseLongLE(bytes);
            }

            out.printf("%016x : %016x%n", address, value);
        }
    }

    @Override
    public boolean step() {
        if (wfi) {
            // TODO: wait for interrupts; until then just end the simulation
            return false;
        }

        pc = next;

        final var instruction = fetch(false);
        final var definition  = Registry.get(instruction);
        final var ilen        = definition.ilen();

        next = pc + ilen;

        switch (definition.mnemonic()) {
            case "fence.i", "c.nop" -> {
                // noop
            }

            case "addi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) + signExtend(imm, 12));
            }
            case "slti" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) < signExtend(imm, 12) ? 1 : 0);
            }
            case "sltiu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, Long.compareUnsigned(gpr(rs1), imm) < 0 ? 1 : 0);
            }

            case "xori" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) ^ signExtend(imm, 12));
            }
            case "ori" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) | signExtend(imm, 12));
            }
            case "andi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) & signExtend(imm, 12));
            }

            case "slli", "c.slli" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gpr(rd, gpr(rs1) << shamt);
            }
            case "srli" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gpr(rd, gpr(rs1) >>> shamt);
            }
            case "srai" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gpr(rd, gpr(rs1) >> shamt);
            }

            case "addiw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, signExtend(gpr(rs1) + signExtend(imm, 12), 32));
            }

            case "slliw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gpr(rd, signExtend(gpr(rs1) << shamt, 32));
            }
            case "srliw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gpr(rd, signExtend(gpr(rs1) >>> shamt, 32));
            }
            case "sraiw" -> {
                final var rd    = definition.get("rd", instruction);
                final var rs1   = definition.get("rs1", instruction);
                final var shamt = definition.get("shamt", instruction);

                gpr(rd, signExtend(gpr(rs1) >> shamt, 32));
            }

            case "add", "c.add" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gpr(rd, gpr(rs1) + gpr(rs2));
            }

            case "sub" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gpr(rd, gpr(rs1) - gpr(rs2));
            }

            case "subw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gpr(rd, signExtend(gpr(rs1) - gpr(rs2), 32));
            }

            case "jal" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                next = pc + signExtend(imm, 21);

                gpr(rd, pc + ilen);
            }

            case "jalr" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                next = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, pc + ilen);
            }

            case "lui" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, imm);
            }
            case "auipc" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, pc + imm);
            }

            case "beq" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gpr(rs1) == gpr(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bne" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gpr(rs1) != gpr(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "blt" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gpr(rs1) < gpr(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bge" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (gpr(rs1) >= gpr(rs2)) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bltu" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (Long.compareUnsigned(gpr(rs1), gpr(rs2)) < 0) {
                    next = pc + signExtend(imm, 13);
                }
            }
            case "bgeu" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                if (Long.compareUnsigned(gpr(rs1), gpr(rs2)) >= 0) {
                    next = pc + signExtend(imm, 13);
                }
            }

            case "lb" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, lb(address));
            }
            case "lh" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, lh(address));
            }
            case "lw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, lw(address));
            }
            case "ld" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, ld(address));
            }
            case "lbu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, lbu(address));
            }
            case "lhu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, lhu(address));
            }
            case "lwu" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                gpr(rd, lwu(address));
            }

            case "sb" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                sb(address, (byte) gpr(rs2));
            }
            case "sh" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                sh(address, (short) gpr(rs2));
            }
            case "sw" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                sw(address, (int) gpr(rs2));
            }
            case "sd" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(rs1) + signExtend(imm, 12);

                sd(address, gpr(rs2));
            }

            case "csrrw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = csr(csr);
                csr(csr, gpr(rs1));
                gpr(rd, value);
            }
            case "csrrwi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = csr(csr);
                csr(csr, rs1);
                gpr(rd, value);
            }
            case "csrrc" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var csr = definition.get("csr", instruction);

                final var value = csr(csr);
                if (rs1 != 0) {
                    final var mask = gpr(rs1);
                    csr(csr, value & ~mask);
                }
                gpr(rd, value);
            }

            case "amoswap.w" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var rs2 = definition.get("rs2", instruction);

                final var address = gpr(rs1);
                final var aligned = address & ~0x3L;

                final long value;
                synchronized (acquireLock(aligned)) {
                    final var source = (int) gpr(rs2);
                    value = lw(aligned);
                    sw(aligned, source);
                }

                gpr(rd, signExtend(value, 32));
            }

            case "wfi" -> {
                wfi = true;
            }

            case "c.addi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) + signExtend(imm, 6));
            }
            case "c.addiw" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, signExtend(gpr(rs1) + signExtend(imm, 6), 32));
            }
            case "c.addi4spn" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(0x2) + imm);
            }
            case "c.li" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, signExtend(imm, 6));
            }
            case "c.addi16sp" -> {
                final var imm = definition.get("imm", instruction);

                gpr(0x2, gpr(0x2) + signExtend(imm, 10));
            }
            case "c.lui" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, signExtend(imm, 18));
            }
            case "c.andi" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                gpr(rd, gpr(rs1) & signExtend(imm, 6));
            }
            case "c.j" -> {
                final var imm = definition.get("imm", instruction);

                next = pc + signExtend(imm, 12);
            }
            case "c.beqz" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                if (gpr(rs1) == 0) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.bnez" -> {
                final var rs1 = definition.get("rs1", instruction);
                final var imm = definition.get("imm", instruction);

                if (gpr(rs1) != 0) {
                    next = pc + signExtend(imm, 9);
                }
            }
            case "c.ldsp" -> {
                final var rd  = definition.get("rd", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(0x2) + imm;

                gpr(rd, ld(address));
            }
            case "c.jr" -> {
                final var rs1 = definition.get("rs1", instruction);

                next = gpr(rs1);
            }
            case "c.mv" -> {
                final var rd  = definition.get("rd", instruction);
                final var rs2 = definition.get("rs2", instruction);

                gpr(rd, gpr(rs2));
            }
            case "c.sdsp" -> {
                final var rs2 = definition.get("rs2", instruction);
                final var imm = definition.get("imm", instruction);

                final var address = gpr(0x2) + imm;

                sd(address, gpr(rs2));
            }

            default -> throw new UnsupportedOperationException(definition.toString());
        }

        return true;
    }

    @Override
    public void setEntry(final long entry) {
        this.entry = entry;
    }

    @Override
    public void loadDirect(final @NotNull IOStream stream) throws IOException {
        stream.read(memory, 0, memory.capacity());
    }

    @Override
    public void loadSegment(
            final @NotNull IOStream stream,
            final long address,
            final long size
    ) throws IOException {
        final var dst = address - pdram;
        stream.read(memory, dst, dst + size);
    }

    @Override
    public int fetch(final boolean unsafe) {
        if (pc < pdram) {
            if (unsafe)
                return 0;
            throw new UnsupportedOperationException("fetch pc=%016x".formatted(pc));
        }

        final var bytes = new byte[4];
        memory.get(pc - pdram, bytes, 0, 4);
        return parseIntLE(bytes);
    }

    @Override
    public int lb(final long address) {
        final var bytes = load(address, 1);
        return signExtend(bytes[0] & 0xff, 8);
    }

    @Override
    public int lbu(final long address) {
        final var bytes = load(address, 1);
        return bytes[0] & 0xff;
    }

    @Override
    public int lh(final long address) {
        return signExtend(parseShortLE(load(address, 2)), 16);
    }

    @Override
    public int lhu(final long address) {
        return parseShortLE(load(address, 2));
    }

    @Override
    public int lw(final long address) {
        return signExtend(parseIntLE(load(address, 4)), 32);
    }

    @Override
    public int lwu(final long address) {
        return parseIntLE(load(address, 4));
    }

    @Override
    public long ld(final long address) {
        return parseLongLE(load(address, 8));
    }

    @Override
    public void sb(final long address, final byte value) {
        store(address, 1, value);
    }

    @Override
    public void sh(final long address, final short value) {
        store(address, 2, (byte) (value & 0xff), (byte) ((value >> 8) & 0xff));
    }

    @Override
    public void sw(final long address, final int value) {
        store(address, 4,
              (byte) (value & 0xff),
              (byte) ((value >> 8) & 0xff),
              (byte) ((value >> 16) & 0xff),
              (byte) ((value >> 24) & 0xff));
    }

    @Override
    public void sd(final long address, final long value) {
        store(address, 8,
              (byte) (value & 0xff),
              (byte) ((value >> 8) & 0xff),
              (byte) ((value >> 16) & 0xff),
              (byte) ((value >> 24) & 0xff),
              (byte) ((value >> 32) & 0xff),
              (byte) ((value >> 40) & 0xff),
              (byte) ((value >> 48) & 0xff),
              (byte) ((value >> 56) & 0xff));
    }

    private @NotNull Object acquireLock(final long address) {
        return locks.computeIfAbsent(address, k -> new Object());
    }

    private long gpr(final int index) {
        if (index == 0)
            return 0L;
        return gprs[index];
    }

    private void gpr(final int index, final long value) {
        if (index == 0)
            return;
        gprs[index] = value;
    }

    private long csr(final int index) {
        return csrs[index];
    }

    private void csr(final int index, final long value) {
        csrs[index] = value;
    }

    private byte @NotNull [] load(final long address, final int n) {
        final var bytes = new byte[n];

        if (address < pdram) {
            // uart rx
            if (address == 0x10000000L && n == 1) {
                try {
                    final var data = System.in.read();
                    bytes[0] = (byte) data;
                } catch (final IOException e) {
                    e.printStackTrace(System.err);
                }
                return bytes;
            }

            // uart lsr
            if (address == 0x10000005L && n == 1) {
                try {
                    final var data = System.in.available() > 0 ? 1 : 0;
                    bytes[0] = (byte) data;
                } catch (final IOException e) {
                    e.printStackTrace(System.err);
                }
                return bytes;
            }

            // TODO: mmio

            Log.warn("load address=%016x n=%d".formatted(address, n));
            return bytes;
        }

        memory.get(address - pdram, bytes, 0, n);
        return bytes;
    }

    private void store(final long address, final int n, final byte @NotNull ... bytes) {
        if (address < pdram) {
            // uart rx
            if (address == 0x10000000L && n == 1) {
                System.out.print((char) bytes[0]);
                return;
            }

            // TODO: mmio

            Log.warn("store address=%016x n=%d".formatted(address, n));
            return;
        }

        memory.put(address - pdram, bytes, 0, bytes.length);
    }
}
