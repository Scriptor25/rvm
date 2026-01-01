package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

public interface Node<T> {

    default boolean has(final @NotNull String key) {
        return false;
    }

    default <N extends Node<?>> @NotNull N get(final @NotNull Class<N> type, final @NotNull String key) {
        throw new UnsupportedOperationException();
    }

    default <N extends Node<?>> @NotNull N get(final @NotNull Class<N> type, final int index) {
        throw new UnsupportedOperationException();
    }

    default @NotNull T value() {
        throw new UnsupportedOperationException();
    }
}
