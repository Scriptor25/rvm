package io.scriptor.arg;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PayloadMap(@NotNull Map<Integer, List<Payload>> payloads) {

    public <T extends Payload> @NotNull Optional<T> get(final int id, final @NotNull Class<T> type) {
        return payloads.getOrDefault(id, List.of()).stream().filter(type::isInstance).map(type::cast).findAny();
    }

    public <T extends Payload> @NotNull List<T> getAll(final int id, final @NotNull Class<T> type) {
        return payloads.getOrDefault(id, List.of()).stream().filter(type::isInstance).map(type::cast).toList();
    }
}
