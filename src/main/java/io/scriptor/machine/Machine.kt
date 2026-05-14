package io.scriptor.machine

import io.scriptor.elf.SymbolTable
import io.scriptor.impl.TrapException
import io.scriptor.impl.device.DeviceTree
import io.scriptor.impl.device.Memory
import io.scriptor.isa.Registry
import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.IntConsumer
import java.util.function.Predicate
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.cast

interface Machine {

    fun interface Consumer<T, R> {
        fun accept(value: T): R?
    }

    val registry: Registry;
    val order: ByteOrder
    val symbols: SymbolTable
    val harts: Array<Hart>

    val devices: Array<Device>

    var once: Boolean
    var active: Boolean

    var breakpointHandler: IntConsumer?
    val locks: MutableMap<ULong, Any>

    operator fun <T : Device> get(
        type: KClass<T>
    ): T {
        for (device in devices) {
            if (type.isInstance(device)) {
                return type.cast(device)
            }
        }
        throw NoSuchElementException()
    }

    operator fun <T : Device> get(
        type: KClass<T>,
        index: Int
    ): T {
        var index = index
        for (device in devices) {
            if (type.isInstance(device)
                && index-- <= 0
            ) {
                return type.cast(device)
            }
        }
        throw NoSuchElementException()
    }

    operator fun <T : Device> get(
        type: KClass<T>,
        predicate: Predicate<T>
    ): T? {
        for (device in devices) {
            if (type.isInstance(device)
                && predicate.test(type.cast(device))
            ) {
                return type.cast(device)
            }
        }
        return null
    }

    operator fun <T : IODevice> get(
        type: KClass<T>,
        address: ULong
    ): T? {
        for (device in devices) {
            if (device is IODevice
                && address in device.begin..<device.end
                && type.isInstance(device)
            ) {
                return type.cast(device)
            }
        }
        return null
    }

    operator fun <T : IODevice> get(
        type: KClass<T>,
        address: ULong,
        capacity: UInt
    ): T? {
        for (device in devices) {
            if (device is IODevice
                && address in device.begin..<device.end
                && capacity <= (device.end - device.begin).toUInt()
                && type.isInstance(device)
            ) {
                return type.cast(device)
            }
        }
        return null
    }

    operator fun <T : IODevice> get(
        type: KClass<T>,
        begin: ULong,
        end: ULong
    ): T? {
        for (device in devices) {
            if (device is IODevice
                && begin in device.begin..<device.end
                && end in device.begin..device.end
                && type.isInstance(device)
            ) {
                return type.cast(device)
            }
        }
        return null
    }

