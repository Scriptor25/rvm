package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.impl.CLINT;
import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import static io.scriptor.util.ByteUtil.signExtend;

public interface Machine {

    @NotNull SymbolTable getSymbols();

    @NotNull CLINT getCLINT();

    @NotNull Hart getHart(int id);

    @NotNull Stream<Hart> getHarts();

    /**
     * reset the machine state.
     */
    void reset();

    /**
     * dump the current machine state.
     *
     * @param out output print stream
     */
    void dump(@NotNull PrintStream out);

    void tick();

    /**
     * proceed execution for one step.
     */
    void step(int id);

    /**
     * proceed execution.
     */
    void spin(int id);

    /**
     * pause execution.
     */
    void pause(int id);

    void insertBreakpoint(long address, @NotNull IntConsumer callback);

    void removeBreakpoint(long address);

    boolean hitBreakpoint(int id, long address);

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
    void setEntry(long address);

    /**
     * load a stream into memory.
     *
     * @param stream input stream
     * @throws IOException if any
     */
    void loadDirect(@NotNull IOStream stream, long address, long size, long allocate)
            throws IOException;

    /**
     * load a segment from a stream into memory.
     *
     * @param stream  input stream
     * @param address destination address
     * @param size    destination size
     * @throws IOException if any
     */
    void loadSegment(@NotNull IOStream stream, long address, long size, long allocate)
            throws IOException;

    /**
     * load a sign-extended 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lb(long address) {
        return (int) signExtend(read(address, 1, false), 8);
    }

    /**
     * load an unsigned 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lbu(long address) {
        return (int) read(address, 1, false);
    }

    /**
     * load a sign-extended 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lh(long address) {
        return (int) signExtend(read(address, 2, false), 16);
    }

    /**
     * load an unsigned 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lhu(long address) {
        return (int) read(address, 2, false);
    }

    /**
     * load a sign-extended 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lw(long address) {
        return (int) signExtend(read(address, 4, false), 32);
    }

    /**
     * load an unsigned 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lwu(long address) {
        return (int) read(address, 4, false);
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
     * read a N-byte value.
     *
     * @param address source address
     * @param size    value size
     * @param unsafe  ignore errors
     * @return N-byte value at source address
     */
    long read(long address, int size, boolean unsafe);

    void read(
            long address,
            byte @NotNull [] buffer,
            int offset,
            int length
    );

    /**
     * write a N-byte value.
     *
     * @param address destination address
     * @param size    value size
     * @param value   N-byte source value
     * @param unsafe  ignore errors
     */
    void write(long address, int size, long value, boolean unsafe);

    void write(
            long address,
            byte @NotNull [] buffer,
            int offset,
            int length
    );

    /**
     * fetch the 4-byte value at pc.
     *
     * @param pc     program counter
     * @param unsafe return 0 instead of error
     * @return instruction at pc, or 0 if error and unsafe
     */
    int fetch(long pc, boolean unsafe);
}
