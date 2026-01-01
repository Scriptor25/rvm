package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

public record BooleanNode(@NotNull Boolean value) implements Node<Boolean> {
}
