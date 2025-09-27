package io.scriptor.machine;

import io.scriptor.isa.Instruction;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

public interface Hart {

    void reset(final long entry);

    void dump(final @NotNull PrintStream out);

    void step();

    long execute(final int instruction, final Instruction definition);

    boolean wfi();

    void wake();

    /**
     * get the harts gpr file
     *
     * @return the gpr file
     */
    @NotNull GPRFile getGPRFile();

    /**
     * get the harts fpr file
     *
     * @return the fpr file
     */
    @NotNull FPRFile getFPRFile();

    /**
     * get the harts csr file
     *
     * @return the csr file
     */
    @NotNull CSRFile getCSRFile();
}
