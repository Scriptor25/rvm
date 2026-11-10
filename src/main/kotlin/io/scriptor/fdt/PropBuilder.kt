package io.scriptor.fdt

import java.nio.ByteBuffer

class PropBuilder(val name: String) : Buildable<Prop> {

    lateinit var data: Writable

    fun data() {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
            }

            override fun size(): Int {
                return 0
            }
        }
    }

    fun data(vararg values: Byte) {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
                buffer.put(values)
            }

            override fun size(): Int {
                return values.size
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun data(vararg values: UByte) {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
                buffer.put(values.toByteArray())
            }

            override fun size(): Int {
                return values.size
            }
        }
    }

    fun data(vararg values: Int) {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
                for (d in values) {
                    buffer.putInt(d)
                }
            }

            override fun size(): Int {
                return Int.SIZE_BYTES * values.size
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun data(vararg values: UInt) {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
                for (d in values) {
                    buffer.putInt(d.toInt())
                }
            }

            override fun size(): Int {
                return UInt.SIZE_BYTES * values.size
            }
        }
    }

    fun data(vararg values: Long) {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
                for (d in values) {
                    buffer.putLong(d)
                }
            }

            override fun size(): Int {
                return Long.SIZE_BYTES * values.size
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun data(vararg values: ULong) {
        data = object : Writable {
            override fun write(buffer: ByteBuffer) {
                for (d in values) {
                    buffer.putLong(d.toLong())
                }
            }

            override fun size(): Int {
                return ULong.SIZE_BYTES * values.size
            }
        }
    }

    fun data(values: String) {
        val bytes = values.toByteArray()
        val buffer = ByteArray(bytes.size + 1)
        System.arraycopy(bytes, 0, buffer, 0, bytes.size)
        buffer[bytes.size] = 0
        data(*buffer)
    }

    override fun build(): Prop {
        return Prop(name, data)
    }
}
