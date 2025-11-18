package io.scriptor.machine;

import io.scriptor.impl.MMU.Access;
import io.scriptor.impl.TrapException;
import io.scriptor.isa.Instruction;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;

import static io.scriptor.impl.MMU.Access.*;
import static io.scriptor.util.ByteUtil.signExtend;

public interface Hart extends Device {

    @Override
    default void reset() {
        reset(0L);
    }

    void reset(final long entry);

    long execute(final int instruction, final Instruction definition);

    boolean sleeping();

    void wake();

    int id();

    long pc();

    void pc(final long pc);

    int privilege();

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

    long translate(long vaddr, @NotNull Access access, boolean unsafe);

    default long lb(final long vaddr) {
        return signExtend(read(vaddr, 1, false), 8);
    }

    default long lbu(final long vaddr) {
        return read(vaddr, 1, false);
    }

    default long lh(final long vaddr) {
        return signExtend(read(vaddr, 2, false), 16);
    }

    default long lhu(final long vaddr) {
        return read(vaddr, 2, false);
    }

    default long lw(final long vaddr) {
        return signExtend(read(vaddr, 4, false), 32);
    }

    default long lwu(final long vaddr) {
        return read(vaddr, 4, false);
    }

    default long ld(final long vaddr) {
        return read(vaddr, 8, false);
    }

    default @NotNull String lstring(long vaddr) {
        final var buffer = new ByteArrayOutputStream();
        for (byte b; (b = (byte) lb(vaddr++)) != 0; ) {
            buffer.write(b);
        }
        return buffer.toString();
    }

    default void sb(final long vaddr, final byte value) {
        write(vaddr, 1, value, false);
    }

    default void sh(final long vaddr, final short value) {
        write(vaddr, 2, value, false);
    }

    default void sw(final long vaddr, final int value) {
        write(vaddr, 4, value, false);
    }

    default void sd(final long vaddr, final long value) {
        write(vaddr, 8, value, false);
    }

    default int fetch(final long vaddr, final boolean unsafe) {
        final var paddr = translate(vaddr, FETCH, unsafe);

        if (unsafe && paddr == ~0L) {
            Log.warn("fetch invalid virtual address: address=%x", vaddr);
            return 0;
        }

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        final var device = machine().device(IODevice.class, paddr);

        if (device != null && device.begin() <= paddr && paddr + 4 <= device.end()) {
            return (int) (device.read((int) (paddr - device.begin()), 4) & 0xFFFFFFFFL);
        }

        throw new TrapException(id(), 0x01L, paddr, "fetch invalid address: address=%x", paddr);
    }

    default long read(final long vaddr, final int size, final boolean unsafe) {
        final var paddr = translate(vaddr, READ, unsafe);

        if (unsafe && paddr == ~0L) {
            Log.warn("read invalid virtual address: address=%x, size=%d", vaddr, size);
            return 0L;
        }

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        return machine().pRead(paddr, size, unsafe);
    }

    default void write(final long vaddr, final int size, final long value, final boolean unsafe) {
        final var paddr = translate(vaddr, WRITE, unsafe);

        if (unsafe && paddr == ~0L) {
            Log.warn("write invalid virtual address: address=%x, size=%d, value=%x", vaddr, size, value);
            return;
        }

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        machine().pWrite(paddr, size, value, unsafe);
    }

    default void direct(final byte @NotNull [] data, final long vaddr, final boolean write) {
        final var paddr = translate(vaddr, write ? WRITE : READ, true);

        if (paddr == ~0L) {
            Log.warn("direct read/write invalid virtual address: address=%x, length=%d", vaddr, data.length);
            return;
        }

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr);
        }

        machine().pDirect(data, paddr, write);
    }
}
