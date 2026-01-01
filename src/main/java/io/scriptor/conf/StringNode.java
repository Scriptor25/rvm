package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

public record StringNode(@NotNull String value) implements Node<String> {
}
