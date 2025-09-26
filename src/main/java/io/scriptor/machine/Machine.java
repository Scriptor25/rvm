package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.io.IOStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;

import static io.scriptor.util.ByteUtil.signExtend;

public interface Machine {

    @Contract(pure = true)
    long getDRAM();

    @Contract(pure = true)
    @NotNull SymbolTable getSymbols();

    /**
     * reset the machine state.
     */
    @Contract(mutates = "this")
    void reset();

    /**
     * dump the current machine state.
     *
     * @param out output print stream
     */
    @Contract(mutates = "io,this,param")
    void dump(final @NotNull PrintStream out);

    /**
     * proceed execution.
     *
     * @return if machine state is still ok
     */
    @Contract(mutates = "this")
    boolean step();

    /**
     * set the entry address.
     *
     * @param address entry address
     */
    @Contract(mutates = "this")
    void setEntry(final long address);

    /**
     * load a stream into memory.
     *
     * @param stream input stream
     * @throws IOException if any
     */
    @Contract(mutates = "io,this,param1")
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
    @Contract(mutates = "io,this,param1")
    void loadSegment(final @NotNull IOStream stream, final long address, final long size, final long allocate)
            throws IOException;

    /**
     * fetch the 4-byte value at pc.
     *
     * @param unsafe return 0 instead of error
     * @return instruction at pc, or 0 if error and unsafe
     */
    @Contract(mutates = "this")
    int fetch(final boolean unsafe);

    /**
     * read a 4-byte value from a general purpose register.
     *
     * @param gpr source register
     */
    @Contract(mutates = "this")
    int gprw(final int gpr);

    /**
     * write a 4-byte value to a general purpose register.
     *
     * @param gpr   destination register
     * @param value source value
     */
    @Contract(mutates = "this")
    void gprw(final int gpr, final int value);

    /**
     * read an 8-byte value from a general purpose register.
     *
     * @param gpr source register
     */
    @Contract(mutates = "this")
    long gprd(final int gpr);

    /**
     * write an 8-byte value to a general purpose register.
     *
     * @param gpr   destination register
     * @param value source value
     */
    @Contract(mutates = "this")
    void gprd(final int gpr, final long value);

    /**
     * load a sign-extended 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default int lb(final long address) {
        return (int) signExtend(read(address, 1), 8);
    }

    /**
     * load an unsigned 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default int lbu(final long address) {
        return (int) read(address, 1);
    }

    /**
     * load a sign-extended 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default int lh(final long address) {
        return (int) signExtend(read(address, 2), 16);
    }

    /**
     * load an unsigned 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default int lhu(final long address) {
        return (int) read(address, 2);
    }

    /**
     * load a sign-extended 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default int lw(final long address) {
        return (int) signExtend(read(address, 4), 32);
    }

    /**
     * load an unsigned 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default int lwu(final long address) {
        return (int) read(address, 4);
    }

    /**
     * load an 8-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    @Contract(mutates = "io,this")
    default long ld(final long address) {
        return read(address, 8);
    }

    /**
     * store a 1-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    @Contract(mutates = "io,this")
    default void sb(final long address, final byte value) {
        write(address, 1, value);
    }

    /**
     * store a 2-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    @Contract(mutates = "io,this")
    default void sh(final long address, final short value) {
        write(address, 2, value);
    }

    /**
     * store a 4-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    @Contract(mutates = "io,this")
    default void sw(final long address, final int value) {
        write(address, 4, value);
    }

    /**
     * store an 8-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    @Contract(mutates = "io,this")
    default void sd(final long address, final long value) {
        write(address, 8, value);
    }

    /**
     * read a N-byte value.
     *
     * @param address source address
     * @param size    value size
     * @return N-byte value at source address
     */
    @Contract(mutates = "io,this")
    long read(final long address, final int size);

    /**
     * write a N-byte value.
     *
     * @param address destination address
     * @param size    value size
     * @param value   N-byte source value
     */
    @Contract(mutates = "io,this")
    void write(final long address, final int size, final long value);
}
