package io.scriptor.machine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public record ControlStatusRegisterMeta(
        long mask,
        int base,
        @Nullable LongSupplier get,
        @Nullable LongConsumer set,
        @NotNull List<LongConsumer> getHooks,
        @NotNull List<LongConsumer> setHooks
) {
}
