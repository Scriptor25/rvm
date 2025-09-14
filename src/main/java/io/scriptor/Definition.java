package io.scriptor;

import io.scriptor.instruction.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static io.scriptor.Constants.*;

public enum Definition {

    // ---------- RV32I ---------- //

    LUI(CODE_LUI, UType.class),
    AUIPC(CODE_AUIPC, UType.class),

    JAL(CODE_JAL, JType.class),

    JALR(CODE_JALR, IType.class, instruction -> instruction.func3() == 0b000),

    BEQ(CODE_BRANCH, BType.class, instruction -> instruction.func3() == 0b000),
    BNE(CODE_BRANCH, BType.class, instruction -> instruction.func3() == 0b001),
    BLT(CODE_BRANCH, BType.class, instruction -> instruction.func3() == 0b100),
    BGE(CODE_BRANCH, BType.class, instruction -> instruction.func3() == 0b101),
    BLTU(CODE_BRANCH, BType.class, instruction -> instruction.func3() == 0b110),
    BGEU(CODE_BRANCH, BType.class, instruction -> instruction.func3() == 0b111),

    LB(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b000),
    LH(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b001),
    LW(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b010),
    LBU(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b100),
    LHU(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b101),

    SB(CODE_STORE, SType.class, instruction -> instruction.func3() == 0b000),
    SH(CODE_STORE, SType.class, instruction -> instruction.func3() == 0b001),
    SW(CODE_STORE, SType.class, instruction -> instruction.func3() == 0b010),

