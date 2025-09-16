package io.scriptor;

import io.scriptor.instruction.Instruction;
import io.scriptor.instruction.Instruction.Constructor;
import io.scriptor.instruction.compressed.*;
import io.scriptor.instruction.full.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntPredicate;

import static io.scriptor.Constants.*;

public enum Definition {

    // ---------- RV32I ---------- //

    LUI(MASK_U, OPCODE_LUI, UType::new),
    AUIPC(MASK_U, OPCODE_AUIPC, UType::new),

    JAL(MASK_J, OPCODE_JAL, JType::new),

    JALR(MASK_I, OPCODE_JALR, IType::new),

    BEQ(MASK_B, OPCODE_BRANCH, BType::new),
    BNE(MASK_B, OPCODE_BRANCH | 0b001 << SHIFT_FUNCT3, BType::new),
    BLT(MASK_B, OPCODE_BRANCH | 0b100 << SHIFT_FUNCT3, BType::new),
    BGE(MASK_B, OPCODE_BRANCH | 0b101 << SHIFT_FUNCT3, BType::new),
    BLTU(MASK_B, OPCODE_BRANCH | 0b110 << SHIFT_FUNCT3, BType::new),
    BGEU(MASK_B, OPCODE_BRANCH | 0b111 << SHIFT_FUNCT3, BType::new),

    LB(MASK_I, OPCODE_LOAD, IType::new),
    LH(MASK_I, OPCODE_LOAD | 0b001 << SHIFT_FUNCT3, IType::new),
    LW(MASK_I, OPCODE_LOAD | 0b010 << SHIFT_FUNCT3, IType::new),
    LBU(MASK_I, OPCODE_LOAD | 0b100 << SHIFT_FUNCT3, IType::new),
    LHU(MASK_I, OPCODE_LOAD | 0b101 << SHIFT_FUNCT3, IType::new),

    SB(MASK_S, OPCODE_STORE, SType::new),
    SH(MASK_S, OPCODE_STORE | 0b001 << SHIFT_FUNCT3, SType::new),
    SW(MASK_S, OPCODE_STORE | 0b010 << SHIFT_FUNCT3, SType::new),

    ADDI(MASK_I, OPCODE_OP_IMM, IType::new),
    SLTI(MASK_I, OPCODE_OP_IMM | 0b010 << SHIFT_FUNCT3, IType::new),
    SLTIU(MASK_I, OPCODE_OP_IMM | 0b011 << SHIFT_FUNCT3, IType::new),
    XORI(MASK_I, OPCODE_OP_IMM | 0b100 << SHIFT_FUNCT3, IType::new),
    ORI(MASK_I, OPCODE_OP_IMM | 0b110 << SHIFT_FUNCT3, IType::new),
    ANDI(MASK_I, OPCODE_OP_IMM | 0b111 << SHIFT_FUNCT3, IType::new),

    /**
     * imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
     */
    SLLI(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM | 0b001 << SHIFT_FUNCT3, IType::new),
    /**
     * imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
     */
    SRLI(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM | 0b101 << SHIFT_FUNCT3, IType::new),
    /**
     * imm[11:6] = 0b0100000, imm[5:0] = shamt[5:0]
     */
    SRAI(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, IType::new),

    ADD(MASK_R, OPCODE_OP, RType::new),
    SUB(MASK_R, OPCODE_OP | 0b0100000 << SHIFT_FUNCT7, RType::new),
    SLL(MASK_R, OPCODE_OP | 0b001 << SHIFT_FUNCT3, RType::new),
    SLT(MASK_R, OPCODE_OP | 0b010 << SHIFT_FUNCT3, RType::new),
    SLTU(MASK_R, OPCODE_OP | 0b011 << SHIFT_FUNCT3, RType::new),
    XOR(MASK_R, OPCODE_OP | 0b100 << SHIFT_FUNCT3, RType::new),
    SRL(MASK_R, OPCODE_OP | 0b101 << SHIFT_FUNCT3, RType::new),
    SRA(MASK_R, OPCODE_OP | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, RType::new),
    OR(MASK_R, OPCODE_OP | 0b110 << SHIFT_FUNCT3, RType::new),
    AND(MASK_R, OPCODE_OP | 0b111 << SHIFT_FUNCT3, RType::new),

    // TODO: FENCE, FENCE.TSO, PAUSE, ECALL, EBREAK

    // ---------- RV64I ---------- //

