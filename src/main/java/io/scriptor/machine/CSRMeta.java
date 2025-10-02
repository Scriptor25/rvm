package io.scriptor.machine;

import org.jetbrains.annotations.Nullable;

import java.util.function.LongSupplier;

public record CSRMeta(long mask, int base, @Nullable LongSupplier get, java.util.function.LongConsumer set) {
}
