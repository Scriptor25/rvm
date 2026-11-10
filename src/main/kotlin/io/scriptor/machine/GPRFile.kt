package io.scriptor.machine

interface GPRFile : Device {

    /**
     * read an 8-byte value from a general purpose register.
     * 
     * @param reg source register
     */
    fun getdu(reg: UInt): ULong
    fun getd(reg: UInt): Long
    fun getwu(reg: UInt): UInt
    fun getw(reg: UInt): Int
    fun gethu(reg: UInt): UShort
    fun geth(reg: UInt): Short
    fun getbu(reg: UInt): UByte
    fun getb(reg: UInt): Byte

    /**
     * write an 8-byte value to a general purpose register.
     * 
     * @param reg   destination register
     * @param value source value
     */
    fun put(reg: UInt, value: ULong)
    fun put(reg: UInt, value: Long)
    fun put(reg: UInt, value: UInt)
    fun put(reg: UInt, value: Int)
    fun put(reg: UInt, value: UShort)
    fun put(reg: UInt, value: Short)
    fun put(reg: UInt, value: UByte)
    fun put(reg: UInt, value: Byte)

    operator fun set(reg: UInt, value: ULong) = put(reg, value)
    operator fun set(reg: UInt, value: Long) = put(reg, value)
    operator fun set(reg: UInt, value: UInt) = put(reg, value)
    operator fun set(reg: UInt, value: Int) = put(reg, value)
    operator fun set(reg: UInt, value: UShort) = put(reg, value)
    operator fun set(reg: UInt, value: Short) = put(reg, value)
    operator fun set(reg: UInt, value: UByte) = put(reg, value)
    operator fun set(reg: UInt, value: Byte) = put(reg, value)
}
