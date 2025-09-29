package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ChannelInputStream extends ExtendedInputStream {

    private final FileInputStream stream;
    private final FileChannel channel;

    public ChannelInputStream(final @NotNull String name) throws FileNotFoundException {
        super();
        this.stream = new FileInputStream(name);
        this.channel = stream.getChannel();
    }

    public ChannelInputStream(final @NotNull File file) throws FileNotFoundException {
        super();
        this.stream = new FileInputStream(file);
        this.channel = stream.getChannel();
    }

    public ChannelInputStream(final @NotNull FileDescriptor fd) {
        super();
        this.stream = new FileInputStream(fd);
        this.channel = stream.getChannel();
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public int read(final byte @NotNull [] b) throws IOException {
        return stream.read(b);
    }

    @Override
    public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
        return stream.read(b, off, len);
    }

    @Override
    public byte @NotNull [] readAllBytes() throws IOException {
        return stream.readAllBytes();
    }

    @Override
    public byte @NotNull [] readNBytes(final int len) throws IOException {
        return stream.readNBytes(len);
    }

    @Override
    public int readNBytes(final byte @NotNull [] b, final int off, final int len) throws IOException {
        return stream.readNBytes(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return stream.skip(n);
    }

    @Override
    public void skipNBytes(final long n) throws IOException {
        stream.skipNBytes(n);
    }

    @Override
    public int available() throws IOException {
        return stream.available();
    }

    @Override
    public void mark(final int readlimit) {
        stream.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        stream.reset();
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    @Override
    public long transferTo(final @NotNull OutputStream out) throws IOException {
        return stream.transferTo(out);
    }

    @Override
    public long tell() throws IOException {
        return channel.position();
    }

    @Override
    public void seek(final long pos) throws IOException {
        channel.position(pos);
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public void read(final @NotNull ByteBuffer buffer) throws IOException {
        channel.read(buffer);
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
