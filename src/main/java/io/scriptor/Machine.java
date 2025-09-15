package io.scriptor;

import io.scriptor.instruction.Instruction;
import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

public interface Machine {

    void reset();

    boolean step();

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
        final var data = peek();

        final var definitions = Arrays.stream(Definition.values())
                                      .filter(definition -> definition.filter(data))
                                      .toList();

        if (definitions.size() != 1) {
            throw new IllegalStateException("ambiguous definitions for data %08x: %s".formatted(data, definitions));
        }

        return definitions.getFirst().instance(data);

        //        final var opcode = Instruction.decode(data);
        //
        //        return switch (opcode) {
        //            case OPCODE_LOAD,
        //                 OPCODE_LOAD_FP,
        //                 OPCODE_OP_IMM,
        //                 OPCODE_OP_IMM_32,
        //                 OPCODE_JALR,
        //                 OPCODE_SYSTEM -> new IType(data);
        //            case OPCODE_AUIPC,
        //                 OPCODE_LUI -> new UType(data);
        //            case OPCODE_STORE,
        //                 OPCODE_STORE_FP -> new SType(data);
        //            case OPCODE_AMO,
        //                 OPCODE_OP,
        //                 OPCODE_OP_32,
        //                 OPCODE_OP_FP -> new RType(data);
        //            case OPCODE_MADD,
        //                 OPCODE_MSUB,
        //                 OPCODE_NMSUB,
        //                 OPCODE_NMADD -> new R4Type(data);
        //            case OPCODE_BRANCH -> new BType(data);
        //            case OPCODE_JAL -> new JType(data);
        //            default -> throw new NoSuchElementException("opcode %02x".formatted(opcode));
        //        };
    }
}
