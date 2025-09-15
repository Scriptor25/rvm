package io.scriptor;

import io.scriptor.instruction.Instruction;
import io.scriptor.instruction.compressed.*;
import io.scriptor.instruction.full.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

import static io.scriptor.Constants.*;

public enum Definition {

    // ---------- RV32I ---------- //

    LUI(MASK_U, OPCODE_LUI, UType.class),
    AUIPC(MASK_U, OPCODE_AUIPC, UType.class),

    JAL(MASK_J, OPCODE_JAL, JType.class),

    JALR(MASK_I, OPCODE_JALR, IType.class),

    BEQ(MASK_B, OPCODE_BRANCH, BType.class),
    BNE(MASK_B, OPCODE_BRANCH | 0b001 << SHIFT_FUNCT3, BType.class),
    BLT(MASK_B, OPCODE_BRANCH | 0b100 << SHIFT_FUNCT3, BType.class),
    BGE(MASK_B, OPCODE_BRANCH | 0b101 << SHIFT_FUNCT3, BType.class),
    BLTU(MASK_B, OPCODE_BRANCH | 0b110 << SHIFT_FUNCT3, BType.class),
    BGEU(MASK_B, OPCODE_BRANCH | 0b111 << SHIFT_FUNCT3, BType.class),

    LB(MASK_I, OPCODE_LOAD, IType.class),
    LH(MASK_I, OPCODE_LOAD | 0b001 << SHIFT_FUNCT3, IType.class),
    LW(MASK_I, OPCODE_LOAD | 0b010 << SHIFT_FUNCT3, IType.class),
    LBU(MASK_I, OPCODE_LOAD | 0b100 << SHIFT_FUNCT3, IType.class),
    LHU(MASK_I, OPCODE_LOAD | 0b101 << SHIFT_FUNCT3, IType.class),

    SB(MASK_S, OPCODE_STORE, SType.class),
    SH(MASK_S, OPCODE_STORE | 0b001 << SHIFT_FUNCT3, SType.class),
    SW(MASK_S, OPCODE_STORE | 0b010 << SHIFT_FUNCT3, SType.class),

    ADDI(MASK_I, OPCODE_OP_IMM, IType.class),
    SLTI(MASK_I, OPCODE_OP_IMM | 0b010 << SHIFT_FUNCT3, IType.class),
    SLTIU(MASK_I, OPCODE_OP_IMM | 0b011 << SHIFT_FUNCT3, IType.class),
    XORI(MASK_I, OPCODE_OP_IMM | 0b100 << SHIFT_FUNCT3, IType.class),
    ORI(MASK_I, OPCODE_OP_IMM | 0b110 << SHIFT_FUNCT3, IType.class),
    ANDI(MASK_I, OPCODE_OP_IMM | 0b111 << SHIFT_FUNCT3, IType.class),

    /**
     * imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
     */
    SLLI(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM | 0b001 << SHIFT_FUNCT3, IType.class),
    /**
     * imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
     */
    SRLI(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM | 0b101 << SHIFT_FUNCT3, IType.class),
    /**
     * imm[11:6] = 0b0100000, imm[5:0] = shamt[5:0]
     */
    SRAI(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, IType.class),

    ADD(MASK_R, OPCODE_OP, RType.class),
    SUB(MASK_R, OPCODE_OP | 0b0100000 << SHIFT_FUNCT7, RType.class),
    SLL(MASK_R, OPCODE_OP | 0b001 << SHIFT_FUNCT3, RType.class),
    SLT(MASK_R, OPCODE_OP | 0b010 << SHIFT_FUNCT3, RType.class),
    SLTU(MASK_R, OPCODE_OP | 0b011 << SHIFT_FUNCT3, RType.class),
    XOR(MASK_R, OPCODE_OP | 0b100 << SHIFT_FUNCT3, RType.class),
    SRL(MASK_R, OPCODE_OP | 0b101 << SHIFT_FUNCT3, RType.class),
    SRA(MASK_R, OPCODE_OP | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, RType.class),
    OR(MASK_R, OPCODE_OP | 0b110 << SHIFT_FUNCT3, RType.class),
    AND(MASK_R, OPCODE_OP | 0b111 << SHIFT_FUNCT3, RType.class),

    // TODO: FENCE, FENCE.TSO, PAUSE, ECALL, EBREAK

