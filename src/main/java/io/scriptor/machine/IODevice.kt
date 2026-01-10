package io.scriptor.machine

interface IODevice : Device {

    val begin: ULong
    val end: ULong

    fun read(offset: UInt, size: UInt): ULong
    fun write(offset: UInt, size: UInt, value: ULong)
}
