package io.scriptor;

import io.scriptor.type.*;
import org.jetbrains.annotations.NotNull;

import static io.scriptor.Constants.*;

public enum InstructionDefinition {

    // ---------- RV32I ----------

    LUI(UType.class, CODE_LUI),
    AUIPC(UType.class, CODE_AUIPC),

    JAL(JType.class, CODE_JAL),

    JALR(IType.class,
         CODE_JALR,
         UNDEFINED,
         0b000,
         UNDEFINED),

    BEQ(BType.class,
        CODE_BRANCH,
        UNDEFINED,
        0b000,
        UNDEFINED),
    BNE(BType.class,
        CODE_BRANCH,
        UNDEFINED,
        0b001,
        UNDEFINED),
    BLT(BType.class,
        CODE_BRANCH,
        UNDEFINED,
        0b100,
        UNDEFINED),
    BGE(BType.class,
        CODE_BRANCH,
        UNDEFINED,
        0b101,
        UNDEFINED),
    BLTU(BType.class,
         CODE_BRANCH,
         UNDEFINED,
         0b110,
         UNDEFINED),
    BGEU(BType.class,
         CODE_BRANCH,
         UNDEFINED,
         0b111,
         UNDEFINED),

    LB(IType.class,
       CODE_LOAD,
       UNDEFINED,
       0b000,
       UNDEFINED),
    LH(IType.class,
       CODE_LOAD,
       UNDEFINED,
       0b001,
       UNDEFINED),
    LW(IType.class,
       CODE_LOAD,
       UNDEFINED,
       0b010,
       UNDEFINED),
    LBU(IType.class,
        CODE_LOAD,
        UNDEFINED,
        0b100,
        UNDEFINED),
    LHU(IType.class,
        CODE_LOAD,
        UNDEFINED,
        0b101,
        UNDEFINED),

    SB(SType.class,
       CODE_STORE,
       UNDEFINED,
       0b000,
       UNDEFINED),
    SH(SType.class,
       CODE_STORE,
       UNDEFINED,
       0b001,
       UNDEFINED),
    SW(SType.class,
       CODE_STORE,
       UNDEFINED,
       0b010,
       UNDEFINED),

