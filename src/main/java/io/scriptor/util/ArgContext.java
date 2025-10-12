package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;

public class ArgContext {

    private static final Set<String> flags = Set.of("--debug");
    private static final Set<String> options = Set.of("--memory", "--order", "--harts", "--load", "--entry");

    private final Map<String, List<String>> values = new HashMap<>();

    public ArgContext() {
    }

    public void parse(final @NotNull String @NotNull [] args) {
        for (int i = 0; i < args.length; ++i) {
            if (flags.contains(args[i])) {
                values.computeIfAbsent(args[i], _ -> new ArrayList<>());
                continue;
            }
            if (options.contains(args[i])) {
                final var name  = args[i++];
                final var value = args[i];
                values.computeIfAbsent(name, _ -> new ArrayList<>()).add(value);
                continue;
            }
            Log.warn("unused argument '%s'", args[i]);
        }
    }

    public boolean has(final @NotNull String name) {
        return values.containsKey(name);
    }

    public <T> T get(
            final @NotNull String name,
            final @NotNull Function<String, T> present,
            final @NotNull Supplier<T> empty
    ) {
        if (values.containsKey(name) && !values.get(name).isEmpty()) {
            return present.apply(values.get(name).getFirst());
        }
        return empty.get();
    }

    public int getInt(
            final @NotNull String name,
            final @NotNull ToIntFunction<String> present,
            final @NotNull IntSupplier empty
    ) {
        if (values.containsKey(name) && !values.get(name).isEmpty()) {
            return present.applyAsInt(values.get(name).getFirst());
        }
        return empty.getAsInt();
    }


    public long getLong(
            final @NotNull String name,
            final @NotNull ToLongFunction<String> present,
            final @NotNull LongSupplier empty
    ) {
        if (values.containsKey(name) && !values.get(name).isEmpty()) {
            return present.applyAsLong(values.get(name).getFirst());
        }
        return empty.getAsLong();
    }

    public void getAll(final @NotNull String name, final @NotNull Consumer<String> action) {
        if (values.containsKey(name) && !values.get(name).isEmpty()) {
            for (final var entry : values.get(name)) {
                action.accept(entry);
            }
        }
    }
}
