package io.scriptor;

import io.scriptor.type.Instruction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class Main {

    // https://riscv.github.io/riscv-isa-manual/snapshot/privileged/#_risc_v_privileged_instruction_set_listings
    // https://riscv.github.io/riscv-isa-manual/snapshot/unprivileged/#rv32

    public static void main(final @NotNull String[] args) {
        final var file = new File("complex_64.bin");

        try (final var stream = new FileInputStream(file)) {
            final var bytes = new byte[4];
            while (stream.readNBytes(bytes, 0, 4) == 4) {
                final var data        = ((int) bytes[3] << 24) | ((int) bytes[2] << 16) | ((int) bytes[1] << 8) | (int) bytes[0];
                final var instruction = new Instruction(data);

                System.out.println(instruction);
            }
        } catch (final IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private Main() {
    }
}
