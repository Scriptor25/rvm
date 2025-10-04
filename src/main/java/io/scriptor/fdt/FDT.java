package io.scriptor.fdt;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FDT {

    private FDT() {
    }

    public static @NotNull ByteBuffer write(final @NotNull Tree tree, final @NotNull ByteBuffer buffer) {
        final var order = buffer.order();
        buffer.order(ByteOrder.BIG_ENDIAN);

        final var strings = new StringTable();

        buffer.position(Header.BYTES);

        final var off_mem_rsvmap = buffer.position();
        buffer.putInt(0)
              .putInt(0)
              .putInt(0)
              .putInt(0);

        final var off_dt_struct = buffer.position();
        tree.write(buffer, strings);

        final var off_dt_strings = buffer.position();
        strings.write(buffer);

        final var totalsize = buffer.position();

        final var header = new HeaderBuilder()
                .magic(0xD00DFEED)
                .totalsize(totalsize)
                .off_dt_struct(off_dt_struct)
                .off_dt_strings(off_dt_strings)
                .off_mem_rsvmap(off_mem_rsvmap)
                .version(17)
                .last_comp_version(16)
                .boot_cpuid_phys(0)
                .size_dt_strings(totalsize - off_dt_strings)
                .size_dt_struct(off_dt_strings - off_dt_struct)
                .build();

        buffer.position(0);
        header.write(buffer);

        buffer.position(totalsize)
              .flip()
              .limit((buffer.limit() + 7) & ~7)
              .order(order);

        return buffer;
    }
}
