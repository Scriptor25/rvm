package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.impl.device.Memory;
import io.scriptor.util.ExtendedInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public interface Machine extends Device {

    @NotNull SymbolTable symbols();

    @NotNull Memory memory();

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

    @NotNull Object acquireLock(long address);

    void entry(long entry);

    long entry();

    void offset(long offset);

    long offset();

    void segment(@NotNull ExtendedInputStream stream, long address, int size, int allocate) throws IOException;

    long pRead(long paddr, int size, boolean unsafe);

    void pWrite(long paddr, int size, long value, boolean unsafe);

    void pDirect(byte @NotNull [] data, long paddr, boolean write);

    void generateDeviceTree(@NotNull ByteBuffer buffer);
}
