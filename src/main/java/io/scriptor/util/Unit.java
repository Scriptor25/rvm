package io.scriptor.util;

public final class Unit {

    private Unit() {
    }

    public static int KiB(final int n) {
        return n << 0x0A;
    }

    public static int MiB(final int n) {
        return n << 0x14;
    }

    public static int GiB(final int n) {
        return n << 0x1E;
    }
}
