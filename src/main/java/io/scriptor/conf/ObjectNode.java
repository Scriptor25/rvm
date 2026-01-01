package io.scriptor.conf;

import com.carrotsearch.hppc.ObjectObjectHashMap;
import com.carrotsearch.hppc.ObjectObjectMap;
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ObjectNode implements Node<Object>, Iterable<ObjectObjectCursor<String, Node<?>>> {

    private final ObjectObjectMap<String, Node<?>> map = new ObjectObjectHashMap<>();

    public ObjectNode() {
    }

    public @NotNull ObjectNode put(final @NotNull String key, final @NotNull Node<?> value) {
        map.put(key, value);
        return this;
    }

    public @NotNull ObjectNode remove(final @NotNull String key) {
        map.remove(key);
        return this;
    }

    @Override
    public boolean has(final @NotNull String key) {
        return map.containsKey(key);
    }

    @Override
    public <N extends Node<?>> @NotNull N get(final @NotNull Class<N> type, final @NotNull String key) {
        if (map.containsKey(key))
            return type.cast(map.get(key));
        throw new NoSuchElementException(key);
    }

    @Override
    public @NotNull Iterator<ObjectObjectCursor<String, Node<?>>> iterator() {
        return map.iterator();
    }
}
