package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.impl.MMU;
import io.scriptor.util.ExtendedInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static io.scriptor.util.ByteUtil.signExtend;

public interface Machine extends Device {

    @NotNull SymbolTable symbols();

    @NotNull MMU mmu();

    @NotNull Hart hart(int id);

    <T extends Device> @NotNull T device(@NotNull Class<T> type);

    <T extends Device> @NotNull T device(@NotNull Class<T> type, final @NotNull Predicate<T> predicate);

    <T extends Device> @NotNull T device(@NotNull Class<T> type, int index);

    <T extends Device> void device(@NotNull Class<T> type, final @NotNull Consumer<T> consumer);

    void dump(@NotNull PrintStream out, long paddr, long length);

    void spinOnce();

    void spin();

    void pause();

    void onBreakpoint(@NotNull IntConsumer handler);

    boolean breakpoint(int id);

    void onTrap(@NotNull IntConsumer handler);

    void trap(int id);

    @NotNull Object acquireLock(long address);

    void entry(long entry);

    long entry();

    void offset(long offset);

    long offset();

    void segment(@NotNull ExtendedInputStream stream, long address, int size, int allocate) throws IOException;

    default long lb(final int hartid, final long vaddr) {
        return signExtend(read(hartid, vaddr, 1, false), 8);
    }

    default long lbu(final int hartid, final long vaddr) {
        return read(hartid, vaddr, 1, false);
    }

    default long lh(final int hartid, final long vaddr) {
        return signExtend(read(hartid, vaddr, 2, false), 16);
    }

    default long lhu(final int hartid, final long vaddr) {
        return read(hartid, vaddr, 2, false);
    }

    default long lw(final int hartid, final long vaddr) {
        return signExtend(read(hartid, vaddr, 4, false), 32);
    }

    default long lwu(final int hartid, final long vaddr) {
        return read(hartid, vaddr, 4, false);
    }

    default long ld(final int hartid, final long vaddr) {
        return read(hartid, vaddr, 8, false);
    }

    default @NotNull String lstring(final int hartid, long vaddr) {
        final var buffer = new ByteArrayOutputStream();
        for (byte b; (b = (byte) lb(hartid, vaddr++)) != 0; ) {
            buffer.write(b);
        }
        return buffer.toString();
    }

    default void sb(final int hartid, final long vaddr, final byte value) {
        write(hartid, vaddr, 1, value, false);
    }

    default void sh(final int hartid, final long vaddr, final short value) {
        write(hartid, vaddr, 2, value, false);
    }

    default void sw(final int hartid, final long vaddr, final int value) {
        write(hartid, vaddr, 4, value, false);
    }

    default void sd(final int hartid, final long vaddr, final long value) {
        write(hartid, vaddr, 8, value, false);
    }

    int fetch(int hartid, long pc, boolean unsafe);

    long read(int hartid, long vaddr, int size, boolean unsafe);

    void write(int hartid, long vaddr, int size, long value, boolean unsafe);

    long pRead(long paddr, int size, boolean unsafe);

    void pWrite(long paddr, int size, long value, boolean unsafe);

    void direct(int hartid, byte @NotNull [] data, long vaddr, boolean write);

    void pDirect(byte @NotNull [] data, long paddr, boolean write);

    void generateDeviceTreeBlob(@NotNull ByteBuffer buffer);
}
