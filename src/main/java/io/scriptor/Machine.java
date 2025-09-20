package io.scriptor;

import io.scriptor.io.IOStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;

public interface Machine {

    void reset();

    void dump(final @NotNull PrintStream out);

    boolean step();

    void setEntry(final long address);

    void loadDirect(final @NotNull IOStream stream) throws IOException;

    void loadSegment(final @NotNull IOStream stream, final long address, final long size) throws IOException;

    int fetch(final boolean unsafe);

    int lb(final long address);

    int lbu(final long address);

    int lh(final long address);

    int lhu(final long address);

    int lw(final long address);

    int lwu(final long address);

    long ld(final long address);

    void sb(final long address, final byte value);

    void sh(final long address, final short value);

    void sw(final long address, final int value);

    void sd(final long address, final long value);
}
