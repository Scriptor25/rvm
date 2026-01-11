package io.scriptor.machine

import io.scriptor.elf.SymbolTable
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.IntConsumer
import java.util.function.Predicate

interface Machine : Device {

    val order: ByteOrder
    val symbols: SymbolTable
    val harts: Array<Hart>

    fun <T : Device> device(type: Class<T>): T
    fun <T : Device> device(type: Class<T>, index: Int): T
    fun <T : Device> device(type: Class<T>, predicate: Predicate<T>)
    fun <T : IODevice> device(type: Class<T>, address: ULong): T?
    fun <T : IODevice> device(type: Class<T>, address: ULong, capacity: UInt): T?

    operator fun <T : Device> get(type: Class<T>): T = device(type)
    operator fun <T : Device> get(type: Class<T>, index: Int): T = device(type, index)
    operator fun <T : Device> get(type: Class<T>, predicate: Predicate<T>) = device(type, predicate)
    operator fun <T : IODevice> get(type: Class<T>, address: ULong): T? = device(type, address)
    operator fun <T : IODevice> get(type: Class<T>, address: ULong, capacity: UInt): T? =
        device(type, address, capacity)

    fun dump(out: PrintStream, paddr: ULong, length: ULong)

    fun spinOnce()

    fun spin()

    fun pause()

    fun setBreakpointHandler(handler: IntConsumer)

    fun handleBreakpoint(id: Int): Boolean

    fun acquireLock(address: ULong): Any

    fun pRead(paddr: ULong, size: UInt, unsafe: Boolean): ULong

    fun pWrite(paddr: ULong, size: UInt, value: ULong, unsafe: Boolean)

    fun pDirect(data: ByteArray, paddr: ULong, write: Boolean)

    fun generateDeviceTree(buffer: ByteBuffer)
}
