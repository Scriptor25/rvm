package io.scriptor.io;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class LongByteBuffer {

    private final int chunkSize;
    private final ByteBuffer[] chunks;

    public LongByteBuffer(final int chunkSize, final long capacity) {
        final var chunkCount = (int) ((capacity + (long) chunkSize - 1L) / (long) chunkSize);

        this.chunkSize = chunkSize;
        this.chunks = new ByteBuffer[chunkCount];

        for (int i = 0; i < chunkCount; ++i) {
            chunks[i] = ByteBuffer.allocateDirect(chunkSize)
                                  .order(ByteOrder.nativeOrder());
        }
    }

    public byte get(final long index) {
        final var chunk  = (int) (index / (long) chunkSize);
        final var offset = (int) (index % (long) chunkSize);
        return chunks[chunk].get(offset);
    }

    public void get(final long index, final byte @NotNull [] bytes, final int offset, final int length) {
        for (int i = 0; i < length; ++i)
            bytes[offset + i] = get(index + i);
    }

    public void put(final long index, final byte value) {
        final var chunk  = (int) (index / (long) chunkSize);
        final var offset = (int) (index % (long) chunkSize);
        chunks[chunk].put(offset, value);
    }

    public void put(final long index, final byte @NotNull [] bytes, final int offset, final int length) {
        for (int i = 0; i < length; ++i)
            put(index + i, bytes[offset + i]);
    }

    public long capacity() {
        return (long) chunks.length * (long) chunkSize;
    }

    public @NotNull ByteBuffer[] range(final long index, final long count) {
        final var num = (int) ((count + (long) chunkSize - 1L) / (long) chunkSize);

        final var buffers = new ByteBuffer[num];

        var chunk  = (int) (index / (long) chunkSize);
        var offset = (int) (index % (long) chunkSize);

        var remainder = count;

        for (int i = 0; i < num; ++i) {
            final var buffer = chunks[chunk++];

            final int limit;
            if (remainder <= chunkSize) {
                limit = (int) remainder;
            } else {
                limit = chunkSize;
                remainder -= chunkSize;
            }

            buffers[i] = buffer.duplicate()
                               .order(buffer.order())
                               .position(offset)
                               .limit(limit);
            offset = 0;
        }

        return buffers;
    }
}
