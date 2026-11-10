package io.scriptor.fdt

import java.nio.ByteBuffer

class StringTable {
    private val entries: MutableList<String> = ArrayList()

    fun push(string: String): Int {
        var offset = 0
        for (entry in entries) {
            if (entry == string) {
                return offset
            }
            offset += entry.toByteArray().size + 1
        }

        entries.add(string)
        return offset
    }

    fun write(buffer: ByteBuffer) {
        for (entry in entries) {
            buffer.put(entry.toByteArray())
            buffer.put(0.toByte())
        }
    }

    fun size(): Int {
        var offset = 0
        for (entry in entries) {
            offset += entry.toByteArray().size + 1
        }
        return offset
    }
}
