package io.scriptor.isa;

import io.scriptor.util.IntSet;
import org.jetbrains.annotations.NotNull;

public record Operand(
        @NotNull String label,
        @NotNull Segment[] segments,
        @NotNull IntSet exclude
) {

    public boolean excludes(final int value) {
        return exclude.contains(extract(value));
    }

    public int extract(final int instruction) {
        int result = 0;
        for (final var segment : segments) {
            final var width = (segment.hi() - segment.lo()) + 1;
            final var mask  = (1 << width) - 1;
            final var bits  = (instruction >>> segment.lo()) & mask;
            result |= bits << segment.shift();
        }
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "%s%s!%s".formatted(label, segments, exclude);
    }
}
