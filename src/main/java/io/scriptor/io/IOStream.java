package io.scriptor.io;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class IOStream implements Closeable {

    @Contract(pure = true)
    public boolean ok() throws IOException {
        return false;
    }

    @Contract(mutates = "io,this")
    public IOStream seek(final long pos) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(pure = true)
    public long tell() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(pure = true)
    public long size() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this")
    public byte read() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this")
    public byte @NotNull [] read(final int count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this,param")
    public long read(final byte @NotNull [] bytes) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this,param")
    public long read(final @NotNull ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this,param1")
    public long read(final @NotNull LongByteBuffer buffer, final long index, final long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this")
    public void write(final byte b) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this")
    public long write(final byte @NotNull [] bytes) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Contract(mutates = "io,this")
    public long write(final @NotNull LongByteBuffer buffer, final long index, final long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    @Contract(pure = true)
    public void close() throws IOException {
    }
}