    LWU(MASK_I, OPCODE_LOAD | 0b110 << SHIFT_FUNCT3, IType::new),
    LD(MASK_I, OPCODE_LOAD | 0b011 << SHIFT_FUNCT3, IType::new),

    SD(MASK_S, OPCODE_STORE | 0b011 << SHIFT_FUNCT3, SType::new),

    ADDIW(MASK_I, OPCODE_OP_IMM_32, IType::new),

    /**
     * imm[11:6] = 0b000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SLLIW(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM_32 | 0b001 << SHIFT_FUNCT3, IType::new),
    /**
     * imm[11:6] = 0b000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SRLIW(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM_32 | 0b101 << SHIFT_FUNCT3, IType::new),
    /**
     * imm[11:6] = 0b010000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SRAIW(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM_32 | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, IType::new),

    ADDW(MASK_R, OPCODE_OP_32, RType::new),
    SUBW(MASK_R, OPCODE_OP_32 | 0b0100000 << SHIFT_FUNCT7, RType::new),
    SLLW(MASK_R, OPCODE_OP_32 | 0b001 << SHIFT_FUNCT3, RType::new),
    SRLW(MASK_R, OPCODE_OP_32 | 0b101 << SHIFT_FUNCT3, RType::new),
    SRAW(MASK_R, OPCODE_OP_32 | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, RType::new),

    // ---------- RV32/RV64 Zifencei ---------- //

    // TODO: FENCE.I

    // ---------- RV32/RV64 Zicsr ---------- //

    /**
     * imm = csr
     */
    CSRRW(MASK_I, OPCODE_SYSTEM | 0b001 << SHIFT_FUNCT3, IType::new),
    /**
     * imm = csr
     */
    CSRRS(MASK_I, OPCODE_SYSTEM | 0b010 << SHIFT_FUNCT3, IType::new),
    /**
     * imm = csr
     */
    CSRRC(MASK_I, OPCODE_SYSTEM | 0b011 << SHIFT_FUNCT3, IType::new),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRWI(MASK_I, OPCODE_SYSTEM | 0b101 << SHIFT_FUNCT3, IType::new),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRSI(MASK_I, OPCODE_SYSTEM | 0b110 << SHIFT_FUNCT3, IType::new),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRCI(MASK_I, OPCODE_SYSTEM | 0b111 << SHIFT_FUNCT3, IType::new),

    // ---------- RV32M ---------- //

