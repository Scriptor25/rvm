package io.scriptor.impl.device

import io.scriptor.fdt.BuilderContext
import io.scriptor.fdt.NodeBuilder
import io.scriptor.machine.Device
import io.scriptor.machine.IODevice
import io.scriptor.machine.Machine
import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.ByteBuffer

@OptIn(ExperimentalUnsignedTypes::class)
class Memory : IODevice {

    override val machine: Machine

    override val begin: ULong
    override val end: ULong

    private val capacity: UInt
    private val readonly: Boolean

    private val buffer: ByteBuffer

    constructor(machine: Machine, begin: ULong, capacity: UInt, readonly: Boolean) {
        this.machine = machine
        this.begin = begin
        this.capacity = capacity
        this.readonly = readonly

        this.end = begin + capacity
        this.buffer = ByteBuffer.allocateDirect(capacity.toInt()).order(machine.order)
    }

    override fun dump(out: PrintStream) {
        // ByteUtil.dump(out, buffer);
    }

    override fun reset() {
    }

    override fun build(context: BuilderContext<Device>, builder: NodeBuilder) {
        val phandle = context.get(this)

        if (readonly) {
            builder
                .name(format("rom@%x", begin))
                .prop { it.name("phandle").data(phandle) }
                .prop { it.name("reg").data(begin, end - begin) }
                .prop { it.name("no-map").data() }
                .prop { it.name("readonly").data() }
        } else {
            builder
                .name(format("memory@%x", begin))
                .prop { it.name("phandle").data(phandle) }
                .prop { it.name("device_type").data("memory") }
                .prop { it.name("reg").data(begin, end - begin) }
        }
    }

    override fun read(offset: UInt, size: UInt): ULong {
        if (0U > offset || offset + size > capacity) {
            Log.error("invalid memory read offset=%x, size=%d", offset, size)
            return 0UL
        }

        return when (size) {
            1U -> buffer.get(offset.toInt()).toUByte().toULong()
            2U -> buffer.getShort(offset.toInt()).toUShort().toULong()
            4U -> buffer.getInt(offset.toInt()).toUInt().toULong()
            8U -> buffer.getLong(offset.toInt()).toULong()
            else -> {
                Log.error("invalid memory read offset=%x, size=%d", offset, size)
                0UL
            }
        }
    }

    override fun write(offset: UInt, size: UInt, value: ULong) {
        if (readonly) {
            Log.error("invalid read-only memory write offset=%x, size=%d, value=%x", offset, size, value)
            return
        }

        if (0U > offset || offset + size > capacity) {
            Log.error("invalid memory write offset=%x, size=%d, value=%x", offset, size, value)
            return
        }

        when (size) {
            1U -> buffer.put(offset.toInt(), value.toByte())
            2U -> buffer.putShort(offset.toInt(), value.toShort())
            4U -> buffer.putInt(offset.toInt(), value.toInt())
            8U -> buffer.putLong(offset.toInt(), value.toLong())
            else -> Log.error("invalid memory write offset=%x, size=%d, value=%x", offset, size, value)
        }
    }

    override fun toString(): String {
        return format("memory@%x", begin)
    }

    fun direct(data: ByteArray, offset: UInt, write: Boolean) {
        if (offset > capacity || offset + data.size.toUInt() > capacity) {
            Log.warn("direct read/write out of bounds: offset=%x, length=%d", offset, data.size)
            return
        }

        if (write) {
            buffer.put(offset.toInt(), data, 0, data.size)
        } else {
            buffer.get(offset.toInt(), data, 0, data.size)
        }
    }

    fun buffer(): ByteBuffer {
        return buffer
    }

    fun export(filename: String) {
        FileOutputStream(filename).use { stream ->
            stream.channel.write(buffer)
        }
    }
}
