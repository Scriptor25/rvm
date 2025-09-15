package io.scriptor;

public interface Constants {

    int OPCODE_LOAD = 0b00_000_11;
    int OPCODE_LOAD_FP = 0b00_001_11;
    int OPCODE_CUSTOM_0 = 0b00_010_11;
    int OPCODE_MISC_MEM = 0b00_011_11;
    int OPCODE_OP_IMM = 0b00_100_11;
    int OPCODE_AUIPC = 0b00_101_11;
    int OPCODE_OP_IMM_32 = 0b00_110_11;

    int OPCODE_STORE = 0b01_000_11;
    int OPCODE_STORE_FP = 0b01_001_11;
    int OPCODE_CUSTOM_1 = 0b01_010_11;
    int OPCODE_AMO = 0b01_011_11;
    int OPCODE_OP = 0b01_100_11;
    int OPCODE_LUI = 0b01_101_11;
    int OPCODE_OP_32 = 0b01_110_11;

    int OPCODE_MADD = 0b10_000_11;
    int OPCODE_MSUB = 0b10_001_11;
    int OPCODE_NMSUB = 0b10_010_11;
    int OPCODE_NMADD = 0b10_011_11;
    int OPCODE_OP_FP = 0b10_100_11;
    int OPCODE_OP_V = 0b10_101_11;
    int OPCODE_CUSTOM_2 = 0b10_110_11;

    int OPCODE_BRANCH = 0b11_000_11;
    int OPCODE_JALR = 0b11_001_11;
    int OPCODE_JAL = 0b11_011_11;
    int OPCODE_SYSTEM = 0b11_100_11;
    int OPCODE_OP_VE = 0b11_101_11;
    int OPCODE_CUSTOM_3 = 0b11_110_11;

    int RVC_OPCODE_C0 = 0b00;
    int RVC_OPCODE_C1 = 0b01;
    int RVC_OPCODE_C2 = 0b10;

    int RVC_ADDI4SPN = 0b000;
    int RVC_ADDI = 0b000;
    int RVC_SLLI = 0b000;

    int RVC_FLD = 0b001;
    int RVC_JAL = 0b001;
    int RVC_ADDIW = 0b001;
    int RVC_FLDSP = 0b001;

    int RVC_LW = 0b010;
    int RVC_LI = 0b010;
    int RVC_LWSP = 0b010;

    int RVC_FLW = 0b011;
    int RVC_LD = 0b011;
    int RVC_LUI = 0b011;
    int RVC_ADDI16SP = 0b011;
    int RVC_FLWSP = 0b011;
    int RVC_LDSP = 0b011;

    int RVC_MISC_ALU = 0b100;
    int RVC_JALR = 0b100;
    int RVC_JR = 0b100;
    int RVC_MV = 0b100;
    int RVC_ADD = 0b100;

    int RVC_FSD = 0b101;
    int RVC_J = 0b101;
    int RVC_FSDSP = 0b101;

    int RVC_SW = 0b110;
    int RVC_BEQZ = 0b110;
    int RVC_SWSP = 0b110;

    int RVC_FSW = 0b111;
    int RVC_SD = 0b111;
    int RVC_BNEZ = 0b111;
    int RVC_FSWSP = 0b111;
    int RVC_SDSP = 0b111;

    int SHIFT_FUNCT2 = 25;
    int SHIFT_FUNCT3 = 12;
    int SHIFT_FUNCT7 = 25;
    int SHIFT_RD = 7;
    int SHIFT_RS1 = 15;
    int SHIFT_RS2 = 20;

    int MASK_OPCODE = 0b1111111;
    int MASK_FUNCT2 = 0b11 << SHIFT_FUNCT2;
    int MASK_FUNCT3 = 0b111 << SHIFT_FUNCT3;
    int MASK_FUNCT7 = 0b1111111 << SHIFT_FUNCT7;
    int MASK_RD = 0b11111 << SHIFT_RD;
    int MASK_RS1 = 0b11111 << SHIFT_RS1;
    int MASK_RS2 = 0b11111 << SHIFT_RS2;

    int MASK_R = MASK_FUNCT7 | MASK_FUNCT3 | MASK_OPCODE;
    int MASK_R4 = MASK_FUNCT2 | MASK_FUNCT3 | MASK_OPCODE;
    int MASK_I = MASK_FUNCT3 | MASK_OPCODE;
    int MASK_S = MASK_FUNCT3 | MASK_OPCODE;
    int MASK_B = MASK_FUNCT3 | MASK_OPCODE;
    int MASK_U = MASK_OPCODE;
    int MASK_J = MASK_OPCODE;
}