    MUL(MASK_R, OPCODE_OP | 0b0000001 << SHIFT_FUNCT7, RType::new),
    MULH(MASK_R, OPCODE_OP | 0b001 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    MULHSU(MASK_R, OPCODE_OP | 0b010 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    MULHU(MASK_R, OPCODE_OP | 0b011 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    DIV(MASK_R, OPCODE_OP | 0b100 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    DIVU(MASK_R, OPCODE_OP | 0b101 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    REM(MASK_R, OPCODE_OP | 0b110 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    REMU(MASK_R, OPCODE_OP | 0b111 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),

    // ---------- RV64M ---------- //

    MULW(MASK_R, OPCODE_OP_32 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    DIVW(MASK_R, OPCODE_OP_32 | 0b100 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    DIVUW(MASK_R, OPCODE_OP_32 | 0b101 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    REMW(MASK_R, OPCODE_OP_32 | 0b110 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),
    REMUW(MASK_R, OPCODE_OP_32 | 0b111 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType::new),

    // ---------- RV32A ---------- //

    /**
     * funct7[0] = rl, funct7[1] = aq, rs2 = 0b00000
     */
    LR_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27 | MASK_RS2,
         OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0001000 << SHIFT_FUNCT7,
         RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    SC_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
         OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0001100 << SHIFT_FUNCT7,
         RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOSWAP_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0000100 << SHIFT_FUNCT7,
              RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOADD_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOXOR_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOAND_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0110000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOOR_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
            OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7,
            RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMIN_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1000000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAX_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMINU_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1100000 << SHIFT_FUNCT7,
              RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAXU_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1110000 << SHIFT_FUNCT7,
              RType::new),

    // ---------- RV64A ---------- //

    /**
     * funct7[0] = rl, funct7[1] = aq, rs2 = 0b00000
     */
    LR_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27 | MASK_RS2,
         OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0001000 << SHIFT_FUNCT7,
         RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    SC_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
         OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0001100 << SHIFT_FUNCT7,
         RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOSWAP_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0000100 << SHIFT_FUNCT7,
              RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOADD_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOXOR_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOAND_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0110000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOOR_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
            OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7,
            RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMIN_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1000000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAX_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMINU_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1100000 << SHIFT_FUNCT7,
              RType::new),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAXU_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1110000 << SHIFT_FUNCT7,
              RType::new),

    // ---------- RV32F ---------- //

    FLW(MASK_I, OPCODE_LOAD_FP | 0b010 << SHIFT_FUNCT3, IType::new),
    FSW(MASK_S, OPCODE_STORE_FP | 0b010 << SHIFT_FUNCT3, SType::new),
    /**
     * funct3 = rm
     */
    FMADD_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_MADD, R4Type::new),
    /**
     * funct3 = rm
     */
    FMSUB_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_MSUB, R4Type::new),
    /**
     * funct3 = rm
     */
    FNMSUB_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_NMSUB, R4Type::new),
    /**
     * funct3 = rm
     */
    FNMADD_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_NMADD, R4Type::new),
    /**
     * funct3 = rm
     */
    FADD_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP, RType::new),
    /**
     * funct3 = rm
     */
    FSUB_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP | 0b0000100 << SHIFT_FUNCT7, RType::new),
    /**
     * funct3 = rm
     */
    FMUL_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP | 0b0001000 << SHIFT_FUNCT7, RType::new),
    /**
     * funct3 = rm
     */
    FDIV_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP | 0b0001100 << SHIFT_FUNCT7, RType::new),
    /**
     * funct3 = rm, rs2 = 0b00000
     */
    FSQRT_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
            OPCODE_OP_FP | 0b0101100 << SHIFT_FUNCT7,
            RType::new),
    FSGNJ_S(MASK_R, OPCODE_OP_FP | 0b0010000 << SHIFT_FUNCT7, RType::new),
    FSGNJN_S(MASK_R, OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7, RType::new),
    FSGNJX_S(MASK_R, OPCODE_OP_FP | 0b010 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7, RType::new),
    FMIN_S(MASK_R, OPCODE_OP_FP | 0b0010100 << SHIFT_FUNCT7, RType::new),
    FMAX_S(MASK_R, OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b0010100 << SHIFT_FUNCT7, RType::new),
    /**
     * funct3 = rm, rs2 = 0b00000
     */
    FCVT_W_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct3 = rm, rs2 = 0b00001
     */
    FCVT_WU_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7 | 0b00001 << SHIFT_RS2,
              RType::new),
    /**
     * rs2 = 0b00000
     */
    FMV_X_W(MASK_R | MASK_RS2,
            OPCODE_OP_FP | 0b1110000 << SHIFT_FUNCT7,
            RType::new),
    FEQ_S(MASK_R, OPCODE_OP_FP | 0b010 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7, RType::new),
    FLT_S(MASK_R, OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7, RType::new),
    FLE_S(MASK_R, OPCODE_OP_FP | 0b1010000 << SHIFT_FUNCT7, RType::new),
    /**
     * rs2 = 0b00000
     */
    FCLASS_S(MASK_R | MASK_RS2,
             OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b1110000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct3 = rm, rs2 = 0b00000
     */
    FCVT_S_W(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7,
             RType::new),
    /**
     * funct3 = rm, rs2 = 0b00001
     */
    FCVT_S_WU(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7 | 0b00001 << SHIFT_RS2,
              RType::new),
    /**
     * rs2 = 0b00000
     */
    FMV_W_X(MASK_R | MASK_RS2,
            OPCODE_OP_FP | 0b1111000 << SHIFT_FUNCT7,
            RType::new),

    // ---------- RV64F ---------- //

    /**
     * funct3 = rm, rs2 = 0b00010
     */
    FCVT_L_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7 | 0b00010 << SHIFT_RS2,
             RType::new),
    /**
     * funct3 = rm, rs2 = 0b00011
     */
    FCVT_LU_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7 | 0b00011 << SHIFT_RS2,
              RType::new),
    /**
     * funct3 = rm, rs2 = 0b00010
     */
    FCVT_S_L(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7 | 0b00010 << SHIFT_RS2,
             RType::new),
    /**
     * funct3 = rm, rs2 = 0b00011
     */
    FCVT_S_LU(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7 | 0b00011 << SHIFT_RS2,
              RType::new),

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

    // ---------- RVC ---------- //

    C_ADDI4SPN(0b1110000000000011, 0b0000000000000000, CIWType::new),
    C_FLD(0b1110000000000011, 0b0010000000000000, CLType::new),
    C_LW(0b1110000000000011, 0b0100000000000000, CLType::new),
    C_FLW(0b1110000000000011, 0b0110000000000000, CLType::new),
    C_LD(0b1110000000000011, 0b0110000000000000, CLType::new),
    C_FSD(0b1110000000000011, 0b1010000000000000, CSType::new),
    C_SW(0b1110000000000011, 0b1100000000000000, CSType::new),
    C_FSW(0b1110000000000011, 0b1110000000000000, CSType::new),
    C_SD(0b1110000000000011, 0b1110000000000000, CSType::new),


    C_NOP(0b1110111110000011, 0b0000000000000001, CIType::new),
    C_ADDI(0b1110000000000011, 0b0000000000000001, CIType::new, data -> ((data >> 7) & 0b11111) != 0),
    C_JAL32_ADDIW64(0b1110000000000011, 0b0010000000000001, CIType::new),
    C_LI(0b1110000000000011, 0b0100000000000001, CIType::new),
    C_ADDI16SP(0b1110111110000011, 0b0110000100000001, CIType::new),
    C_LUI(0b1110000000000011, 0b0110000000000001, CIType::new, data -> ((data >> 7) & 0b11111) != 2),
    C_SRLI(0b1110110000000011, 0b1000000000000001, CBType::new),
    C_SRAI(0b1110110000000011, 0b1000010000000001, CBType::new),
    C_ANDI(0b1110110000000011, 0b1000100000000001, CBType::new),
    C_SUB(0b1111110001100011, 0b1000110000000001, CAType::new),
    C_XOR(0b1111110001100011, 0b1000110000100001, CAType::new),
    C_OR(0b1111110001100011, 0b1000110001000001, CAType::new),
    C_AND(0b1111110001100011, 0b1000110001100001, CAType::new),
    C_SUBW(0b1111110001100011, 0b1001110000000001, CAType::new),
    C_ADDW(0b1111110001100011, 0b1001110000100001, CAType::new),
    C_J(0b1110000000000011, 0b1010000000000001, CJType::new),
    C_BEQZ(0b1110000000000011, 0b1100000000000001, CBType::new),
    C_BNEZ(0b1110000000000011, 0b1110000000000001, CBType::new),

    C_SLLI(0b1110000000000011, 0b0000000000000010, CIType::new),
    C_FLDSP(0b1110000000000011, 0b0010000000000010, CIType::new),
    C_LWSP(0b1110000000000011, 0b0100000000000010, CIType::new),
    C_FLWSP32_LDSP64(0b1110000000000011, 0b0110000000000010, CIType::new),
    C_JR(0b1111000001111111, 0b1000000000000010, CRType::new),
    C_MV(0b1111000000000011,
         0b1000000000000010,
         CRType::new,
         data -> ((data >> 7) & 0b11111) != 0 && ((data >> 2) & 0b11111) != 0),
    C_JALR(0b1111000001111111, 0b1001000000000010, CRType::new),
    C_ADD(0b1111000000000011, 0b1001000000000010, CRType::new, data -> ((data >> 2) & 0b11111) != 0),
    C_FSDSP(0b1110000000000011, 0b1010000000000010, CSSType::new),
    C_SWSP(0b1110000000000011, 0b1100000000000010, CSSType::new),
    C_FSWSP32_SDSP64(0b1110000000000011, 0b1110000000000010, CSSType::new),

    // ---------- Privileged ---------- //

    WFI(MASK_OPCODE | MASK_RS1 | MASK_RS2 | 0b111111111111 << 20, OPCODE_SYSTEM | 0b000100000101 << 20, IType::new),

    ;

    private final int mask;
    private final int bits;
    private final Constructor<?> type;
    private final IntPredicate predicate;

    Definition(
            final int mask,
            final int bits,
            final @NotNull Constructor<?> type
    ) {
        this.mask = mask;
        this.bits = bits;
        this.type = type;
        this.predicate = null;
    }

    Definition(
            final int mask,
            final int bits,
            final @NotNull Constructor<?> type,
            final @NotNull IntPredicate predicate
    ) {
        this.mask = mask;
        this.bits = bits;
        this.type = type;
        this.predicate = predicate;
    }

    public boolean filter(final int data) {
        return bits == (data & mask) && (predicate == null || predicate.test(data));
    }

    public @NotNull Instruction instance(final int data) {
        return type.create(data, this);
    }
}
