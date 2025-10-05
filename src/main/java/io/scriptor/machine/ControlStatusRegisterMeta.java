package io.scriptor.machine;

import com.carrotsearch.hppc.ObjectIndexedContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public record ControlStatusRegisterMeta(
        long mask,
        int base,
        @Nullable LongSupplier get,
        @Nullable LongConsumer set,
        @NotNull ObjectIndexedContainer<LongConsumer> getHooks,
        @NotNull ObjectIndexedContainer<LongConsumer> setHooks
) {
}
