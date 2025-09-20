package io.scriptor.io;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class IOStream implements Closeable {

    public boolean ok() throws IOException {
        return false;
    }

    public IOStream seek(final long pos) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long tell() throws IOException {
        throw new UnsupportedOperationException();
    }

    public long size() throws IOException {
        throw new UnsupportedOperationException();
    }

    public byte read() throws IOException {
        throw new UnsupportedOperationException();
    }

    public byte @NotNull [] read(final int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long read(final byte @NotNull [] bytes) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long read(final @NotNull ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long read(final @NotNull LongByteBuffer buffer, final long index, final long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void write(final byte b) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long write(final byte @NotNull [] bytes) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long write(final @NotNull LongByteBuffer buffer, final long index, final long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
    }
}
