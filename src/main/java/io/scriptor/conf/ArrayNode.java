package io.scriptor.conf;

import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public final class ArrayNode implements Node<Object>, Iterable<ObjectCursor<Node<?>>> {

    private final ObjectArrayList<Node<?>> list = new ObjectArrayList<>();

    public ArrayNode() {
    }

    public @NotNull ArrayNode add(final @NotNull Node<?> value) {
        list.add(value);
        return this;
    }

    public @NotNull ArrayNode remove(final int index) {
        list.removeAt(index);
        return this;
    }

    @Override
    public <N extends Node<?>> @NotNull N get(final @NotNull Class<N> type, final int index) {
        return type.cast(list.get(index));
    }

    @Override
    public @NotNull Iterator<ObjectCursor<Node<?>>> iterator() {
        return list.iterator();
    }
}
