package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

public interface Buildable<T> {

    T build();

    default void build(final @NotNull Consumer<T> consumer) {
        consumer.accept(build());
    }

    default <V> V map(final @NotNull Function<T, V> mapper) {
        return mapper.apply(build());
    }
}
