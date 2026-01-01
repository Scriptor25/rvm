package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

public record FloatingNode(@NotNull Double value) implements Node<Double> {
}
