#!/usr/bin/env sh

riscv64-linux-gnu-as -c entry.s -o entry.o -march=rv64gc -mabi=lp64d -g
riscv64-linux-gnu-gcc -Wall -Wextra -c -mcmodel=medany kernel.c -o kernel.o -ffreestanding -march=rv64gc -mabi=lp64d -fno-omit-frame-pointer -g
riscv64-linux-gnu-ld -T linker.ld -nostdlib kernel.o entry.o -o kernel.elf -g
