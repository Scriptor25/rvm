package io.scriptor.isa;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public record Operand(
        @NotNull String label,
        @NotNull List<Segment> segments,
        @NotNull Set<Integer> exclude
) {

    @Contract(pure = true)
    public boolean excludes(final int value) {
        return exclude.contains(extract(value));
    }

    @Contract(pure = true)
    public int extract(final int value) {
        int result = 0;
        for (final var segment : segments) {
            final var width = (segment.hi() - segment.lo()) + 1;
            final var mask  = (1 << width) - 1;
            final var bits  = (value >>> segment.lo()) & mask;
            result |= bits << segment.shift();
        }
        return result;
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return "%s%s!%s".formatted(label, segments, exclude);
    }
}
