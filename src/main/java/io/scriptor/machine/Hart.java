package io.scriptor.machine;

import io.scriptor.isa.Instruction;
import org.jetbrains.annotations.NotNull;

public interface Hart extends Device {

    @Override
    default void reset() {
        reset(0L);
    }

    void reset(final long entry);

    long execute(final int instruction, final Instruction definition);

    boolean wfi();

    void wake();

    long pc();

    void pc(final long pc);

    /**
     * get the harts gpr file
     *
     * @return the gpr file
     */
    @NotNull GeneralPurposeRegisterFile gprFile();

    /**
     * get the harts fpr file
     *
     * @return the fpr file
     */
    @NotNull FloatingPointRegisterFile fprFile();

    /**
     * get the harts csr file
     *
     * @return the csr file
     */
    @NotNull ControlStatusRegisterFile csrFile();
}