    ADDI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b000,
         UNDEFINED),
    SLTI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b010,
         UNDEFINED),
    SLTIU(IType.class,
          CODE_OP_IMM,
          UNDEFINED,
          0b011,
          UNDEFINED),
    XORI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b100,
         UNDEFINED),
    ORI(IType.class,
        CODE_OP_IMM,
        UNDEFINED,
        0b110,
        UNDEFINED),
    ANDI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b111,
         UNDEFINED),

    SLLI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b001,
         UNDEFINED), // imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
    SRLI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b101,
         UNDEFINED), // imm[11:6] = 0b0000000, imm[5:0] = shamt[5:0]
    SRAI(IType.class,
         CODE_OP_IMM,
         UNDEFINED,
         0b101,
         UNDEFINED), // imm[11:6] = 0b0100000, imm[5:0] = shamt[5:0]

    ADD(RType.class,
        CODE_OP,
        UNDEFINED,
        0b000,
        0b0000000),
    SUB(RType.class,
        CODE_OP,
        UNDEFINED,
        0b000,
        0b0100000),
    SLL(RType.class,
        CODE_OP,
        UNDEFINED,
        0b001,
        0b0000000),
    SLT(RType.class,
        CODE_OP,
        UNDEFINED,
        0b010,
        0b0000000),
    SLTU(RType.class,
         CODE_OP,
         UNDEFINED,
         0b011,
         0b0000000),
    XOR(RType.class,
        CODE_OP,
        UNDEFINED,
        0b100,
        0b0000000),
    SRL(RType.class,
        CODE_OP,
        UNDEFINED,
        0b101,
        0b0000000),
    SRA(RType.class,
        CODE_OP,
        UNDEFINED,
        0b101,
        0b0100000),
    OR(RType.class,
       CODE_OP,
       UNDEFINED,
       0b110,
       0b0000000),
    AND(RType.class,
        CODE_OP,
        UNDEFINED,
        0b111,
        0b0000000),

    // TODO: FENCE, FENCE.TSO, PAUSE, ECALL, EBREAK

    // ---------- RV64I ----------

    LWU(IType.class,
        CODE_LOAD,
        UNDEFINED,
        0b110,
        UNDEFINED),
    LD(IType.class,
       CODE_LOAD,
       UNDEFINED,
       0b011,
       UNDEFINED),

    SD(SType.class,
       CODE_STORE,
       UNDEFINED,
       0b011,
       UNDEFINED),

    ADDIW(IType.class,
          CODE_OP_IMM_32,
          UNDEFINED,
          0b000,
          UNDEFINED),

    SLLIW(IType.class,
          CODE_OP_IMM_32,
          UNDEFINED,
          0b001,
          UNDEFINED), // imm[11:6] = 0b0000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
    SRLIW(IType.class,
          CODE_OP_IMM_32,
          UNDEFINED,
          0b101,
          UNDEFINED), // imm[11:6] = 0b0000000, imm[5] = 0b0, imm[4:0] = shamt[4:0]
    SRAIW(IType.class,
          CODE_OP_IMM_32,
          UNDEFINED,
          0b101,
          UNDEFINED), // imm[11:6] = 0b0100000, imm[5] = 0b0, imm[4:0] = shamt[4:0]

    ADDW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b000,
         0b0000000),
    SUBW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b000,
         0b0100000),
    SLLW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b001,
         0b0000000),
    SRLW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b101,
         0b0000000),
    SRAW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b101,
         0b0100000),

    // ---------- RV32/RV64 Zifencei ----------

    // TODO: FENCE.I

    // ---------- RV32/RV64 Zicsr ----------

    CSRRW(IType.class,
          CODE_SYSTEM,
          UNDEFINED,
          0b001,
          UNDEFINED), // imm = csr
    CSRRS(IType.class,
          CODE_SYSTEM,
          UNDEFINED,
          0b010,
          UNDEFINED), // imm = csr
    CSRRC(IType.class,
          CODE_SYSTEM,
          UNDEFINED,
          0b011,
          UNDEFINED), // imm = csr
    CSRRWI(IType.class,
           CODE_SYSTEM,
           UNDEFINED,
           0b101,
           UNDEFINED), // imm = csr, rs1[4:0] = uimm[4:0]
    CSRRSI(IType.class,
           CODE_SYSTEM,
           UNDEFINED,
           0b110,
           UNDEFINED), // imm = csr, rs1[4:0] = uimm[4:0]
    CSRRCI(IType.class,
           CODE_SYSTEM,
           UNDEFINED,
           0b111,
           UNDEFINED), // imm = csr, rs1[4:0] = uimm[4:0]

    // ---------- RV32M ----------

    MUL(RType.class,
        CODE_OP,
        UNDEFINED,
        0b000,
        0b0000001),
    MULH(RType.class,
         CODE_OP,
         UNDEFINED,
         0b001,
         0b0000001),
    MULHSU(RType.class,
           CODE_OP,
           UNDEFINED,
           0b010,
           0b0000001),
    MULHU(RType.class,
          CODE_OP,
          UNDEFINED,
          0b011,
          0b0000001),
    DIV(RType.class,
        CODE_OP,
        UNDEFINED,
        0b100,
        0b0000001),
    DIVU(RType.class,
         CODE_OP,
         UNDEFINED,
         0b101,
         0b0000001),
    REM(RType.class,
        CODE_OP,
        UNDEFINED,
        0b110,
        0b0000001),
    REMU(RType.class,
         CODE_OP,
         UNDEFINED,
         0b111,
         0b0000001),

    // ---------- RV64M ----------

    MULW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b000,
         0b0000001),
    DIVW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b100,
         0b0000001),
    DIVUW(RType.class,
          CODE_OP_32,
          UNDEFINED,
          0b101,
          0b0000001),
    REMW(RType.class,
         CODE_OP_32,
         UNDEFINED,
         0b110,
         0b0000001),
    REMUW(RType.class,
          CODE_OP_32,
          UNDEFINED,
          0b111,
          0b0000001),

    // ---------- RV32A ----------

    LR_W(RType.class,
         CODE_AMO,
         UNDEFINED,
         0b010,
         0b0001000), // func7[0] = rl, func7[1] = aq, rs2 = 0b00000
    SC_W(RType.class,
         CODE_AMO,
         UNDEFINED,
         0b010,
         0b0001100), // func7[0] = rl, func7[1] = aq
    AMOSWAP_W(RType.class,
              CODE_AMO,
              UNDEFINED,
              0b010,
              0b0000100), // func7[0] = rl, func7[1] = aq
    AMOADD_W(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b010,
             0b0000000), // func7[0] = rl, func7[1] = aq
    AMOXOR_W(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b010,
             0b0010000), // func7[0] = rl, func7[1] = aq
    AMOAND_W(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b010,
             0b0110000), // func7[0] = rl, func7[1] = aq
    AMOOR_W(RType.class,
            CODE_AMO,
            UNDEFINED,
            0b010,
            0b0100000), // func7[0] = rl, func7[1] = aq
    AMOMIN_W(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b010,
             0b1000000), // func7[0] = rl, func7[1] = aq
    AMOMAX_W(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b010,
             0b1010000), // func7[0] = rl, func7[1] = aq
    AMOMINU_W(RType.class,
              CODE_AMO,
              UNDEFINED,
              0b010,
              0b1100000), // func7[0] = rl, func7[1] = aq
    AMOMAXU_W(RType.class,
              CODE_AMO,
              UNDEFINED,
              0b010,
              0b1110000), // func7[0] = rl, func7[1] = aq

    // ---------- RV64A ----------

    LR_D(RType.class,
         CODE_AMO,
         UNDEFINED,
         0b011,
         0b0001000), // func7[0] = rl, func7[1] = aq, rs2 = 0b00000
    SC_D(RType.class,
         CODE_AMO,
         UNDEFINED,
         0b011,
         0b0001100), // func7[0] = rl, func7[1] = aq
    AMOSWAP_D(RType.class,
              CODE_AMO,
              UNDEFINED,
              0b011,
              0b0000100), // func7[0] = rl, func7[1] = aq
    AMOADD_D(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b011,
             0b0000000), // func7[0] = rl, func7[1] = aq
    AMOXOR_D(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b011,
             0b0010000), // func7[0] = rl, func7[1] = aq
    AMOAND_D(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b011,
             0b0110000), // func7[0] = rl, func7[1] = aq
    AMOOR_D(RType.class,
            CODE_AMO,
            UNDEFINED,
            0b011,
            0b0100000), // func7[0] = rl, func7[1] = aq
    AMOMIN_D(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b011,
             0b1000000), // func7[0] = rl, func7[1] = aq
    AMOMAX_D(RType.class,
             CODE_AMO,
             UNDEFINED,
             0b011,
             0b1010000), // func7[0] = rl, func7[1] = aq
    AMOMINU_D(RType.class,
              CODE_AMO,
              UNDEFINED,
              0b011,
              0b1100000), // func7[0] = rl, func7[1] = aq
    AMOMAXU_D(RType.class,
              CODE_AMO,
              UNDEFINED,
              0b011,
              0b1110000), // func7[0] = rl, func7[1] = aq

    // ---------- RV32F ----------

    FLW(IType.class,
        CODE_LOAD_FP,
        UNDEFINED,
        0b010,
        UNDEFINED),
    FSW(SType.class,
        CODE_STORE_FP,
        UNDEFINED,
        0b010,
        UNDEFINED),
    FMADD_S(R4Type.class,
            CODE_MADD,
            0b00,
            UNDEFINED,
            UNDEFINED), // func3 = rm
    FMSUB_S(R4Type.class,
            CODE_MSUB,
            0b00,
            UNDEFINED,
            UNDEFINED), // func3 = rm
    FNMSUB_S(R4Type.class,
             CODE_NMSUB,
             0b00,
             UNDEFINED,
             UNDEFINED), // func3 = rm
    FNMADD_S(R4Type.class,
             CODE_NMADD,
             0b00,
             UNDEFINED,
             UNDEFINED), // func3 = rm
    FADD_S(RType.class,
           CODE_OP_FP,
           UNDEFINED,
           UNDEFINED,
           0b0000000), // func3 = rm
    FSUB_S(RType.class,
           CODE_OP_FP,
           UNDEFINED,
           UNDEFINED,
           0b0000100), // func3 = rm
    FMUL_S(RType.class,
           CODE_OP_FP,
           UNDEFINED,
           UNDEFINED,
           0b0001000), // func3 = rm
    FDIV_S(RType.class,
           CODE_OP_FP,
           UNDEFINED,
           UNDEFINED,
           0b0001100), // func3 = rm
    FSQRT_S(RType.class,
            CODE_OP_FP,
            UNDEFINED,
            UNDEFINED,
            0b0101100), // func3 = rm, rs2 = 0b00000
    FSGNJ_S(RType.class,
            CODE_OP_FP,
            UNDEFINED,
            0b000,
            0b0010000),
    FSGNJN_S(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             0b001,
             0b0010000),
    FSGNJX_S(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             0b010,
             0b0010000),
    FMIN_S(RType.class,
           CODE_OP_FP,
           UNDEFINED,
           0b000,
           0b0010100),
    FMAX_S(RType.class,
           CODE_OP_FP,
           UNDEFINED,
           0b001,
           0b0010100),
    FCVT_W_S(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             UNDEFINED,
             0b1100000), // func3 = rm, rs2 = 0b00000
    FCVT_WU_S(RType.class,
              CODE_OP_FP,
              UNDEFINED,
              UNDEFINED,
              0b1100000), // func3 = rm, rs2 = 0b00001
    FMV_X_W(RType.class,
            CODE_OP_FP,
            UNDEFINED,
            0b000,
            0b1110000), // rs2 = 0b00000
    FEQ_S(RType.class,
          CODE_OP_FP,
          UNDEFINED,
          0b010,
          0b1010000),
    FLT_S(RType.class,
          CODE_OP_FP,
          UNDEFINED,
          0b001,
          0b1010000),
    FLE_S(RType.class,
          CODE_OP_FP,
          UNDEFINED,
          0b000,
          0b1010000),
    FCLASS_S(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             0b001,
             0b1110000), // rs2 = 0b00000
    FCVT_S_W(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             UNDEFINED,
             0b1101000), // func3 = rm, rs2 = 0b00000
    FCVT_S_WU(RType.class,
              CODE_OP_FP,
              UNDEFINED,
              UNDEFINED,
              0b1101000), // func3 = rm, rs2 = 0b00001
    FMV_W_X(RType.class,
            CODE_OP_FP,
            UNDEFINED,
            0b000,
            0b1111000), // rs2 = 0b00000

    // ---------- RV64F ----------

    FCVT_L_S(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             UNDEFINED,
             0b1100000), // func3 = rm, rs2 = 0b00010
    FCVT_LU_S(RType.class,
              CODE_OP_FP,
              UNDEFINED,
              UNDEFINED,
              0b1100000), // func3 = rm, rs2 = 0b00011
    FCVT_S_L(RType.class,
             CODE_OP_FP,
             UNDEFINED,
             UNDEFINED,
             0b1101000), // func3 = rm, rs2 = 0b00010
    FCVT_S_LU(RType.class,
              CODE_OP_FP,
              UNDEFINED,
              UNDEFINED,
              0b1101000), // func3 = rm, rs2 = 0b00011

    // ---------- RV32D ----------

    // TODO

    // ---------- RV64D ----------

    // TODO

    // ---------- RV32Q ----------

    // TODO

    // ---------- RV64Q ----------

    // TODO

    // ---------- RV32Zfh ----------

    // TODO

    // ---------- RV64Zfh ----------

    // TODO

    // ---------- Zawrs ----------

    // TODO

    ;

    private final Class<? extends Instruction> type;
    private final int opcode;
    private final int func2;
    private final int func3;
    private final int func7;

    InstructionDefinition(final @NotNull Class<? extends Instruction> type, final int opcode) {
        this.type = type;
        this.opcode = opcode;
        this.func2 = UNDEFINED;
        this.func3 = UNDEFINED;
        this.func7 = UNDEFINED;
    }

    InstructionDefinition(
            final @NotNull Class<? extends Instruction> type,
            final int opcode,
            final int func2,
            final int func3,
            final int func7
    ) {
        this.type = type;
        this.opcode = opcode;
        this.func2 = func2;
        this.func3 = func3;
        this.func7 = func7;
    }

    public @NotNull Class<? extends Instruction> getType() {
        return type;
    }

    public int getOpcode() {
        return opcode;
    }

    public int getFunc2() {
        return func2;
    }

    public int getFunc3() {
        return func3;
    }

    public int getFunc7() {
        return func7;
    }
}
