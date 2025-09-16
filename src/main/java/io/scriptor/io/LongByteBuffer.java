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

    public @NotNull ByteBuffer[] chunks() {
        return chunks;
    }

    public byte @NotNull [] bytes(final long offset, final int length) {
        final var bytes = new byte[length];

        final var chunkOffset    = (int) (offset / (long) chunkSize);
        final var chunkOffsetRem = (int) (offset % (long) chunkSize);
        final var chunkCount     = (int) (length / (long) chunkSize);
        final var chunkCountRem  = (int) (length % (long) chunkSize);

        for (int i = 0; i < chunkCount; ++i) {
            chunks[i + chunkOffset].position(i == 0 ? chunkOffsetRem : 0)
                                   .get(bytes, i * chunkSize, chunkSize);
        }

        if (chunkCountRem != 0) {
            chunks[chunkCount + chunkOffset].position(chunkCount == 0 ? chunkOffsetRem : 0)
                                            .get(bytes, chunkCount * chunkSize, chunkCountRem);
        }

        return bytes;
    }

    public @NotNull LongByteBuffer reset() {
        for (final var chunk : chunks) {
            chunk.position(0)
                 .limit(chunkSize);
        }

        return this;
    }

    public @NotNull LongByteBuffer range(final long begin, final long end) {
        final var beginChunk  = (int) (begin / (long) chunkSize);
        final var beginOffset = (int) (begin % (long) chunkSize);
        final var endChunk    = (int) (end / (long) chunkSize);
        final var endOffset   = (int) (end % (long) chunkSize);

        for (final var chunk : chunks) {
            chunk.position(0)
                 .limit(chunkSize);
        }
        for (int i = 0; i < beginChunk; ++i) {
            chunks[i].limit(0);
        }
        for (int i = endChunk; i < chunks.length; ++i) {
            chunks[i].limit(0);
        }

        chunks[beginChunk].position(beginOffset);
        chunks[endChunk].limit(endOffset);

        return this;
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
}
