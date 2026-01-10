package io.scriptor.util

import java.io.InputStream
import java.nio.ByteBuffer

abstract class ExtendedInputStream : InputStream() {

    abstract fun tell(): Long
    abstract fun seek(pos: Long)
    abstract fun size(): Long
    abstract fun read(buffer: ByteBuffer)
}
