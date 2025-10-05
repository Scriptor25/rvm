package io.scriptor.fdt;

import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.ObjectIndexedContainer;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class StringTable {

    private final ObjectIndexedContainer<String> entries = new ObjectArrayList<>();

    public int push(final @NotNull String string) {
        int offset = 0;
        for (final var entry : entries) {
            if (Objects.equals(entry.value, string)) {
                return offset;
            }
            offset += entry.value.getBytes().length + 1;
        }

        entries.add(string);
        return offset;
    }

    public void write(final @NotNull ByteBuffer buffer) {
        for (final var entry : entries) {
            buffer.put(entry.value.getBytes());
            buffer.put((byte) 0);
        }
    }

    public int size() {
        int offset = 0;
        for (final var entry : entries) {
            offset += entry.value.getBytes().length + 1;
        }
        return offset;
    }
}
