package io.scriptor.io;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class IOStream implements Closeable {

    public boolean ok() throws IOException {
        return false;
    }

    public void seek(final long pos) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long tell() throws IOException {
        throw new UnsupportedOperationException();
    }

    public byte read() throws IOException {
        final var buffer = new LongByteBuffer(1, 1, ByteOrder.nativeOrder());
        read(buffer);
        return buffer.get(0);
    }

    public byte @NotNull [] read(final int count) throws IOException {
        final var buffer = new LongByteBuffer(count, count, ByteOrder.nativeOrder());
        read(buffer);
        return buffer.bytes(0, count);
    }

    public long read(final byte @NotNull [] bytes) throws IOException {
        final var buffer = new LongByteBuffer(ByteBuffer.wrap(bytes));
        return read(buffer);
    }

    public long read(final @NotNull LongByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    public long write(final byte b) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
    }
}
