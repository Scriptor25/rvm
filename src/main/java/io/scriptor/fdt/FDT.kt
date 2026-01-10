package io.scriptor.fdt

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FDT {
    fun write(tree: Tree, buffer: ByteBuffer): ByteBuffer {
        val order = buffer.order()
        buffer.order(ByteOrder.BIG_ENDIAN)

        val strings = StringTable()

        buffer.position(Header.BYTES)

        val off_mem_rsvmap = buffer.position()
        buffer.putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(0)

        val off_dt_struct = buffer.position()
        tree.write(buffer, strings)

        val off_dt_strings = buffer.position()
        strings.write(buffer)

        val totalsize = buffer.position()

        val header = HeaderBuilder()
            .magic(0xD00DFEEDU)
            .totalsize(totalsize.toUInt())
            .off_dt_struct(off_dt_struct.toUInt())
            .off_dt_strings(off_dt_strings.toUInt())
            .off_mem_rsvmap(off_mem_rsvmap.toUInt())
            .version(17U)
            .last_comp_version(16U)
            .boot_cpuid_phys(0U)
            .size_dt_strings((totalsize - off_dt_strings).toUInt())
            .size_dt_struct((off_dt_strings - off_dt_struct).toUInt())
            .build()

        buffer.position(0)
        header.write(buffer)

        buffer.position(totalsize)
            .flip()
            .limit((buffer.limit() + 7) and 7.inv())
            .order(order)

        return buffer
    }
}
