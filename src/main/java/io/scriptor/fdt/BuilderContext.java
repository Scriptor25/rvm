package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class BuilderContext<T> {

    private final Map<T, Integer> handles = new HashMap<>();

    public int push(final @NotNull T key) {
        final var handle = handles.size() + 1;
        handles.put(key, handle);
        return handle;
    }

    public int get(final @NotNull T key) {
        return handles.get(key);
    }
}
