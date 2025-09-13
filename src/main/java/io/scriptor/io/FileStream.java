package io.scriptor.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public class FileStream extends IOStream {

    private final FileChannel channel;

    public FileStream(final @NotNull String filename, final boolean writable) throws IOException {
        final Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        if (writable) {
            options.add(StandardOpenOption.WRITE);
        }
        channel = FileChannel.open(Path.of(filename), options);
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
    public long read(final @NotNull LongByteBuffer buffer) throws IOException {
        return channel.read(buffer.chunks());
    }

    @Override
    public long write(final byte b) throws IOException {
        return channel.write(ByteBuffer.wrap(new byte[] { b }));
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
