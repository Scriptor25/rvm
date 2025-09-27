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

    long pc();

    void pc(final long pc);

    boolean active();

    void active(final boolean active);

    /**
     * get the harts gpr file
     *
     * @return the gpr file
     */
    @NotNull GPRFile gprFile();

    /**
     * get the harts fpr file
     *
     * @return the fpr file
     */
    @NotNull FPRFile fprFile();

    /**
     * get the harts csr file
     *
     * @return the csr file
     */
    @NotNull CSRFile csrFile();
}
