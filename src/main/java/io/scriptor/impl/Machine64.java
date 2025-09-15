package io.scriptor.impl;

import io.scriptor.Machine;
import io.scriptor.MachineLayout;
import io.scriptor.instruction.CompressedInstruction;
import io.scriptor.io.IOStream;
import io.scriptor.io.LongByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

import static io.scriptor.Bytes.*;

public final class Machine64 implements Machine {

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
    public boolean step() {
        if (wfi) {
            // TODO: wait for interrupts; until then just end the simulation
            return false;
        }

        pc = next;

        final var instruction = decode();
        final var definition  = instruction.def();

        final var ilen = ((instruction instanceof CompressedInstruction) ? 2 : 4);

        next = pc + ilen;

        switch (definition) {
            case ADDI -> {
                // (rd) = (rs1) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs + rhs;
                gpr(instruction.rd(), res);
            }

            case SLTI -> {
                // if (rs1) < {imm} then (rd) = 1 else (rd) = 0
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs < rhs ? 1 : 0;
                gpr(instruction.rd(), res);
            }
            case SLTIU -> {
                // if (rs1) < imm then (rd) = 1 else (rd) = 0
                final var lhs = gpr(instruction.rs1());
                final var rhs = instruction.imm();
                final var res = Long.compareUnsigned(lhs, rhs) < 0 ? 1 : 0;
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

            case ANDI -> {
                // (rd) = (rs1) & {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs & rhs;
                gpr(instruction.rd(), res);
            }
            case ORI -> {
                // (rd) = (rs1) | {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs | rhs;
                gpr(instruction.rd(), res);
            }
            case XORI -> {
                // (rd) = (rs1) ^ {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs ^ rhs;
                gpr(instruction.rd(), res);
            }

            case ADDIW -> {
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                final var res = lhs + rhs;
                gpr(instruction.rd(), res);
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

            case ADD -> {
                // (rd) = (rs1) + (rs2)
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                final var res = lhs + rhs;
                gpr(instruction.rd(), res);
            }

            case SUBW -> {
                final var lhs = (int) gpr(instruction.rs1());
                final var rhs = (int) gpr(instruction.rs2());
                final var res = lhs - rhs;
                gpr(instruction.rd(), res);
            }

            case JAL -> {
                // (pc') = (pc) + {imm}
                // (rd) = (pc) + 4
                final var offset = signExtend(instruction.imm(), 21);
                next = pc + offset;
                gpr(instruction.rd(), pc + ilen);
            }

            case JALR -> {
                // (pc') = (rs1) + {imm}
                // (rd) = (pc) + 4
                final var base   = gpr(instruction.rs1());
                final var offset = signExtend(instruction.imm(), 12);
                next = base + offset;
                gpr(instruction.rd(), pc + ilen);
            }

            case LUI -> {
                // (rd) = imm
                gpr(instruction.rd(), instruction.imm());
            }
            case AUIPC -> {
                // (rd) = (pc) + imm
                gpr(instruction.rd(), pc + instruction.imm());
            }

            case BEQ -> {
                // if (rs1) == (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs == rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BNE -> {
                // if (rs1) != (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs != rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BLT -> {
                // if (rs1) < (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs < rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BGE -> {
                // if (rs1) >= (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs >= rhs) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BLTU -> {
                // if (rs1) < (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (Long.compareUnsigned(lhs, rhs) < 0) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }
            case BGEU -> {
                // if (rs1) >= (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (Long.compareUnsigned(lhs, rhs) >= 0) {
                    next = pc + signExtend(instruction.imm(), 13);
                }
            }

            case CSRRW -> {
                // old = (csr)
                // (csr) = (rs1)
                // (rd) = old
                final var val = csr(instruction.imm());
                csr(instruction.imm(), gpr(instruction.rs1()));
                gpr(instruction.rs1(), val);

                // TODO: trigger side effect for csr
            }

            case WFI -> {
                // wait for interrupts
                wfi = true;
            }

            case SB -> {
                // [(rs1) + {imm}] = (rs2)
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sb(address, (byte) value);
            }
            case SH -> {
                // [(rs1) + {imm}] = (rs2)
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sh(address, (short) value);
            }
            case SW -> {
                // [(rs1) + {imm}] = (rs2)
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sw(address, (int) value);
            }
            case SD -> {
                // [(rs1) + {imm}] = (rs2)
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = gpr(instruction.rs2());
                sd(address, value);
            }

            case LB -> {
                // (rd) = {[(rs1) + {imm}]}
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lb(address);
                gpr(instruction.rd(), value);
            }
            case LH -> {
                // (rd) = {[(rs1) + {imm}]}
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lh(address);
                gpr(instruction.rd(), value);
            }
            case LW -> {
                // (rd) = {[(rs1) + {imm}]}
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lw(address);
                gpr(instruction.rd(), value);
            }
            case LD -> {
                // (rd) = {[(rs1) + {imm}]}
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = ld(address);
                gpr(instruction.rd(), value);
            }
            case LBU -> {
                // (rd) = [(rs1) + {imm}]
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lbu(address);
                gpr(instruction.rd(), value);
            }
            case LHU -> {
                // (rd) = [(rs1) + {imm}]
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lhu(address);
                gpr(instruction.rd(), value);
            }
            case LWU -> {
                // (rd) = [(rs1) + {imm}]
                final var address = gpr(instruction.rs1()) + signExtend(instruction.imm(), 12);
                final var value   = lwu(address);
                gpr(instruction.rd(), value);
            }

            default -> throw new UnsupportedOperationException(instruction.toString());
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
    public int peek() {
        return lwu(pc);
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

            throw new UnsupportedOperationException();
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

            throw new UnsupportedOperationException();
        }

        memory.put(address - pdram, bytes, 0, bytes.length);
    }
}
