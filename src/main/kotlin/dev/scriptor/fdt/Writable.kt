package dev.scriptor.fdt

import java.nio.ByteBuffer

interface Writable {
    fun write(buffer: ByteBuffer)

    fun size(): Int
}
