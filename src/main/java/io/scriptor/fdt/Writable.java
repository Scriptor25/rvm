package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public interface Writable {

    void write(@NotNull ByteBuffer buffer);

    int size();
}
