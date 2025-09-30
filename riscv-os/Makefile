CROSS_COMPILE = riscv64-linux-gnu-

AS = $(CROSS_COMPILE)as
CC = $(CROSS_COMPILE)gcc
LD = $(CROSS_COMPILE)ld

SRC_DIR = src
BIN_DIR = bin

SRC = $(SRC_DIR)/entry.s $(SRC_DIR)/kernel.c
BIN = $(SRC:$(SRC_DIR)/%=$(BIN_DIR)/%.o)

LDS = $(SRC_DIR)/linker.ld

ELF = $(BIN_DIR)/kernel.elf

AS_FLAGS = -march=rv64gc -mabi=lp64d -g
CC_FLAGS = -Wall -Wextra -mcmodel=medany -ffreestanding -march=rv64gc -mabi=lp64d -g
LD_FLAGS = -nostdlib -g

.PHONY: all clean build

all: build

clean:
	-rm $(BIN) $(ELF)

build: $(BIN_DIR) $(ELF)

$(BIN_DIR)/%.s.o: $(SRC_DIR)/%.s
	$(AS) $(AS_FLAGS) -c $< -o $@

$(BIN_DIR)/%.c.o: $(SRC_DIR)/%.c
	$(CC) $(CC_FLAGS) -c $< -o $@

$(ELF): $(LDS) $(BIN)
	$(LD) $(LD_FLAGS) -T $^ -o $(ELF)

$(BIN_DIR):
	mkdir -p $(BIN_DIR)