    // ---------- RV64I ---------- //

    LWU(MASK_I, OPCODE_LOAD | 0b110 << SHIFT_FUNCT3, IType.class),
    LD(MASK_I, OPCODE_LOAD | 0b011 << SHIFT_FUNCT3, IType.class),

    SD(MASK_S, OPCODE_STORE | 0b011 << SHIFT_FUNCT3, SType.class),

    ADDIW(MASK_I, OPCODE_OP_IMM_32, IType.class),

    /**
     * imm[11:6] = 0b000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SLLIW(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM_32 | 0b001 << SHIFT_FUNCT3, IType.class),
    /**
     * imm[11:6] = 0b000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SRLIW(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM_32 | 0b101 << SHIFT_FUNCT3, IType.class),
    /**
     * imm[11:6] = 0b010000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
     */
    SRAIW(MASK_I | MASK_FUNCT7, OPCODE_OP_IMM_32 | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, IType.class),

    ADDW(MASK_R, OPCODE_OP_32, RType.class),
    SUBW(MASK_R, OPCODE_OP_32 | 0b0100000 << SHIFT_FUNCT7, RType.class),
    SLLW(MASK_R, OPCODE_OP_32 | 0b001 << SHIFT_FUNCT3, RType.class),
    SRLW(MASK_R, OPCODE_OP_32 | 0b101 << SHIFT_FUNCT3, RType.class),
    SRAW(MASK_R, OPCODE_OP_32 | 0b101 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7, RType.class),

    // ---------- RV32/RV64 Zifencei ---------- //

    // TODO: FENCE.I

    // ---------- RV32/RV64 Zicsr ---------- //

    /**
     * imm = csr
     */
    CSRRW(MASK_I, OPCODE_SYSTEM | 0b001 << SHIFT_FUNCT3, IType.class),
    /**
     * imm = csr
     */
    CSRRS(MASK_I, OPCODE_SYSTEM | 0b010 << SHIFT_FUNCT3, IType.class),
    /**
     * imm = csr
     */
    CSRRC(MASK_I, OPCODE_SYSTEM | 0b011 << SHIFT_FUNCT3, IType.class),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRWI(MASK_I, OPCODE_SYSTEM | 0b101 << SHIFT_FUNCT3, IType.class),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRSI(MASK_I, OPCODE_SYSTEM | 0b110 << SHIFT_FUNCT3, IType.class),
    /**
     * imm = csr, rs1[4:0] = uimm[4:0]
     */
    CSRRCI(MASK_I, OPCODE_SYSTEM | 0b111 << SHIFT_FUNCT3, IType.class),

    // ---------- RV32M ---------- //

