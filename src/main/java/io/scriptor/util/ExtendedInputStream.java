package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public abstract class ExtendedInputStream extends InputStream {

    public abstract long tell() throws IOException;

    public abstract void seek(long pos) throws IOException;

    public abstract long size() throws IOException;

    public abstract void read(@NotNull ByteBuffer buffer) throws IOException;
}
