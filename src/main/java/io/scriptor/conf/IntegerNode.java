package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

public record IntegerNode(@NotNull Long value) implements Node<Long> {
}
