package io.scriptor.fdt

import java.nio.ByteBuffer

class PropBuilder : Buildable<Prop> {

    private var name: String? = null
    private var data: Writable? = null

    fun name(name: String): PropBuilder {
        this.name = name
        return this
    }

    fun data(data: Writable): PropBuilder {
        this.data = data
        return this
    }

    fun data(): PropBuilder {
        this.data = object : Writable {
            override fun write(buffer: ByteBuffer) {
            }

            override fun size(): Int {
                return 0
            }
        }
        return this
    }

    fun data(vararg data: Byte): PropBuilder {
        return data(
            object : Writable {
                override fun write(buffer: ByteBuffer) {
                    buffer.put(data)
                }

                override fun size(): Int {
                    return data.size
                }
            },
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun data(vararg data: UByte): PropBuilder {
        return data(
            object : Writable {
                override fun write(buffer: ByteBuffer) {
                    buffer.put(data.toByteArray())
                }

                override fun size(): Int {
                    return data.size
                }
            },
        )
    }

    fun data(vararg data: Int): PropBuilder {
        return data(
            object : Writable {
                override fun write(buffer: ByteBuffer) {
                    for (d in data) {
                        buffer.putInt(d)
                    }
                }

                override fun size(): Int {
                    return Int.SIZE_BYTES * data.size
                }
            },
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun data(vararg data: UInt): PropBuilder {
        return data(
            object : Writable {
                override fun write(buffer: ByteBuffer) {
                    for (d in data) {
                        buffer.putInt(d.toInt())
                    }
                }

                override fun size(): Int {
                    return UInt.SIZE_BYTES * data.size
                }
            },
        )
    }

    fun data(vararg data: Long): PropBuilder {
        return data(
            object : Writable {
                override fun write(buffer: ByteBuffer) {
                    for (d in data) {
                        buffer.putLong(d)
                    }
                }

                override fun size(): Int {
                    return Long.SIZE_BYTES * data.size
                }
            },
        )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun data(vararg data: ULong): PropBuilder {
        return data(
            object : Writable {
                override fun write(buffer: ByteBuffer) {
                    for (d in data) {
                        buffer.putLong(d.toLong())
                    }
                }

                override fun size(): Int {
                    return ULong.SIZE_BYTES * data.size
                }
            },
        )
    }

    fun data(data: String): PropBuilder {
        val bytes = data.toByteArray()
        val buffer = ByteArray(bytes.size + 1)
        System.arraycopy(bytes, 0, buffer, 0, bytes.size)
        buffer[bytes.size] = 0
        return data(*buffer)
    }

    override fun build(): Prop {
        return Prop(checkNotNull(name) { "missing prop name" }, checkNotNull(data) { "missing prop data" })
    }
}
