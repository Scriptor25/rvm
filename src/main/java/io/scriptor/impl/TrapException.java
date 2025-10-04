package io.scriptor.impl;

import org.jetbrains.annotations.NotNull;

public final class TrapException extends RuntimeException {

    private final long cause;
    private final long value;
    private final String message;

    public TrapException(
            final long cause,
            final long value,
            final @NotNull String message
    ) {
        super("trap cause=%016x, value=%016x".formatted(cause, value));

        this.cause = cause;
        this.value = value;
        this.message = message;
    }

    public TrapException(
            final long cause,
            final long value,
            final @NotNull String format,
            final Object @NotNull ... args
    ) {
        super("trap cause=%016x, value=%016x".formatted(cause, value));

        this.cause = cause;
        this.value = value;
        this.message = format.formatted(args);
    }

    public long getTrapCause() {
        return cause;
    }

    public long getTrapValue() {
        return value;
    }

    @Override
    public @NotNull String getMessage() {
        return message;
    }
}
