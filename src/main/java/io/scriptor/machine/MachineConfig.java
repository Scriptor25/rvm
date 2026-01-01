package io.scriptor.machine;

import io.scriptor.impl.MachineImpl;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

public final class MachineConfig {

    private int mode = 64;
    private int harts = 1;
    private @NotNull ByteOrder order = ByteOrder.nativeOrder();
    private final List<Function<Machine, Device>> devices = new ArrayList<>();

    public @NotNull MachineConfig mode(final int mode) {
        this.mode = mode;
        return this;
    }

    public @NotNull MachineConfig harts(final int harts) {
        this.harts = harts;
        return this;
    }

    public @NotNull MachineConfig order(final @NotNull ByteOrder order) {
        this.order = order;
        return this;
    }

    public @NotNull MachineConfig device(final @NotNull Function<Machine, Device> generator) {
        this.devices.add(generator);
        return this;
    }

    public @NotNull Machine configure() {
        return switch (mode) {
            case 64 -> new MachineImpl(harts, order, devices.toArray(Function[]::new));
            default -> throw new NoSuchElementException("mode=%d".formatted(mode));
        };
    }
}
