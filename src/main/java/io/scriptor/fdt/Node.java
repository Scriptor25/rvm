package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

import static io.scriptor.fdt.Constant.FDT_BEGIN_NODE;
import static io.scriptor.fdt.Constant.FDT_END_NODE;

public record Node(@NotNull String name, @NotNull Prop @NotNull [] props, @NotNull Node @NotNull [] nodes) {

    public void write(final @NotNull ByteBuffer buffer, final @NotNull StringTable strings) {
        buffer.putInt(FDT_BEGIN_NODE);

        buffer.put(name.getBytes());

        do {
            buffer.put((byte) 0);
        } while ((buffer.position() & 3) != 0);

        for (final var prop : props) {
            prop.write(buffer, strings);
        }

        for (final var node : nodes) {
            node.write(buffer, strings);
        }

        buffer.putInt(FDT_END_NODE);
    }

    public int size() {
        int size = 0;
        size += Integer.SIZE;
        size += name.getBytes().length + 1;
        size = (size + 3) & ~3;
        for (final var prop : props) {
            size += prop.size();
        }
        for (final var node : nodes) {
            size += node.size();
        }
        size += Integer.SIZE;
        return size;
    }
}
