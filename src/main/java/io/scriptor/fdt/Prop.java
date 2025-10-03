package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

import static io.scriptor.fdt.Constant.FDT_PROP;

public record Prop(@NotNull String name, @NotNull Writable data) {

    public void write(final @NotNull ByteBuffer buffer, final @NotNull StringTable strings) {
        final var nameoff = strings.push(name);

        buffer.putInt(FDT_PROP)
              .putInt(data.size())
              .putInt(nameoff);

        data.write(buffer);
        while ((buffer.position() & 3) != 0) {
            buffer.put((byte) 0);
        }
    }

    public int size() {
        int size = 0;
        size += Integer.SIZE;
        size += Integer.SIZE;
        size += Integer.SIZE;
        size += data.size();
        size = (size + 3) & ~3;
        return size;
    }
}
