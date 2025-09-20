package io.scriptor.util;

public final class Unit {

    private Unit() {
    }

    public static long KiB(final long n) {
        return n * 1024L;
    }

    public static long MiB(final long n) {
        return n * 1024L * 1024L;
    }

    public static long GiB(final long n) {
        return n * 1024L * 1024L * 1024L;
    }
}
