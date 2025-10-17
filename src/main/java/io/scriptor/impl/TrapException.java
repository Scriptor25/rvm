package io.scriptor.impl;

import org.jetbrains.annotations.NotNull;

public final class TrapException extends RuntimeException {

    private final int id;
    private final long cause;
    private final long value;
    private final String message;

    public TrapException(
            final int id,
            final long cause,
            final long value,
            final @NotNull String message
    ) {
        super("trap id=%02x, cause=%016x, value=%016x: %s".formatted(id, cause, value, message));

        this.id = id;
        this.cause = cause;
        this.value = value;
        this.message = message;
    }

    public TrapException(
            final int id,
            final long cause,
            final long value,
            final @NotNull String format,
            final Object @NotNull ... args
    ) {
        this(id, cause, value, format.formatted(args));
    }

    public TrapException(final int id, final @NotNull TrapException original) {
        this(id, original.cause, original.value, original.message);
    }

    public int getId() {
        return id;
    }

    public long getTrapCause() {
        return cause;
    }

    public long getTrapValue() {
        return value;
    }

    public @NotNull String getTrapMessage() {
        return message;
    }
}
