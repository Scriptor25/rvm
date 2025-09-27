package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.impl.CLINT;
import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.util.stream.Stream;

import static io.scriptor.util.ByteUtil.signExtend;

public interface Machine {

    @NotNull SymbolTable getSymbols();

    @NotNull CLINT getCLINT();

    @NotNull Hart getHart(final int id);

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
    void dump(final @NotNull PrintStream out);

    /**
     * proceed execution.
     */
    void step();

    /**
     * acquire a read/write lock for the specified address
     *
     * @param address the address
     * @return the lock object
     */
    @NotNull Object acquireLock(final long address);

    /**
     * set the entry address.
     *
     * @param address entry address
     */
    void setEntry(final long address);

    /**
     * load a stream into memory.
     *
     * @param stream input stream
     * @throws IOException if any
     */
    void loadDirect(final @NotNull IOStream stream, final long address, final long size, final long allocate)
            throws IOException;

    /**
     * load a segment from a stream into memory.
     *
     * @param stream  input stream
     * @param address destination address
     * @param size    destination size
     * @throws IOException if any
     */
    void loadSegment(final @NotNull IOStream stream, final long address, final long size, final long allocate)
            throws IOException;

    /**
     * load a sign-extended 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lb(final long address) {
        return (int) signExtend(read(address, 1, false), 8);
    }

    /**
     * load an unsigned 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lbu(final long address) {
        return (int) read(address, 1, false);
    }

    /**
     * load a sign-extended 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lh(final long address) {
        return (int) signExtend(read(address, 2, false), 16);
    }

    /**
     * load an unsigned 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lhu(final long address) {
        return (int) read(address, 2, false);
    }

    /**
     * load a sign-extended 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lw(final long address) {
        return (int) signExtend(read(address, 4, false), 32);
    }

    /**
     * load an unsigned 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lwu(final long address) {
        return (int) read(address, 4, false);
    }

    /**
     * load an 8-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long ld(final long address) {
        return read(address, 8, false);
    }

    /**
     * store a 1-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sb(final long address, final byte value) {
        write(address, 1, value, false);
    }

    /**
     * store a 2-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sh(final long address, final short value) {
        write(address, 2, value, false);
    }

    /**
     * store a 4-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sw(final long address, final int value) {
        write(address, 4, value, false);
    }

    /**
     * store an 8-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sd(final long address, final long value) {
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
    long read(final long address, final int size, final boolean unsafe);

    /**
     * write a N-byte value.
     *
     * @param address destination address
     * @param size    value size
     * @param value   N-byte source value
     * @param unsafe  ignore errors
     */
    void write(final long address, final int size, final long value, boolean unsafe);

    /**
     * fetch the 4-byte value at pc.
     *
     * @param pc     program counter
     * @param unsafe return 0 instead of error
     * @return instruction at pc, or 0 if error and unsafe
     */
    int fetch(final long pc, final boolean unsafe);
}
