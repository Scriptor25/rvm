package io.scriptor.util;

import org.jetbrains.annotations.Contract;

public final class Unit {

    private Unit() {
    }

    @Contract(pure = true)
    public static long KiB(final long n) {
        return n * 1024L;
    }

    @Contract(pure = true)
    public static long MiB(final long n) {
        return n * 1024L * 1024L;
    }

    @Contract(pure = true)
    public static long GiB(final long n) {
        return n * 1024L * 1024L * 1024L;
    }
}
