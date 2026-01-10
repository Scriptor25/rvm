package io.scriptor.machine

interface FPRFile : Device {

    fun getf(reg: UInt): Float = Float.fromBits(getfr(reg).toInt())
    fun getd(reg: UInt): Double = Double.fromBits(getdr(reg).toLong())

    fun put(reg: UInt, value: Float) = put(reg, value.toRawBits().toUInt())
    fun put(reg: UInt, value: Double) = put(reg, value.toRawBits().toULong())

    fun getfr(reg: UInt): UInt
    fun getdr(reg: UInt): ULong

    fun put(reg: UInt, value: UInt)
    fun put(reg: UInt, value: ULong)

    operator fun set(reg: UInt, value: Float) = put(reg, value)
    operator fun set(reg: UInt, value: Double) = put(reg, value)
    operator fun set(reg: UInt, value: UInt) = put(reg, value)
    operator fun set(reg: UInt, value: ULong) = put(reg, value)
}
