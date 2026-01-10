package io.scriptor.machine

interface GPRFile : Device {

    /**
     * read an 8-byte value from a general purpose register.
     * 
     * @param reg source register
     */
    fun getdu(reg: UInt): ULong
    fun getd(reg: UInt): Long = getdu(reg).toLong()
    fun getwu(reg: UInt): UInt = getdu(reg).toUInt()
    fun getw(reg: UInt): Int = getd(reg).toInt()
    fun gethu(reg: UInt): UShort = getdu(reg).toUShort()
    fun geth(reg: UInt): Short = getd(reg).toShort()
    fun getbu(reg: UInt): UByte = getdu(reg).toUByte()
    fun getb(reg: UInt): Byte = getd(reg).toByte()

    /**
     * write an 8-byte value to a general purpose register.
     * 
     * @param reg   destination register
     * @param value source value
     */
    fun put(reg: UInt, value: ULong)
    fun put(reg: UInt, value: Long) = put(reg, value.toULong())
    fun put(reg: UInt, value: UInt) = put(reg, value.toULong())
    fun put(reg: UInt, value: Int) = put(reg, value.toLong())
    fun put(reg: UInt, value: UShort) = put(reg, value.toULong())
    fun put(reg: UInt, value: Short) = put(reg, value.toLong())
    fun put(reg: UInt, value: UByte) = put(reg, value.toULong())
    fun put(reg: UInt, value: Byte) = put(reg, value.toLong())

    operator fun set(reg: UInt, value: ULong) = put(reg, value)
    operator fun set(reg: UInt, value: Long) = put(reg, value)
    operator fun set(reg: UInt, value: UInt) = put(reg, value)
    operator fun set(reg: UInt, value: Int) = put(reg, value)
    operator fun set(reg: UInt, value: UShort) = put(reg, value)
    operator fun set(reg: UInt, value: Short) = put(reg, value)
    operator fun set(reg: UInt, value: UByte) = put(reg, value)
    operator fun set(reg: UInt, value: Byte) = put(reg, value)
}
