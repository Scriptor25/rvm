package io.scriptor.util;

public final class IntSet {

    private static final int EMPTY = Integer.MIN_VALUE;

    private int[] table;
    private int size;
    private int threshold;

    public IntSet() {
        this(10);
    }

    public IntSet(final int capacity) {
        int pow2 = 1;
        while (pow2 < capacity) {
            pow2 <<= 1;
        }
        table = new int[pow2];
        for (int i = 0; i < pow2; ++i) {
            table[i] = EMPTY;
        }
    }

    public boolean add(final int value) {
        if (size >= threshold) {
            resize();
        }

        int index = index(value);
        while (table[index] != EMPTY) {
            if (table[index] == value) {
                return false;
            }
            index = (index + 1) & (table.length - 1);
        }
        table[index] = value;
        size++;
        return true;
    }

    public boolean contains(final int value) {
        int index = index(value);
        while (table[index] != EMPTY) {
            if (table[index] == value) {
                return true;
            }
            index = (index + 1) & (table.length - 1);
        }
        return false;
    }

    private int index(final int value) {
        return Integer.hashCode(value) & (table.length - 1);
    }

    private void resize() {
        final var set = new IntSet(table.length << 1);
        for (int v : table) {
            if (v != EMPTY) {
                set.add(v);
            }
        }
        this.table = set.table;
        this.threshold = set.threshold;
    }
}
