package io.scriptor.machine;

import io.scriptor.elf.SymbolTable;
import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;

import static io.scriptor.util.ByteUtil.signExtend;

public interface Machine {

    long getDRAM();

    @NotNull SymbolTable getSymbols();

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
     *
     * @return if machine state is still ok
     */
    boolean step();

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
     * fetch the 4-byte value at pc.
     *
     * @param unsafe return 0 instead of error
     * @return instruction at pc, or 0 if error and unsafe
     */
    int fetch(final boolean unsafe);

    /**
     * read a 4-byte value from a general purpose register.
     *
     * @param gpr source register
     */
    int gprw(final int gpr);

    /**
     * write a 4-byte value to a general purpose register.
     *
     * @param gpr   destination register
     * @param value source value
     */
    void gprw(final int gpr, final int value);

    /**
     * read an 8-byte value from a general purpose register.
     *
     * @param gpr source register
     */
    long gprd(final int gpr);

    /**
     * write an 8-byte value to a general purpose register.
     *
     * @param gpr   destination register
     * @param value source value
     */
    void gprd(final int gpr, final long value);

    /**
     * read a 4-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv
     */
    int csrw(final int addr, final int priv);

    /**
     * write a 4-byte value to a control/status register.
     *
     * @param addr destination register
     * @param priv
     * @param val  source value
     */
    void csrw(final int addr, final int priv, final int val);

    /**
     * read an 8-byte value from a control/status register.
     *
     * @param addr source register
     * @param priv
     */
    long csrd(final int addr, int priv);

    /**
     * write an 8-byte value to a control/status register.
     *
     * @param csr  destination register
     * @param priv
     * @param val  source value
     */
    void csrd(final int csr, final int priv, final long val);

    /**
     * load a sign-extended 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lb(final long address) {
        return (int) signExtend(read(address, 1), 8);
    }

    /**
     * load an unsigned 1-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lbu(final long address) {
        return (int) read(address, 1);
    }

    /**
     * load a sign-extended 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lh(final long address) {
        return (int) signExtend(read(address, 2), 16);
    }

    /**
     * load an unsigned 2-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lhu(final long address) {
        return (int) read(address, 2);
    }

    /**
     * load a sign-extended 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lw(final long address) {
        return (int) signExtend(read(address, 4), 32);
    }

    /**
     * load an unsigned 4-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default int lwu(final long address) {
        return (int) read(address, 4);
    }

    /**
     * load an 8-byte value.
     *
     * @param address source address
     * @return value at source address
     */
    default long ld(final long address) {
        return read(address, 8);
    }

    /**
     * store a 1-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sb(final long address, final byte value) {
        write(address, 1, value);
    }

    /**
     * store a 2-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sh(final long address, final short value) {
        write(address, 2, value);
    }

    /**
     * store a 4-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
    default void sw(final long address, final int value) {
        write(address, 4, value);
    }

    /**
     * store an 8-byte value.
     *
     * @param address destination address
     * @param value   source value
     */
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
    long read(final long address, final int size);

    /**
     * write a N-byte value.
     *
     * @param address destination address
     * @param size    value size
     * @param value   N-byte source value
     */
    void write(final long address, final int size, final long value);
}
