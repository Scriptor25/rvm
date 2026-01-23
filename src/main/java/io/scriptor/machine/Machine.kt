package io.scriptor.machine

import io.scriptor.elf.SymbolTable
import io.scriptor.isa.Registry
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.IntConsumer
import java.util.function.Predicate
import kotlin.reflect.KClass

interface Machine : Device {

    val registry: Registry;
    val order: ByteOrder
    val symbols: SymbolTable
    val harts: Array<Hart>

    fun <T : Device> device(type: KClass<T>): T
    fun <T : Device> device(type: KClass<T>, index: Int): T
    fun <T : Device> device(type: KClass<T>, predicate: Predicate<T>)
    fun <T : IODevice> device(type: KClass<T>, address: ULong): T?
    fun <T : IODevice> device(type: KClass<T>, address: ULong, capacity: UInt): T?

    fun <T : IODevice> has(type: KClass<T>): Boolean

    operator fun <T : Device> get(type: KClass<T>): T = device(type)
    operator fun <T : Device> get(type: KClass<T>, index: Int): T = device(type, index)
    operator fun <T : Device> get(type: KClass<T>, predicate: Predicate<T>) = device(type, predicate)
    operator fun <T : IODevice> get(type: KClass<T>, address: ULong): T? = device(type, address)
    operator fun <T : IODevice> get(type: KClass<T>, address: ULong, capacity: UInt): T? =
        device(type, address, capacity)

    operator fun <T : IODevice> contains(type: KClass<T>): Boolean = has(type)

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
