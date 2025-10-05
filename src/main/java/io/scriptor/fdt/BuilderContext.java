package io.scriptor.fdt;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import org.jetbrains.annotations.NotNull;

public final class BuilderContext<T> {

    private final ObjectIntMap<T> handles = new ObjectIntHashMap<>();

    public int push(final @NotNull T key) {
        final var handle = handles.size() + 1;
        handles.put(key, handle);
        return handle;
    }

    public int get(final @NotNull T key) {
        return handles.get(key);
    }
}
