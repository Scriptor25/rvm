package io.scriptor;

public interface Constants {

    int UNDEFINED = -1;

    int CODE_LOAD = 0b00_000_11;
    int CODE_LOAD_FP = 0b00_001_11;
    int CODE_CUSTOM_0 = 0b00_010_11;
    int CODE_MISC_MEM = 0b00_011_11;
    int CODE_OP_IMM = 0b00_100_11;
    int CODE_AUIPC = 0b00_101_11;
    int CODE_OP_IMM_32 = 0b00_110_11;

    int CODE_STORE = 0b01_000_11;
    int CODE_STORE_FP = 0b01_001_11;
    int CODE_CUSTOM_1 = 0b01_010_11;
    int CODE_AMO = 0b01_011_11;
    int CODE_OP = 0b01_100_11;
    int CODE_LUI = 0b01_101_11;
    int CODE_OP_32 = 0b01_110_11;

    int CODE_MADD = 0b10_000_11;
    int CODE_MSUB = 0b10_001_11;
    int CODE_NMSUB = 0b10_010_11;
    int CODE_NMADD = 0b10_011_11;
    int CODE_OP_FP = 0b10_100_11;
    int CODE_OP_V = 0b10_101_11;
    int CODE_CUSTOM_2 = 0b10_110_11;

    int CODE_BRANCH = 0b11_000_11;
    int CODE_JALR = 0b11_001_11;
    int CODE_JAL = 0b11_011_11;
    int CODE_SYSTEM = 0b11_100_11;
    int CODE_OP_VE = 0b11_101_11;
    int CODE_CUSTOM_3 = 0b11_110_11;
}
