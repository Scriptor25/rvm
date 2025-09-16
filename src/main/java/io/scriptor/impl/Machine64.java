package io.scriptor.impl;

import io.scriptor.Machine;
import io.scriptor.MachineLayout;
import io.scriptor.instruction.CompressedInstruction;
import io.scriptor.io.IOStream;
import io.scriptor.io.LongByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.scriptor.Bytes.*;

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
        out.printf("  %s%n", decode(instruction));

        for (int i = 0; i < gprs.length; ++i) {
            out.printf("x%-2d: %016x  ", i, gprs[i]);

            if ((i + 1) % 4 == 0) {
                out.println();
            }
        }

        out.printf("mstatus=%x, mtvec=%x, mcause=%x, mepc=%x%n", csrs[0x300], csrs[0x305], csrs[0x342], csrs[0x341]);

        final var sp = gprs[0x2];
        out.printf("stack (sp=%016x):%n", sp);
        for (int offset = -0x10; offset <= 0x10; offset += 0x08) {
            final var address = sp + offset;

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

        final var instruction = decode(fetch(false));
        final var definition  = instruction.def();

        final var ilen = ((instruction instanceof CompressedInstruction) ? 2 : 4);

        next = pc + ilen;

        switch (definition) {
            case FENCE_I, C_NOP -> {
                // noop
            }

            case ADDI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs + rhs;
                gpr(instruction.rd(), res);
            }
            case SLTI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs < rhs ? 1 : 0;
                gpr(instruction.rd(), res);
            }
            case SLTIU -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = instruction.imm();
                final var res = Long.compareUnsigned(lhs, rhs) < 0 ? 1 : 0;
                gpr(instruction.rd(), res);
            }

            case XORI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs ^ rhs;
                gpr(instruction.rd(), res);
            }
            case ORI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs | rhs;
                gpr(instruction.rd(), res);
            }
            case ANDI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs & rhs;
                gpr(instruction.rd(), res);
            }

            case SLLI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = instruction.imm() & 0b111111;
                final var res = lhs << rhs;
                gpr(instruction.rd(), res);
            }
            case SRLI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = instruction.imm() & 0b111111;
                final var res = lhs >>> rhs;
                gpr(instruction.rd(), res);
            }
            case SRAI -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = instruction.imm() & 0b111111;
                final var res = lhs >> rhs;
                gpr(instruction.rd(), res);
            }

            case ADDIW -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs + rhs;
                gpr(instruction.rd(), signExtend(res, 32));
            }

            case SLLIW -> {
                final var lhs = (int) gpr(instruction.rs1());
                final var rhs = instruction.imm() & 0b11111;
                final var res = lhs << rhs;
                gpr(instruction.rd(), res);
            }
            case SRLIW -> {
                final var lhs = (int) gpr(instruction.rs1());
                final var rhs = instruction.imm() & 0b11111;
                final var res = lhs >>> rhs;
                gpr(instruction.rd(), res);
            }
            case SRAIW -> {
                final var lhs = (int) gpr(instruction.rs1());
                final var rhs = instruction.imm() & 0b11111;
                final var res = lhs >> rhs;
                gpr(instruction.rd(), res);
            }

            case ADD, C_ADD -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                final var res = lhs + rhs;
                gpr(instruction.rd(), res);
            }

            case SUB -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                final var res = lhs - rhs;
                gpr(instruction.rd(), res);
            }

            case SUBW -> {
                final var lhs = (int) gpr(instruction.rs1());
                final var rhs = (int) gpr(instruction.rs2());
                final var res = lhs - rhs;
                gpr(instruction.rd(), res);
            }

            case JAL -> {
                final var offset = signExtend(instruction.imm(), 21);
                next = pc + offset;
                gpr(instruction.rd(), pc + ilen);
            }

            case JALR -> {
                final var base   = gpr(instruction.rs1());
                final var offset = signExtend(instruction.imm(), 12);
                next = base + offset;
                gpr(instruction.rd(), pc + ilen);
            }

            case LUI -> {
                gpr(instruction.rd(), instruction.imm());
            }
            case AUIPC -> {
                gpr(instruction.rd(), pc + instruction.imm());
            }

            case BEQ -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs == rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BNE -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs != rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BLT -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs < rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BGE -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs >= rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BLTU -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (Long.compareUnsigned(lhs, rhs) < 0) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BGEU -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (Long.compareUnsigned(lhs, rhs) >= 0) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }

            case LB -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lb(address);
                gpr(instruction.rd(), value);
            }
            case LH -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lh(address);
                gpr(instruction.rd(), value);
            }
            case LW -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lw(address);
                gpr(instruction.rd(), value);
            }
            case LD -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = ld(address);
                gpr(instruction.rd(), value);
            }
            case LBU -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lbu(address);
                gpr(instruction.rd(), value);
            }
            case LHU -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lhu(address);
                gpr(instruction.rd(), value);
            }
            case LWU -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lwu(address);
                gpr(instruction.rd(), value);
            }

            case SB -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sb(address, (byte) value);
            }
            case SH -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sh(address, (short) value);
            }
            case SW -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sw(address, (int) value);
            }
            case SD -> {
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sd(address, value);
            }

            case CSRRW -> {
                final var value = csr(instruction.imm());
                csr(instruction.imm(), gpr(instruction.rs1()));
                gpr(instruction.rd(), value);
            }
            case CSRRWI -> {
                final var value = csr(instruction.imm());
                csr(instruction.imm(), instruction.rs1());
                gpr(instruction.rd(), value);
            }
            case CSRRC -> {
                final var value = csr(instruction.imm());
                if (instruction.rs1() != 0) {
                    final var mask = gpr(instruction.rs1());
                    csr(instruction.imm(), value & ~mask);
                }
                gpr(instruction.rd(), value);
            }

            case AMOSWAP_W -> {
                final var address = gpr(instruction.rs1());
                final var aligned = address & ~0x3L;

                final int value;
                synchronized (acquireLock(aligned)) {
                    final var source = (int) gpr(instruction.rs2());
                    value = lw(aligned);

                    sw(aligned, source);
                }

                final var result = signExtend((long) value, 32);
                gpr(instruction.rd(), result);
            }

            case WFI -> {
                wfi = true;
            }

            case C_ADDI -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 5
                                  | ((instruction.data() >> 2) & 0b11111);
                final var se  = signExtend(value, 6);
                final var res = gpr(instruction.rs1()) + se;
                gpr(instruction.rd(), res);
            }
            case C_JAL32_ADDIW64 -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 5
                                  | ((instruction.data() >> 2) & 0b11111);
                final var se  = signExtend(value, 6);
                final var res = gpr(instruction.rs1()) + se;
                gpr(instruction.rd(), signExtend(res, 32));
            }
            case C_ADDI4SPN -> {
                final var value = ((instruction.data() >> 7) & 0b1111) << 6
                                  | ((instruction.data() >> 11) & 0b11) << 4
                                  | ((instruction.data() >> 5) & 0b1) << 3
                                  | ((instruction.data() >> 6) & 0b1) << 2;
                gpr(instruction.rd(), gpr(0x2) + value);
            }
            case C_LI -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 5
                                  | ((instruction.data() >> 2) & 0b11111);
                final var se = signExtend(value, 6);
                gpr(instruction.rd(), se);
            }
            case C_ADDI16SP -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 9
                                  | ((instruction.data() >> 3) & 0b11) << 7
                                  | ((instruction.data() >> 5) & 0b1) << 6
                                  | ((instruction.data() >> 2) & 0b1) << 5
                                  | ((instruction.data() >> 6) & 0b1) << 4;
                final var se = signExtend(value, 10);
                gpr(0x2, gpr(0x2) + se);
            }
            case C_LUI -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 17
                                  | ((instruction.data() >> 2) & 0b11111) << 12;
                final var se = signExtend(value, 18);
                gpr(instruction.rd(), se);
            }
            case C_SLLI -> {
                final var shamt = ((instruction.data() >> 12) & 0b1) << 5
                                  | ((instruction.data() >> 2) & 0b11111);
                final var res = gpr(instruction.rs1()) << shamt;
                gpr(instruction.rd(), res);
            }
            case C_ANDI -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 5
                                  | ((instruction.data() >> 2) & 0b11111);
                final var se  = signExtend(value, 6);
                final var res = gpr(instruction.rs1()) & se;
                gpr(instruction.rd(), res);
            }
            case C_J -> {
                final var value = ((instruction.data() >> 12) & 0b1) << 11
                                  | ((instruction.data() >> 8) & 0b1) << 10
                                  | ((instruction.data() >> 9) & 0b11) << 8
                                  | ((instruction.data() >> 6) & 0b1) << 7
                                  | ((instruction.data() >> 7) & 0b1) << 6
                                  | ((instruction.data() >> 2) & 0b1) << 5
                                  | ((instruction.data() >> 11) & 0b1) << 4
                                  | ((instruction.data() >> 3) & 0b111) << 1;
                final var se = signExtend(value, 12);
                next = pc + se;
            }
            case C_BEQZ -> {
                if (gpr(instruction.rs1()) == 0) {
                    final var value = ((instruction.data() >> 12) & 0b1) << 8
                                      | ((instruction.data() >> 5) & 0b11) << 6
                                      | ((instruction.data() >> 2) & 0b1) << 5
                                      | ((instruction.data() >> 10) & 0b11) << 3
                                      | ((instruction.data() >> 3) & 0b11) << 1;
                    final var se = signExtend(value, 9);
                    next = pc + se;
                }
            }
            case C_BNEZ -> {
                if (gpr(instruction.rs1()) != 0) {
                    final var value = ((instruction.data() >> 12) & 0b1) << 8
                                      | ((instruction.data() >> 5) & 0b11) << 6
                                      | ((instruction.data() >> 2) & 0b1) << 5
                                      | ((instruction.data() >> 10) & 0b11) << 3
                                      | ((instruction.data() >> 3) & 0b11) << 1;
                    final var se = signExtend(value, 9);
                    next = pc + se;
                }
            }
            case C_FLWSP32_LDSP64 -> {
                final var offset = ((instruction.data() >> 2) & 0b111) << 6
                                   | ((instruction.data() >> 12) & 0b1) << 5
                                   | ((instruction.data() >> 5) & 0b11) << 3;
                final var address = gpr(0x2) + offset;
                gpr(instruction.rd(), ld(address));
            }
            case C_JR -> {
                next = gpr(instruction.rs1());
            }
            case C_MV -> {
                final var res = gpr(instruction.rs2());
                gpr(instruction.rd(), res);
            }
            case C_FSWSP32_SDSP64 -> {
                final var offset = ((instruction.data() >> 7) & 0b111) << 6
                                   | ((instruction.data() >> 10) & 0b111) << 3;
                final var address = gpr(0x2) + offset;
                final var value   = gpr(instruction.rs2());
                sd(address, value);
            }

            default -> throw new UnsupportedOperationException("%s: %s".formatted(instruction.def(), instruction));
        }

        return true;
    }

    @Override
    public void setEntry(final long entry) {
        this.entry = entry;
    }

    @Override
    public void loadDirect(final @NotNull IOStream stream) throws IOException {
        stream.read(memory.reset());
        memory.reset();
    }

    @Override
    public void loadSegment(
            final @NotNull IOStream stream,
            final long address,
            final long size
    ) throws IOException {
        final var dst = address - pdram;
        stream.read(memory.range(dst, dst + size));
        memory.reset();
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
            // TODO: mmio

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

            throw new UnsupportedOperationException("load address=%016x (mmio) n=%d".formatted(address, n));
        }

        memory.get(address - pdram, bytes, 0, n);
        return bytes;
    }

    private void store(final long address, final int n, final byte @NotNull ... bytes) {
        if (address < pdram) {
            // TODO: mmio

            // uart rx
            if (address == 0x10000000L && n == 1) {
                System.out.print((char) bytes[0]);
                return;
            }

            throw new UnsupportedOperationException("store address=%016x (mmio) n=%d".formatted(address, n));
        }

        memory.put(address - pdram, bytes, 0, bytes.length);
    }
}
