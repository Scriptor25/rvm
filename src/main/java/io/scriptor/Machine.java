package io.scriptor;

import io.scriptor.io.IOStream;
import io.scriptor.type.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.NoSuchElementException;

import static io.scriptor.Constants.*;

public interface Machine {

    void reset();

    void step();

    void setEntry(final long address);

    void loadDirect(final @NotNull IOStream stream) throws IOException;

    void loadSegment(final @NotNull IOStream stream, final long address, final long size) throws IOException;

    int peek();

    int lb(final long address);

    int lbu(final long address);

    int lh(final long address);

    int lhu(final long address);

    int lw(final long address);

    int lwu(final long address);

    long ld(final long address);

    void sb(final long address, final byte value);

    void sh(final long address, final short value);

    void sw(final long address, final int value);

    void sd(final long address, final long value);

    default @NotNull Instruction decode() {
        final var data   = peek();
        final var opcode = Instruction.decode(data);

        return switch (opcode) {
            case CODE_LOAD,
                 CODE_LOAD_FP,
                 CODE_OP_IMM,
                 CODE_OP_IMM_32,
                 CODE_JALR,
                 CODE_SYSTEM -> new IType(data);
            case CODE_AUIPC,
                 CODE_LUI -> new UType(data);
            case CODE_STORE,
                 CODE_STORE_FP -> new SType(data);
            case CODE_AMO,
                 CODE_OP,
                 CODE_OP_32,
                 CODE_OP_FP -> new RType(data);
            case CODE_MADD,
                 CODE_MSUB,
                 CODE_NMSUB,
                 CODE_NMADD -> new R4Type(data);
            case CODE_BRANCH -> new BType(data);
            case CODE_JAL -> new JType(data);
            default -> throw new NoSuchElementException("opcode %02x".formatted(opcode));
        };
    }
}
