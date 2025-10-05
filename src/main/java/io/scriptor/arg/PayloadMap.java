package io.scriptor.arg;

import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.ObjectIndexedContainer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Optional;

public record PayloadMap(@NotNull IntObjectMap<ObjectIndexedContainer<Payload>> payloads) {

    public <T extends Payload> @NotNull Optional<T> get(final int id, final @NotNull Class<T> type) {
        if (payloads.containsKey(id)) {
            return payloads.get(id)
                           .stream()
                           .filter(type::isInstance)
                           .map(type::cast)
                           .findAny();
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public <T extends Payload> T @NotNull [] getAll(final int id, final @NotNull Class<T> type) {
        if (payloads.containsKey(id)) {
            return payloads.get(id)
                           .stream()
                           .filter(type::isInstance)
                           .map(type::cast)
                           .toArray(length -> (T[]) Array.newInstance(type, length));
        }
        return (T[]) Array.newInstance(type, 0);
    }
}
