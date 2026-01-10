package io.scriptor.machine

import java.util.function.Consumer
import java.util.function.Supplier

interface CSRFile : Device {

    fun define(addr: UInt)
    fun define(addr: UInt, mask: ULong)

    fun define(addr: UInt, mask: ULong, base: Int)

    fun define(addr: UInt, mask: ULong, base: UInt) = define(addr, mask, base.toInt())

    fun define(addr: UInt, mask: ULong, base: Int, value: ULong)

    fun define(addr: UInt, mask: ULong, base: UInt, value: ULong) = define(addr, mask, base.toInt(), value)

    fun defineVal(addr: UInt, value: ULong)

    fun define(addr: UInt, mask: ULong, get: Supplier<ULong>)
    fun define(addr: UInt, mask: ULong, get: Supplier<ULong>, set: Consumer<ULong>)

    fun hookGet(addr: UInt, hook: Consumer<ULong>)
    fun hookPut(addr: UInt, hook: Consumer<ULong>)

    /**
     * read an 8-byte value from a control/status register.
     * 
     * @param addr source register
     * @param priv privilege level
     */
    fun getdu(addr: UInt, priv: UInt): ULong
    fun getd(addr: UInt, priv: UInt): Long = getdu(addr, priv).toLong()

    /**
     * write an 8-byte value to a control/status register.
     * 
     * @param addr  destination register
     * @param priv  privilege level
     * @param value source value
     */
    fun put(addr: UInt, priv: UInt, value: ULong)
    fun put(addr: UInt, priv: UInt, value: Long) = put(addr, priv, value.toULong())

    operator fun set(addr: UInt, priv: UInt, value: ULong) = put(addr, priv, value)
    operator fun set(addr: UInt, priv: UInt, value: Long) = put(addr, priv, value)
}
