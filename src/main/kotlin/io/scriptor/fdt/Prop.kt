package io.scriptor.fdt

import java.nio.ByteBuffer

data class Prop(val name: String, val data: Writable) {
    fun write(buffer: ByteBuffer, strings: StringTable) {
        val nameoff = strings.push(name)

        buffer
            .putInt(Constant.FDT_PROP.toInt())
            .putInt(data.size())
            .putInt(nameoff)

        data.write(buffer)
        while ((buffer.position() and 3) != 0) {
            buffer.put(0.toByte())
        }
    }

    fun size(): Int {
        var size = 0
        size += Integer.SIZE
        size += Integer.SIZE
        size += Integer.SIZE
        size += data.size()
        size = (size + 3) and 3.inv()
        return size
    }
}
