package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public final class PropBuilder implements Buildable<Prop> {

    public static @NotNull PropBuilder create() {
        return new PropBuilder();
    }

    private String name;
    private Writable data;

    private PropBuilder() {
    }

    public @NotNull PropBuilder name(final @NotNull String name) {
        this.name = name;
        return this;
    }

    public @NotNull PropBuilder data(final @NotNull Writable data) {
        this.data = data;
        return this;
    }

    public @NotNull PropBuilder data() {
        this.data = new Writable() {
            @Override
            public void write(final @NotNull ByteBuffer buffer) {
            }

            @Override
            public int size() {
                return 0;
            }
        };
        return this;
    }

    public @NotNull PropBuilder data(final byte @NotNull ... data) {
        return data(new Writable() {

            @Override
            public void write(final @NotNull ByteBuffer buffer) {
                buffer.put(data);
            }

            @Override
            public int size() {
                return data.length;
            }
        });
    }

    public @NotNull PropBuilder data(final int @NotNull ... data) {
        return data(new Writable() {

            @Override
            public void write(final @NotNull ByteBuffer buffer) {
                for (final var d : data) {
                    buffer.putInt(d);
                }
            }

            @Override
            public int size() {
                return Integer.BYTES * data.length;
            }
        });
    }

    public @NotNull PropBuilder data(final long @NotNull ... data) {
        return data(new Writable() {

            @Override
            public void write(final @NotNull ByteBuffer buffer) {
                for (final var d : data) {
                    buffer.putLong(d);
                }
            }

            @Override
            public int size() {
                return Long.BYTES * data.length;
            }
        });
    }

    public @NotNull PropBuilder data(final @NotNull String data) {
        final var bytes  = data.getBytes();
        final var buffer = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, buffer, 0, bytes.length);
        buffer[bytes.length] = '\0';
        return data(buffer);
    }

    @Override
    public @NotNull Prop build() {
        if (name == null) {
            throw new IllegalStateException("missing prop name");
        }
        if (data == null) {
            throw new IllegalStateException("missing prop data");
        }
        return new Prop(name, data);
    }
}