    MUL(MASK_R, OPCODE_OP | 0b0000001 << SHIFT_FUNCT7, RType.class),
    MULH(MASK_R, OPCODE_OP | 0b001 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    MULHSU(MASK_R, OPCODE_OP | 0b010 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    MULHU(MASK_R, OPCODE_OP | 0b011 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    DIV(MASK_R, OPCODE_OP | 0b100 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    DIVU(MASK_R, OPCODE_OP | 0b101 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    REM(MASK_R, OPCODE_OP | 0b110 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    REMU(MASK_R, OPCODE_OP | 0b111 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),

    // ---------- RV64M ---------- //

    MULW(MASK_R, OPCODE_OP_32 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    DIVW(MASK_R, OPCODE_OP_32 | 0b100 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    DIVUW(MASK_R, OPCODE_OP_32 | 0b101 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    REMW(MASK_R, OPCODE_OP_32 | 0b110 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),
    REMUW(MASK_R, OPCODE_OP_32 | 0b111 << SHIFT_FUNCT3 | 0b0000001 << SHIFT_FUNCT7, RType.class),

    // ---------- RV32A ---------- //

    /**
     * funct7[0] = rl, funct7[1] = aq, rs2 = 0b00000
     */
    LR_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27 | MASK_RS2,
         OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0001000 << SHIFT_FUNCT7,
         RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    SC_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
         OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0001100 << SHIFT_FUNCT7,
         RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOSWAP_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0000100 << SHIFT_FUNCT7,
              RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOADD_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOXOR_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOAND_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0110000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOOR_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
            OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7,
            RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMIN_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1000000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAX_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMINU_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1100000 << SHIFT_FUNCT7,
              RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAXU_W(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b010 << SHIFT_FUNCT3 | 0b1110000 << SHIFT_FUNCT7,
              RType.class),

    // ---------- RV64A ---------- //

    /**
     * funct7[0] = rl, funct7[1] = aq, rs2 = 0b00000
     */
    LR_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27 | MASK_RS2,
         OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0001000 << SHIFT_FUNCT7,
         RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    SC_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
         OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0001100 << SHIFT_FUNCT7,
         RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOSWAP_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0000100 << SHIFT_FUNCT7,
              RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOADD_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOXOR_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOAND_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0110000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOOR_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
            OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b0100000 << SHIFT_FUNCT7,
            RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMIN_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1000000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAX_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
             OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMINU_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1100000 << SHIFT_FUNCT7,
              RType.class),
    /**
     * funct7[0] = rl, funct7[1] = aq
     */
    AMOMAXU_D(MASK_OPCODE | MASK_FUNCT3 | 0b11111 << 27,
              OPCODE_AMO | 0b011 << SHIFT_FUNCT3 | 0b1110000 << SHIFT_FUNCT7,
              RType.class),

    // ---------- RV32F ---------- //

    FLW(MASK_I, OPCODE_LOAD_FP | 0b010 << SHIFT_FUNCT3, IType.class),
    FSW(MASK_S, OPCODE_STORE_FP | 0b010 << SHIFT_FUNCT3, SType.class),
    /**
     * funct3 = rm
     */
    FMADD_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_MADD, R4Type.class),
    /**
     * funct3 = rm
     */
    FMSUB_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_MSUB, R4Type.class),
    /**
     * funct3 = rm
     */
    FNMSUB_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_NMSUB, R4Type.class),
    /**
     * funct3 = rm
     */
    FNMADD_S(MASK_OPCODE | MASK_FUNCT2, OPCODE_NMADD, R4Type.class),
    /**
     * funct3 = rm
     */
    FADD_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP, RType.class),
    /**
     * funct3 = rm
     */
    FSUB_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP | 0b0000100 << SHIFT_FUNCT7, RType.class),
    /**
     * funct3 = rm
     */
    FMUL_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP | 0b0001000 << SHIFT_FUNCT7, RType.class),
    /**
     * funct3 = rm
     */
    FDIV_S(MASK_OPCODE | MASK_FUNCT7, OPCODE_OP_FP | 0b0001100 << SHIFT_FUNCT7, RType.class),
    /**
     * funct3 = rm, rs2 = 0b00000
     */
    FSQRT_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
            OPCODE_OP_FP | 0b0101100 << SHIFT_FUNCT7,
            RType.class),
    FSGNJ_S(MASK_R, OPCODE_OP_FP | 0b0010000 << SHIFT_FUNCT7, RType.class),
    FSGNJN_S(MASK_R, OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7, RType.class),
    FSGNJX_S(MASK_R, OPCODE_OP_FP | 0b010 << SHIFT_FUNCT3 | 0b0010000 << SHIFT_FUNCT7, RType.class),
    FMIN_S(MASK_R, OPCODE_OP_FP | 0b0010100 << SHIFT_FUNCT7, RType.class),
    FMAX_S(MASK_R, OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b0010100 << SHIFT_FUNCT7, RType.class),
    /**
     * funct3 = rm, rs2 = 0b00000
     */
    FCVT_W_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct3 = rm, rs2 = 0b00001
     */
    FCVT_WU_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7 | 0b00001 << SHIFT_RS2,
              RType.class),
    /**
     * rs2 = 0b00000
     */
    FMV_X_W(MASK_R | MASK_RS2,
            OPCODE_OP_FP | 0b1110000 << SHIFT_FUNCT7,
            RType.class),
    FEQ_S(MASK_R, OPCODE_OP_FP | 0b010 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7, RType.class),
    FLT_S(MASK_R, OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b1010000 << SHIFT_FUNCT7, RType.class),
    FLE_S(MASK_R, OPCODE_OP_FP | 0b1010000 << SHIFT_FUNCT7, RType.class),
    /**
     * rs2 = 0b00000
     */
    FCLASS_S(MASK_R | MASK_RS2,
             OPCODE_OP_FP | 0b001 << SHIFT_FUNCT3 | 0b1110000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct3 = rm, rs2 = 0b00000
     */
    FCVT_S_W(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7,
             RType.class),
    /**
     * funct3 = rm, rs2 = 0b00001
     */
    FCVT_S_WU(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7 | 0b00001 << SHIFT_RS2,
              RType.class),
    /**
     * rs2 = 0b00000
     */
    FMV_W_X(MASK_R | MASK_RS2,
            OPCODE_OP_FP | 0b1111000 << SHIFT_FUNCT7,
            RType.class),

    // ---------- RV64F ---------- //

    /**
     * funct3 = rm, rs2 = 0b00010
     */
    FCVT_L_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7 | 0b00010 << SHIFT_RS2,
             RType.class),
    /**
     * funct3 = rm, rs2 = 0b00011
     */
    FCVT_LU_S(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1100000 << SHIFT_FUNCT7 | 0b00011 << SHIFT_RS2,
              RType.class),
    /**
     * funct3 = rm, rs2 = 0b00010
     */
    FCVT_S_L(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
             OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7 | 0b00010 << SHIFT_RS2,
             RType.class),
    /**
     * funct3 = rm, rs2 = 0b00011
     */
    FCVT_S_LU(MASK_OPCODE | MASK_FUNCT7 | MASK_RS2,
              OPCODE_OP_FP | 0b1101000 << SHIFT_FUNCT7 | 0b00011 << SHIFT_RS2,
              RType.class),

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