    operator fun <T : Device, R> get(
        type: KClass<T>,
        predicate: Predicate<T>,
        consumer: Consumer<T, R>
    ): R? {
        for (device in devices) {
            if (type.isInstance(device)
                && predicate.test(type.cast(device))
            ) {
                val result = consumer.accept(type.cast(device))
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    operator fun <T : IODevice, R> get(
        type: KClass<T>,
        address: ULong,
        consumer: Consumer<T, R>
    ): R? {
        for (device in devices) {
            if (device is IODevice
                && address in device.begin..<device.end
                && type.isInstance(device)
            ) {
                val result = consumer.accept(type.cast(device))
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    operator fun <T : IODevice, R> get(
        type: KClass<T>,
        address: ULong,
        capacity: UInt,
        consumer: Consumer<T, R>
    ): R? {
        for (device in devices) {
            if (device is IODevice
                && address in device.begin..<device.end
                && capacity <= (device.end - device.begin).toUInt()
                && type.isInstance(device)
            ) {
                val result = consumer.accept(type.cast(device))
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    operator fun <T : IODevice, R> get(
        type: KClass<T>,
        begin: ULong,
        end: ULong,
        consumer: Consumer<T, R>
    ): R? {
        for (device in devices) {
            if (device is IODevice
                && begin in device.begin..<device.end
                && end in device.begin..device.end
                && type.isInstance(device)
            ) {
                val result = consumer.accept(type.cast(device))
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    operator fun <T : IODevice> contains(type: KClass<T>): Boolean {
        for (device in devices) {
            if (type.isInstance(device)) {
                return true
            }
        }
        return false
    }

    fun spinOnce() {
        once = true
        active = true
    }

    fun spin() {
        once = false
        active = true
    }

    fun pause() {
        once = false
        active = false
    }

    fun dump(out: PrintStream) {
        out.printf("active=%b, once=%b, order=%s\n", active, once, order)
        for (hart in harts) {
            hart.dump(out)
        }
        for (device in devices) {
            device.dump(out)
        }
    }

    fun dump(out: PrintStream, pAddress: ULong, length: ULong) {
        if (length == 0UL) {
            out.println("<empty>")
            return
        }

        val CHUNK = 0x20

        var allZero = false
        var allZeroBegin = 0UL

        var i = 0UL
        while (i < length) {
            val chunk = min(length - i, CHUNK.toULong()).toInt()
            if (chunk <= 0) break

            val buffer = ByteArray(chunk)
            pDirect(buffer, pAddress + i, false)

            var allZeroP = true
            for (j in 0..<chunk)
                if (buffer[j].toInt() != 0) {
                    allZeroP = false
                    break
                }

            if (allZero && !allZeroP) {
                allZero = false
                out.println(format("%016x - %016x", allZeroBegin, i - 1U))
            } else if (!allZero && allZeroP) {
                allZero = true
                allZeroBegin = i
                i += CHUNK.toULong()
                continue
            } else if (allZero) {
                i += CHUNK.toULong()
                continue
            }

            out.print(format("%016x |", pAddress + i))

            for (j in 0..<chunk) out.print(format(" %02x", buffer[j]))
            for (j in chunk..<CHUNK) out.print(" 00")

            out.print(" | ")

            for (j in 0..<chunk) out.print(if (buffer[j] >= 0x20) Char(buffer[j].toUShort()) else '.')
            for (j in chunk..<CHUNK) out.print('.')

            out.println()
            i += CHUNK.toULong()
        }
        out.println("(END)")
    }

    fun handleBreakpoint(id: Int): Boolean {
        if (breakpointHandler != null) {
            breakpointHandler!!.accept(id)
            return true
        }
        return false
    }

    fun acquireLock(address: ULong): Any {
        if (address in locks)
            return locks[address]!!

        val lock = Any()
        locks[address] = lock
        return lock
    }

    fun pRead(pAddress: ULong, size: UInt, unsafe: Boolean): ULong {
        for (device in devices) {
            if (device is IODevice) {
                if (device.begin <= pAddress && pAddress + size <= device.end) {
                    val value = device.read((pAddress - device.begin).toUInt(), size)
                    if (value != null) {
                        return value
                    }
                }
            }
        }

        if (unsafe) {
            Log.warn("read invalid address: address=%x, size=%d", pAddress, size)
            return 0UL
        }

        throw TrapException(-1, 0x05UL, pAddress, "read invalid address: address=%x, size=%d", pAddress, size)
    }

    fun pWrite(pAddress: ULong, size: UInt, value: ULong, unsafe: Boolean) {
        for (device in devices) {
            if (device is IODevice) {
                if (device.begin <= pAddress && pAddress + size <= device.end) {
                    if (device.write((pAddress - device.begin).toUInt(), size, value)) {
                        return
                    }
                }
            }
        }

        if (unsafe) {
            Log.warn("write invalid address: address=%x, size=%d, value=%x", pAddress, size, value)
            return
        }

        throw TrapException(
            -1,
            0x07UL,
            pAddress,
            "write invalid address: address=%x, size=%d, value=%x",
            pAddress,
            size,
            value,
        )
    }

    fun pDirect(data: ByteArray, pAddress: ULong, write: Boolean): Boolean {
        for (device in devices) {
            if (device is Memory) {
                if (device.begin <= pAddress && pAddress + data.size.toUInt() <= device.end) {
                    if (device.direct(data, (pAddress - device.begin).toUInt(), write)) {
                        return true
                    }
                }
            }
        }

        Log.warn("direct read/write invalid address: address=%x, length=%d", pAddress, data.size)
        return false
    }

    fun generateDeviceTree(buffer: ByteBuffer)

    fun reset() {
        once = false
        active = false

        var dt: DeviceTree? = null

        for (device in devices) {
            device.reset()

            if (device is DeviceTree) {
                dt = device
            }
        }

        for (hart in harts) {
            hart.reset()
            hart.gprFile[0x0AU] = hart.id.toUInt() // boot hart id
            hart.gprFile[0x0BU] = dt?.begin ?: 0U  // device tree address
        }
    }

    fun step() {
        if (!active)
            return

        for (device in devices)
            device.step()

        for (hart in harts)
            hart.step()

        if (once) {
            active = false
            handleBreakpoint(-1)
        }
    }

    fun close() {
        for (hart in harts) hart.close()
        for (device in devices) device.close()
    }
}
