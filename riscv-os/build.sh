#!/usr/bin/env sh

riscv64-linux-gnu-as -c entry.s -o entry.o -march=rv64g -mabi=lp64
riscv64-linux-gnu-gcc -Wall -Wextra -c -mcmodel=medany kernel.c -o kernel.o -ffreestanding -march=rv64g -mabi=lp64
riscv64-linux-gnu-ld -T linker.ld -nostdlib kernel.o entry.o -o kernel.elf --no-relax
