package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

public record Token(@NotNull TokenType type, @NotNull String string, long integer, double floating) {
}
