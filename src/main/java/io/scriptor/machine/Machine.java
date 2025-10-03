package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.impl.device.CLINT;
import io.scriptor.util.ExtendedInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntConsumer;

import static io.scriptor.util.ByteUtil.signExtend;

public interface Machine extends Device {

    @NotNull SymbolTable symbols();

    @NotNull CLINT clint();

    @NotNull Hart hart(int id);

    void dump(@NotNull PrintStream out, long address, long size);

    /**
     * proceed execution for one step.
     */
    void spinOnce();

    /**
     * proceed execution.
     */
    void spin();

    /**
     * pause execution.
     */
    void pause();

    void onBreakpoint(@NotNull IntConsumer handler);

    boolean breakpoint(int id);

    void onTrap(@NotNull IntConsumer handler);

    void trap(int id);

    /**
     * acquire a read/write lock for the specified address
     *
     * @param address the address
     * @return the lock object
     */
    @NotNull Object acquireLock(long address);

    /**
     * set the entry address.
     *
     * @param address entry address
     */
    void entry(long address);

    void offset(long offset);

    long offset();

    void order(@NotNull ByteOrder order);

    /**
     * load a segment from a stream into memory.
     *
     * @param stream  input stream
     * @param address destination address
     * @param size    destination size
     * @throws IOException if any
     */
    void segment(@NotNull ExtendedInputStream stream, long address, long size, long allocate)
            throws IOException;

    /**
     * load a sign-extended 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long lb(long address) {
        return signExtend(read(address, 1, false), 8);
    }

    /**
     * load an unsigned 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long lbu(long address) {
        return read(address, 1, false);
    }

    /**
     * load a sign-extended 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long lh(long address) {
        return signExtend(read(address, 2, false), 16);
    }

    /**
     * load an unsigned 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long lhu(long address) {
        return read(address, 2, false);
    }

    /**
     * load a sign-extended 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long lw(long address) {
        return signExtend(read(address, 4, false), 32);
    }

    /**
     * load an unsigned 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long lwu(long address) {
        return read(address, 4, false);
    }

    /**
     * load an 8-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long ld(long address) {
        return read(address, 8, false);
    }

    default @NotNull String lstring(long address) {
        final var buffer = new ByteArrayOutputStream();
        for (byte b; (b = (byte) lb(address++)) != 0; ) {
            buffer.write(b);
        }
        return buffer.toString();
    }

    /**
     * store a 1-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sb(long address, byte value) {
        write(address, 1, value, false);
    }

    /**
     * store a 2-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sh(long address, short value) {
        write(address, 2, value, false);
    }

    /**
     * store a 4-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sw(long address, int value) {
        write(address, 4, value, false);
    }

    /**
     * store an 8-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sd(long address, long value) {
        write(address, 8, value, false);
    }

    /**
     * fetch the 4-byte value at pc.
     *
     * @param pc     program counter
     * @param unsafe return 0 instead of error
     * @return instruction at pc, or 0 if error and unsafe
     */
    int fetch(long pc, boolean unsafe);

    /**
     * read a N-byte value.
     *
     * @param address source address
     * @param size    value size
     * @param unsafe  ignore errors
     * @return N-byte value at source address
     */
    long read(long address, int size, boolean unsafe);

    /**
     * write a N-byte value.
     *
     * @param address destination address
     * @param size    value size
     * @param value   N-byte source value
     * @param unsafe  ignore errors
     */
    void write(long address, int size, long value, boolean unsafe);

    boolean direct(long address, byte @NotNull [] buffer, boolean write);

    void generateDeviceTreeBlob(final @NotNull ByteBuffer buffer);
}
