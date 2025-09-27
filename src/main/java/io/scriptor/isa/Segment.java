package io.scriptor.isa;

import org.jetbrains.annotations.NotNull;

public record Segment(int hi, int lo, int shift) {

    @Override
    public @NotNull String toString() {
        return "%d:%d<<%d".formatted(hi, lo, shift);
    }
}
