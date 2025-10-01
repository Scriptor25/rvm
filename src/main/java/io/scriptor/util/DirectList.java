package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public final class DirectList<T> implements Collection<T> {

    private Object @NotNull [] buffer = new Object[10];
    private int size = 0;

    @Override
    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    public T get(final int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return (T) buffer[index];
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(final Object o) {
        for (final var b : buffer) {
            if (Objects.equals(b, o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return new Iterator<>() {

            int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            @SuppressWarnings("unchecked")
            public T next() {
                return (T) buffer[index++];
            }
        };
    }

    @Override
    public Object @NotNull [] toArray() {
        final var copy = new Object[size];
        System.arraycopy(buffer, 0, copy, 0, size);
        return copy;
    }

    @Override
    @SuppressWarnings("SuspiciousSystemArraycopy")
    public <A> A @NotNull [] toArray(A @NotNull [] a) {
        if (a.length < size) {
            a = Arrays.copyOf(a, size);
        }
        System.arraycopy(buffer, 0, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @Override
    public boolean add(final T t) {
        if (buffer.length <= size) {
            buffer = Arrays.copyOf(buffer, buffer.length << 1);
        }
        buffer[size++] = t;
        return true;
    }

    @Override
    public boolean remove(final Object o) {
        int i = 0;
        for (; i < size; ++i) {
            if (Objects.equals(buffer[i], o)) {
                break;
            }
        }
        if (i >= size) {
            return false;
        }
        System.arraycopy(buffer, i + 1, buffer, i, size - i - 1);
        buffer[--size] = null;
        return true;
    }

    @Override
    public boolean containsAll(final @NotNull Collection<?> c) {
        for (final var b : buffer) {
            for (final var o : c) {
                if (!Objects.equals(b, o)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean addAll(final @NotNull Collection<? extends T> c) {
        c.forEach(this::add);
        return !c.isEmpty();
    }

    @Override
    public boolean removeAll(final @NotNull Collection<?> c) {
        var removed = false;
        for (final var o : c) {
            removed |= remove(o);
        }
        return removed;
    }

    @Override
    public boolean retainAll(final @NotNull Collection<?> c) {
        final var remove = new Object[size];

        int p = 0;
        for (final var b : buffer) {
            for (final var o : c) {
                if (!Objects.equals(b, o)) {
                    remove[p++] = b;
                }
            }
        }

        final var removed = p != 0;

        for (; p >= 0; --p) {
            remove(remove[p]);
        }

        return removed;
    }

    @Override
    public void clear() {
        size = 0;
        Arrays.fill(buffer, null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof List<?> list) {
            if (size != list.size()) {
                return false;
            }
            for (int i = 0; i < size; ++i) {
                if (!Objects.equals(buffer[i], list.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(Arrays.copyOfRange(buffer, 0, size));
    }

    @Override
    public <A> A @NotNull [] toArray(final @NotNull IntFunction<A @NotNull []> generator) {
        return toArray(generator.apply(size));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(final @NotNull Consumer<? super T> action) {
        for (int i = 0; i < size; ++i) {
            action.accept((T) buffer[i]);
        }
    }
}
