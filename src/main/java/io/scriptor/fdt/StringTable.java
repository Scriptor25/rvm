package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StringTable {

    private final List<String> entries = new ArrayList<>();

    public int push(final @NotNull String string) {
        int offset = 0;
        for (final var entry : entries) {
            if (Objects.equals(entry, string)) {
                return offset;
            }
            offset += entry.getBytes().length + 1;
        }

        entries.add(string);
        return offset;
    }

    public void write(final @NotNull ByteBuffer buffer) {
        for (final var entry : entries) {
            buffer.put(entry.getBytes());
            buffer.put((byte) 0);
        }
    }

    public int size() {
        int offset = 0;
        for (final var entry : entries) {
            offset += entry.getBytes().length + 1;
        }
        return offset;
    }
}
