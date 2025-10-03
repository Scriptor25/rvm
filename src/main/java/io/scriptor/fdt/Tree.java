package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

import static io.scriptor.fdt.Constant.FDT_END;

public record Tree(@NotNull Node root) {

    public void write(final @NotNull ByteBuffer buffer, final @NotNull StringTable strings) {
        root.write(buffer, strings);
        buffer.putInt(FDT_END);
    }

    public int size() {
        int size = 0;
        size += root.size();
        size += Integer.BYTES;
        return size;
    }
}