    ADDI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b000),
    SLTI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b010),
    SLTIU(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b011),
    XORI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b100),
    ORI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b110),
    ANDI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b111),

    /**
     * imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
     */
    SLLI(CODE_OP_IMM,
         IType.class,
         instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b0000000),
    /**
     * imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
     */
    SRLI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0000000),
    /**
     * imm[11:6] = 0b0100000, imm[5:0] = shamt[5:0]
     */
    SRAI(CODE_OP_IMM, IType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0100000),

    ADD(CODE_OP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b0000000),
    SUB(CODE_OP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b0100000),
    SLL(CODE_OP, RType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b0000000),
    SLT(CODE_OP, RType.class, instruction -> instruction.func3() == 0b010 && instruction.func7() == 0b0000000),
    SLTU(CODE_OP, RType.class, instruction -> instruction.func3() == 0b011 && instruction.func7() == 0b0000000),
    XOR(CODE_OP, RType.class, instruction -> instruction.func3() == 0b100 && instruction.func7() == 0b0000000),
    SRL(CODE_OP, RType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0000000),
    SRA(CODE_OP, RType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0100000),
    OR(CODE_OP, RType.class, instruction -> instruction.func3() == 0b110 && instruction.func7() == 0b0000000),
    AND(CODE_OP, RType.class, instruction -> instruction.func3() == 0b111 && instruction.func7() == 0b0000000),

    // TODO: FENCE, FENCE.TSO, PAUSE, ECALL, EBREAK

    // ---------- RV64I ---------- //

    LWU(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b110),
    LD(CODE_LOAD, IType.class, instruction -> instruction.func3() == 0b011),

    SD(CODE_STORE, SType.class, instruction -> instruction.func3() == 0b011),

    ADDIW(CODE_OP_IMM_32, IType.class, instruction -> instruction.func3() == 0b000),

    /**
     * imm[11:6] = 0b000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SLLIW(CODE_OP_IMM_32, IType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b0000000),
    /**
     * imm[11:6] = 0b000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SRLIW(CODE_OP_IMM_32, IType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0000000),
    /**
     * imm[11:6] = 0b010000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SRAIW(CODE_OP_IMM_32, IType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0100000),

    ADDW(CODE_OP_32, RType.class, instruction -> instruction.func2() == 0b000 && instruction.func7() == 0b0000000),
    SUBW(CODE_OP_32, RType.class, instruction -> instruction.func2() == 0b000 && instruction.func7() == 0b0100000),
    SLLW(CODE_OP_32, RType.class, instruction -> instruction.func2() == 0b001 && instruction.func7() == 0b0000000),
    SRLW(CODE_OP_32, RType.class, instruction -> instruction.func2() == 0b101 && instruction.func7() == 0b0000000),
    SRAW(CODE_OP_32, RType.class, instruction -> instruction.func2() == 0b101 && instruction.func7() == 0b0100000),

    // ---------- RV32/RV64 Zifencei ---------- //

    // TODO: FENCE.I

    // ---------- RV32/RV64 Zicsr ---------- //

    /**
     * imm = csr
     */
    CSRRW(CODE_SYSTEM, IType.class, instruction -> instruction.func3() == 0b001),
    /**
     * imm = csr
     */
    CSRRS(CODE_SYSTEM, IType.class, instruction -> instruction.func3() == 0b010),
    /**
     * imm = csr
     */
    CSRRC(CODE_SYSTEM, IType.class, instruction -> instruction.func3() == 0b011),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRWI(CODE_SYSTEM, IType.class, instruction -> instruction.func3() == 0b101),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRSI(CODE_SYSTEM, IType.class, instruction -> instruction.func3() == 0b110),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRCI(CODE_SYSTEM, IType.class, instruction -> instruction.func3() == 0b111),

    // ---------- RV32M ---------- //

    MUL(CODE_OP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b0000001),
    MULH(CODE_OP, RType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b0000001),
    MULHSU(CODE_OP, RType.class, instruction -> instruction.func3() == 0b010 && instruction.func7() == 0b0000001),
    MULHU(CODE_OP, RType.class, instruction -> instruction.func3() == 0b011 && instruction.func7() == 0b0000001),
    DIV(CODE_OP, RType.class, instruction -> instruction.func3() == 0b100 && instruction.func7() == 0b0000001),
    DIVU(CODE_OP, RType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0000001),
    REM(CODE_OP, RType.class, instruction -> instruction.func3() == 0b110 && instruction.func7() == 0b0000001),
    REMU(CODE_OP, RType.class, instruction -> instruction.func3() == 0b111 && instruction.func7() == 0b0000001),

    // ---------- RV64M ---------- //

    MULW(CODE_OP_32, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b0000001),
    DIVW(CODE_OP_32, RType.class, instruction -> instruction.func3() == 0b100 && instruction.func7() == 0b0000001),
    DIVUW(CODE_OP_32, RType.class, instruction -> instruction.func3() == 0b101 && instruction.func7() == 0b0000001),
    REMW(CODE_OP_32, RType.class, instruction -> instruction.func3() == 0b110 && instruction.func7() == 0b0000001),
    REMUW(CODE_OP_32, RType.class, instruction -> instruction.func3() == 0b111 && instruction.func7() == 0b0000001),

    // ---------- RV32A ---------- //

    /**
     * func7[0] = rl, func7[1] = aq, rs2 = 0b00000
     */
    LR_W(CODE_AMO,
         RType.class,
         instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0001000 && instruction.rs2() == 0b00000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    SC_W(CODE_AMO,
         RType.class,
         instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0001100),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOSWAP_W(CODE_AMO,
              RType.class,
              instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0000100),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOADD_W(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0000000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOXOR_W(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0010000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOAND_W(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0110000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOOR_W(CODE_AMO,
            RType.class,
            instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b0100000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMIN_W(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b1000000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMAX_W(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b1010000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMINU_W(CODE_AMO,
              RType.class,
              instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b1100000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMAXU_W(CODE_AMO,
              RType.class,
              instruction -> instruction.func3() == 0b010 && (instruction.func7() & 0b1111100) == 0b1110000),

    // ---------- RV64A ---------- //

    /**
     * func7[0] = rl, func7[1] = aq, rs2 = 0b00000
     */
    LR_D(CODE_AMO,
         RType.class,
         instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0001000 && instruction.rs2() == 0b00000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    SC_D(CODE_AMO,
         RType.class,
         instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0001100),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOSWAP_D(CODE_AMO,
              RType.class,
              instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0000100),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOADD_D(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0000000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOXOR_D(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0010000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOAND_D(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0110000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOOR_D(CODE_AMO,
            RType.class,
            instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b0100000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMIN_D(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b1000000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMAX_D(CODE_AMO,
             RType.class,
             instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b1010000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMINU_D(CODE_AMO,
              RType.class,
              instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b1100000),
    /**
     * func7[0] = rl, func7[1] = aq
     */
    AMOMAXU_D(CODE_AMO,
              RType.class,
              instruction -> instruction.func3() == 0b011 && (instruction.func7() & 0b1111100) == 0b1110000),

    // ---------- RV32F ---------- //

    FLW(CODE_LOAD_FP, IType.class, instruction -> instruction.func3() == 0b010),
    FSW(CODE_STORE_FP, SType.class, instruction -> instruction.func3() == 0b010),
    /**
     * func3 = rm
     */
    FMADD_S(CODE_MADD, R4Type.class),
    /**
     * func3 = rm
     */
    FMSUB_S(CODE_MSUB, R4Type.class),
    /**
     * func3 = rm
     */
    FNMSUB_S(CODE_NMSUB, R4Type.class),
    /**
     * func3 = rm
     */
    FNMADD_S(CODE_NMADD, R4Type.class),
    /**
     * func3 = rm
     */
    FADD_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b0000000),
    /**
     * func3 = rm
     */
    FSUB_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b0000100),
    /**
     * func3 = rm
     */
    FMUL_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b0001000),
    /**
     * func3 = rm
     */
    FDIV_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b0001100),
    /**
     * func3 = rm, rs2 = 0b00000
     */
    FSQRT_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b0101100 && instruction.rs2() == 0b00000),
    FSGNJ_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b0010000),
    FSGNJN_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b0010000),
    FSGNJX_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b010 && instruction.func7() == 0b0010000),
    FMIN_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b0010100),
    FMAX_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b0010100),
    /**
     * func3 = rm, rs2 = 0b00000
     */
    FCVT_W_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1100000 && instruction.rs2() == 0b00000),
    /**
     * func3 = rm, rs2 = 0b00001
     */
    FCVT_WU_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1100000 && instruction.rs2() == 0b00001),
    /**
     * rs2 = 0b00000
     */
    FMV_X_W(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b1110000),
    FEQ_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b010 && instruction.func7() == 0b1010000),
    FLT_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b1010000),
    FLE_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b1010000),
    /**
     * rs2 = 0b00000
     */
    FCLASS_S(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b001 && instruction.func7() == 0b1110000),
    /**
     * func3 = rm, rs2 = 0b00000
     */
    FCVT_S_W(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1101000 && instruction.rs2() == 0b00000),
    /**
     * func3 = rm, rs2 = 0b00001
     */
    FCVT_S_WU(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1101000 && instruction.rs2() == 0b00001),
    /**
     * rs2 = 0b00000
     */
    FMV_W_X(CODE_OP_FP, RType.class, instruction -> instruction.func3() == 0b000 && instruction.func7() == 0b1111000),

    // ---------- RV64F ---------- //

    /**
     * func3 = rm, rs2 = 0b00010
     */
    FCVT_L_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1100000 && instruction.rs2() == 0b00010),
    /**
     * func3 = rm, rs2 = 0b00011
     */
    FCVT_LU_S(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1100000 && instruction.rs2() == 0b00011),
    /**
     * func3 = rm, rs2 = 0b00010
     */
    FCVT_S_L(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1101000 && instruction.rs2() == 0b00010),
    /**
     * func3 = rm, rs2 = 0b00011
     */
    FCVT_S_LU(CODE_OP_FP, RType.class, instruction -> instruction.func7() == 0b1101000 && instruction.rs2() == 0b00011),

    // ---------- RV32D ---------- //

    // TODO

    // ---------- RV64D ---------- //

    // TODO

    // ---------- RV32Q ---------- //

    // TODO

    // ---------- RV64Q ---------- //

    // TODO

    // ---------- RV32Zfh ---------- //

    // TODO

    // ---------- RV64Zfh ---------- //

    // TODO

    // ---------- Zawrs ---------- //

    // TODO

    // ---------- Privileged ---------- //

    WFI(CODE_SYSTEM,
        IType.class,
        instruction -> instruction.imm() == 0b000100000101 && instruction.rs1() == 0b00000 && instruction.rd() == 0b00000),

    ;

    private final int opcode;
    private final Class<? extends Instruction> type;
    private final Predicate<Instruction> predicate;

    Definition(
            final int opcode,
            final @NotNull Class<? extends Instruction> type,
            final @NotNull Predicate<Instruction> predicate
    ) {
        this.opcode = opcode;
        this.type = type;
        this.predicate = predicate;
    }

    Definition(final int opcode, final @NotNull Class<? extends Instruction> type) {
        this.opcode = opcode;
        this.type = type;
        this.predicate = null;
    }

    public boolean filter(final @NotNull Instruction instruction) {
        if (!this.type.isInstance(instruction))
            return false;
        if (this.opcode != instruction.opcode())
            return false;
        return predicate == null || predicate.test(instruction);
    }
}