    C_LWSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_LWSP << 13, CIType.class),
    C_LDSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_LDSP << 13, CIType.class),
    C_FLWSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_FLWSP << 13, CIType.class),
    C_FLDSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_FLDSP << 13, CIType.class),

    C_SWSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_SWSP << 13, CSSType.class),
    C_SDSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_SDSP << 13, CSSType.class),
    C_FSWSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_FSWSP << 13, CSSType.class),
    C_FSDSP(0b1110000000000011, RVC_OPCODE_C2 | RVC_FSDSP << 13, CSSType.class),

    C_LW(0b1110000000000011, RVC_OPCODE_C0 | RVC_LW << 13, CLType.class),
    C_LD(0b1110000000000011, RVC_OPCODE_C0 | RVC_LD << 13, CLType.class),
    C_FLW(0b1110000000000011, RVC_OPCODE_C0 | RVC_FLW << 13, CLType.class),
    C_FLD(0b1110000000000011, RVC_OPCODE_C0 | RVC_FLD << 13, CLType.class),

    C_SW(0b1110000000000011, RVC_OPCODE_C0 | RVC_SW << 13, CSType.class),
    C_SD(0b1110000000000011, RVC_OPCODE_C0 | RVC_SD << 13, CSType.class),
    C_FSW(0b1110000000000011, RVC_OPCODE_C0 | RVC_FSW << 13, CSType.class),
    C_FSD(0b1110000000000011, RVC_OPCODE_C0 | RVC_FSD << 13, CSType.class),

    C_J(0b1110000000000011, RVC_OPCODE_C1 | RVC_J << 13, CJType.class),
    C_JAL(0b1110000000000011, RVC_OPCODE_C1 | RVC_JAL << 13, CJType.class),

    C_JR(0b1111000000000011, RVC_OPCODE_C2 | RVC_JR << 12, CRType.class),
    C_JALR(0b1111000000000011, RVC_OPCODE_C2 | RVC_JALR << 12, CRType.class),

    // ---------- Privileged ---------- //

    WFI(MASK_OPCODE | MASK_RS1 | MASK_RS2 | 0b111111111111 << 20, OPCODE_SYSTEM | 0b000100000101 << 20, IType.class),

    ;

    private final int mask;
    private final int bits;
    private final Class<? extends Instruction> type;

    Definition(
            final int mask,
            final int bits,
            final @NotNull Class<? extends Instruction> type
    ) {
        this.mask = mask;
        this.bits = bits;
        this.type = type;
    }

    public boolean filter(final int data) {
        return bits == (data & mask);
    }

    public @NotNull Instruction instance(final int data) {
        try {
            return type.getConstructor(int.class, Definition.class)
                       .newInstance(data, this);
        } catch (final InstantiationException |
                       IllegalAccessException |
                       InvocationTargetException |
                       NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
