package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public interface Machine extends Device {

    @NotNull ByteOrder order();

    @NotNull SymbolTable symbols();

    int harts();

    @NotNull Hart hart(int id);

    <T extends Device> @NotNull T device(@NotNull Class<T> type);

    <T extends Device> @NotNull T device(@NotNull Class<T> type, int index);

    <T extends Device> void device(@NotNull Class<T> type, @NotNull Predicate<T> predicate);

    <T extends IODevice> @Nullable T device(@NotNull Class<T> type, long address);

    void dump(@NotNull PrintStream out, long paddr, long length);

    void spinOnce();

    void spin();

    void pause();

    void onBreakpoint(@NotNull IntConsumer handler);

    boolean breakpoint(int id);

    @NotNull Object acquireLock(long address);

    long pRead(long paddr, int size, boolean unsafe);

    void pWrite(long paddr, int size, long value, boolean unsafe);

    void pDirect(byte @NotNull [] data, long paddr, boolean write);

    void generateDeviceTree(@NotNull ByteBuffer buffer);
}
