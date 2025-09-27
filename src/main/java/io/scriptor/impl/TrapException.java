package io.scriptor.impl;

public final class TrapException extends RuntimeException {

    private final long cause;
    private final long value;

    public TrapException(final long cause, final long value) {
        super("trap cause=%016x value=%016x".formatted(cause, value));

        this.cause = cause;
        this.value = value;
    }

    public long getTrapCause() {
        return cause;
    }

    public long getTrapValue() {
        return value;
    }
}
