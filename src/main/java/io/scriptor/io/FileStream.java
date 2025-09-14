package io.scriptor.io;

import org.jetbrains.annotations.NotNull;
import sun.nio.ch.FileChannelImpl;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileStream extends IOStream {

    public static final IOStream stdin = new FileStream(FileDescriptor.in, true, false);
    public static final IOStream stdout = new FileStream(FileDescriptor.out, false, true);
    public static final IOStream stderr = new FileStream(FileDescriptor.err, false, true);

    private final FileChannel channel;

    public FileStream(final @NotNull String filename, final boolean writable) throws IOException {
        final OpenOption option;
        if (writable) {
            option = StandardOpenOption.WRITE;
        } else {
            option = StandardOpenOption.READ;
        }
        channel = FileChannel.open(Path.of(filename), option);
    }

    public FileStream(final @NotNull FileDescriptor fd, final boolean readable, final boolean writable) {
        channel = FileChannelImpl.open(fd, null, readable, writable, false, false, this);
    }

    @Override
    public boolean ok() throws IOException {
        return channel.position() < channel.size();
    }

    @Override
    public void seek(final long pos) throws IOException {
        channel.position(pos);
    }

    @Override
    public long tell() throws IOException {
        return channel.position();
    }

    @Override
    public byte read() throws IOException {
        final var buffer = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder());
        if (channel.read(buffer) < 1)
            throw new EOFException();
        return buffer.get(0);
    }

    @Override
    public byte @NotNull [] read(final int count) throws IOException {
        final var buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder());
        if (channel.read(buffer) < count)
            throw new EOFException();
        final var bytes = new byte[count];
        buffer.get(0, bytes, 0, count);
        return bytes;
    }

    @Override
    public long read(final byte @NotNull [] bytes) throws IOException {
        return channel.read(ByteBuffer.wrap(bytes));
    }

    @Override
    public long read(final @NotNull LongByteBuffer buffer) throws IOException {
        return channel.read(buffer.chunks());
    }

    @Override
    public void write(final byte b) throws IOException {
        final var buffer = ByteBuffer.allocateDirect(1)
                                     .order(ByteOrder.nativeOrder())
                                     .put(0, b);
        if (channel.write(buffer) < 1)
            throw new EOFException();
    }

    @Override
    public long write(final byte @NotNull [] bytes) throws IOException {
        return channel.write(ByteBuffer.wrap(bytes));
    }

    @Override
    public long write(final @NotNull LongByteBuffer buffer) throws IOException {
        return channel.write(buffer.chunks());
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
