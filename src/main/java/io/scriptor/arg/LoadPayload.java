package io.scriptor.arg;

import org.jetbrains.annotations.NotNull;

public record LoadPayload(@NotNull String filename, long offset) implements Payload {
}
