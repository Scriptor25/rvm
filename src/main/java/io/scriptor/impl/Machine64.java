package io.scriptor.impl;

import io.scriptor.Machine;
import io.scriptor.MachineLayout;
import io.scriptor.io.IOStream;
import io.scriptor.io.LongByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;

import static io.scriptor.Bytes.signExtend;

public final class Machine64 implements Machine {

    private static final InputStream stdin = new FileInputStream(FileDescriptor.in);

    private final long[] gprs;
    private final long[] csrs;
    private final LongByteBuffer memory;
    private long entry;
    private long pc;
    private long next;

    public Machine64(final @NotNull MachineLayout layout, final long memory, final @NotNull ByteOrder byteOrder) {
        this.gprs = new long[layout.gprs()];
        this.csrs = new long[layout.csrs()];
        this.memory = new LongByteBuffer(0x1000, memory, byteOrder);
    }

    @Override
    public void reset() {
        Arrays.fill(gprs, 0);
        pc = entry;
        next = entry;
    }

    @Override
    public void step() {
        pc = next;
        next = (pc + 4) % memory.capacity();

        final var instruction = decode();
        final var definition  = instruction.def();

        switch (definition) {
            case ADDI -> {
                // (rd) = (rs1) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                gpr(instruction.rd(), lhs + rhs);
            }

            case SLTI -> {
                // if (rs1) < {imm} then (rd) = 1 else (rd) = 0
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                gpr(instruction.rd(), lhs < rhs ? 1 : 0);
            }
            case SLTIU -> {
                // if (rs1) < imm then (rd) = 1 else (rd) = 0
                final var lhs = gpr(instruction.rs1());
                final var rhs = instruction.imm();
                gpr(instruction.rd(), Long.compareUnsigned(lhs, rhs) < 0 ? 1 : 0);
            }

            case ANDI -> {
                // (rd) = (rs1) & {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                gpr(instruction.rd(), lhs & rhs);
            }
            case ORI -> {
                // (rd) = (rs1) | {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                gpr(instruction.rd(), lhs | rhs);
            }
            case XORI -> {
                // (rd) = (rs1) ^ {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = signExtend(instruction.imm(), 12);
                gpr(instruction.rd(), lhs ^ rhs);
            }

            case ADD -> {
                // (rd) = (rs1) + (rs2)
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                gpr(instruction.rd(), lhs + rhs);
            }

            case JAL -> {
                // (pc') = (pc) + {imm}
                // (rd) = (pc) + 4
                final var offset = signExtend(instruction.imm(), 21);
                offsetPC(offset);
                gpr(instruction.rd(), getPC() + 4);
            }

            case JALR -> {
                // (pc') = (rs1) + {imm}
                // (rd) = (pc) + 4
                final var base   = gpr(instruction.rs1());
                final var offset = signExtend(instruction.imm(), 12);
                setPC(base + offset);
                gpr(instruction.rd(), getPC() + 4);
            }

            case LUI -> {
                // (rd) = imm
                gpr(instruction.rd(), instruction.imm());
            }
            case AUIPC -> {
                // (rd) = (pc) + imm
                gpr(instruction.rd(), getPC() + instruction.imm());
            }

            case BEQ -> {
                // if (rs1) == (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs == rhs) {
                    offsetPC(signExtend(instruction.imm(), 13));
                }
            }
            case BNE -> {
                // if (rs1) != (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs != rhs) {
                    offsetPC(signExtend(instruction.imm(), 13));
                }
            }
            case BLT -> {
                // if (rs1) < (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs < rhs) {
                    offsetPC(signExtend(instruction.imm(), 13));
                }
            }
            case BGE -> {
                // if (rs1) >= (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (lhs >= rhs) {
                    offsetPC(signExtend(instruction.imm(), 13));
                }
            }
            case BLTU -> {
                // if (rs1) < (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (Long.compareUnsigned(lhs, rhs) < 0) {
                    offsetPC(signExtend(instruction.imm(), 13));
                }
            }
            case BGEU -> {
                // if (rs1) >= (rs2) then (pc') = (pc) + {imm}
                final var lhs = gpr(instruction.rs1());
                final var rhs = gpr(instruction.rs2());
                if (Long.compareUnsigned(lhs, rhs) >= 0) {
                    offsetPC(signExtend(instruction.imm(), 13));
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
        stream.read(memory.range(address, address + size));
        memory.reset();
    }

    @Override
    public int peek() {
        return (((int) memory.get(pc + 3) & 0xff) << 24)
               | (((int) memory.get(pc + 2) & 0xff) << 16)
               | (((int) memory.get(pc + 1) & 0xff) << 8)
               | ((int) memory.get(pc) & 0xff);
    }

    @Override
    public int lb(final long address) {
        // TODO: ports

        final var byte0 = memory.get(address);

        return signExtend(byte0 & 0xff, 8);
    }

    @Override
    public int lbu(final long address) {
        // TODO: ports

        if (address == 0x10000000L) {
            try {
                return stdin.read();
            } catch (final IOException e) {
                e.printStackTrace(System.err);
                return 0x00;
            }
        }

        final var byte0 = memory.get(address);

        return byte0 & 0xff;
    }

    @Override
    public int lh(final long address) {
        // TODO: ports

        final var byte0 = memory.get(address);
        final var byte1 = memory.get(address + 1);

        return signExtend((byte1 & 0xff) << 8 | (byte0 & 0xff), 16);
    }

    @Override
    public int lhu(final long address) {
        // TODO: ports

        final var byte0 = memory.get(address);
        final var byte1 = memory.get(address + 1);

        return (byte1 & 0xff) << 8 | (byte0 & 0xff);
    }

    @Override
    public int lw(final long address) {
        // TODO: ports

        final var byte0 = memory.get(address);
        final var byte1 = memory.get(address + 1);
        final var byte2 = memory.get(address + 2);
        final var byte3 = memory.get(address + 3);

        return signExtend((byte3 & 0xff) << 24 | (byte2 & 0xff) << 16 | (byte1 & 0xff) << 8 | (byte0 & 0xff), 32);
    }

    @Override
    public int lwu(final long address) {
        // TODO: ports

        final var byte0 = memory.get(address);
        final var byte1 = memory.get(address + 1);
        final var byte2 = memory.get(address + 2);
        final var byte3 = memory.get(address + 3);

        return (byte3 & 0xff) << 24 | (byte2 & 0xff) << 16 | (byte1 & 0xff) << 8 | (byte0 & 0xff);
    }

    @Override
    public long ld(final long address) {
        // TODO: ports

        final var byte0 = memory.get(address);
        final var byte1 = memory.get(address + 1);
        final var byte2 = memory.get(address + 2);
        final var byte3 = memory.get(address + 3);
        final var byte4 = memory.get(address + 4);
        final var byte5 = memory.get(address + 5);
        final var byte6 = memory.get(address + 6);
        final var byte7 = memory.get(address + 7);

        return (byte7 & 0xffL) << 56L
               | (byte6 & 0xffL) << 48L
               | (byte5 & 0xffL) << 40L
               | (byte4 & 0xffL) << 32L
               | (byte3 & 0xffL) << 24L
               | (byte2 & 0xffL) << 16L
               | (byte1 & 0xffL) << 8L
               | (byte0 & 0xffL);
    }

    @Override
    public void sb(final long address, final byte value) {
        // TODO: ports

        if (address == 0x10000000L) {
            if (value >= 0x20)
                System.out.printf("%c", value);
            return;
        }

        memory.put(address, value);
    }

    @Override
    public void sh(final long address, final short value) {
        // TODO: ports

        memory.put(address, (byte) (value & 0xff));
        memory.put(address + 1, (byte) ((value >> 8) & 0xff));
    }

    @Override
    public void sw(final long address, final int value) {
        // TODO: ports

        memory.put(address, (byte) (value & 0xff));
        memory.put(address + 1, (byte) ((value >> 8) & 0xff));
        memory.put(address + 2, (byte) ((value >> 16) & 0xff));
        memory.put(address + 3, (byte) ((value >> 24) & 0xff));
    }

    @Override
    public void sd(final long address, final long value) {
        // TODO: ports

        memory.put(address, (byte) (value & 0xff));
        memory.put(address + 1, (byte) ((value >> 8) & 0xff));
        memory.put(address + 2, (byte) ((value >> 16) & 0xff));
        memory.put(address + 3, (byte) ((value >> 24) & 0xff));
        memory.put(address + 4, (byte) ((value >> 32) & 0xff));
        memory.put(address + 5, (byte) ((value >> 40) & 0xff));
        memory.put(address + 6, (byte) ((value >> 48) & 0xff));
        memory.put(address + 7, (byte) ((value >> 56) & 0xff));
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

    private long getPC() {
        return pc;
    }

    private void setPC(final long address) {
        next = address % memory.capacity();
    }

    private void offsetPC(final long offset) {
        next = (pc + offset) % memory.capacity();
    }
}
