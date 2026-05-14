package io.scriptor.machine

import io.scriptor.impl.MMU
import io.scriptor.impl.TrapException
import io.scriptor.isa.Instruction
import io.scriptor.util.Log
import java.io.ByteArrayOutputStream

interface Hart : Device {

    val id: Int
    var pc: ULong

    val gprFile: GPRFile
    val fprFile: FPRFile
    val csrFile: CSRFile

    val mmu: MMU

    val privilege: UInt
    val sleeping: Boolean

    fun execute(instruction: UInt, definition: Instruction): ULong

    fun wake()

    fun translate(vAddress: ULong, access: MMU.Access, unsafe: Boolean): ULong {
        return mmu.translate(privilege, vAddress, access, unsafe)
    }

    fun lb(vAddress: ULong): Byte {
        return read(vAddress, 1U, false).toByte()
    }

    fun lbu(vAddress: ULong): UByte {
        return read(vAddress, 1U, false).toUByte()
    }

    fun lh(vAddress: ULong): Short {
        return read(vAddress, 2U, false).toShort()
    }

    fun lhu(vAddress: ULong): UShort {
        return read(vAddress, 2U, false).toUShort()
    }

    fun lw(vAddress: ULong): Int {
        return read(vAddress, 4U, false).toInt()
    }

    fun lwu(vAddress: ULong): UInt {
        return read(vAddress, 4U, false).toUInt()
    }

    fun ld(vAddress: ULong): Long {
        return read(vAddress, 8U, false).toLong()
    }

    fun ldu(vAddress: ULong): ULong {
        return read(vAddress, 8U, false)
    }

    fun lstring(vAddress: ULong): String {
        var ptr = vAddress
        val buffer = ByteArrayOutputStream()
        var b: Byte
        while ((lb(ptr++).also { b = it }).toInt() != 0) {
            buffer.write(b.toInt())
        }
        return buffer.toString()
    }

    fun sb(vAddress: ULong, value: Byte) {
        write(vAddress, 1U, value.toUByte().toULong(), false)
    }

    fun sb(vAddress: ULong, value: UByte) {
        write(vAddress, 1U, value.toULong(), false)
    }

    fun sh(vAddress: ULong, value: Short) {
        write(vAddress, 2U, value.toUShort().toULong(), false)
    }

    fun sh(vAddress: ULong, value: UShort) {
        write(vAddress, 2U, value.toULong(), false)
    }

    fun sw(vAddress: ULong, value: Int) {
        write(vAddress, 4U, value.toUInt().toULong(), false)
    }

    fun sw(vAddress: ULong, value: UInt) {
        write(vAddress, 4U, value.toULong(), false)
    }

    fun sd(vAddress: ULong, value: Long) {
        write(vAddress, 8U, value.toULong(), false)
    }

    fun sd(vAddress: ULong, value: ULong) {
        write(vAddress, 8U, value, false)
    }

    fun fetch(vAddress: ULong, unsafe: Boolean): UInt {
        val pAddress = translate(vAddress, MMU.Access.FETCH, unsafe)

        if (pAddress != vAddress) {
            Log.info("virtual address %016x -> physical address %016x", vAddress, pAddress)
        }

        val value = machine.get(
            IODevice::class,
            pAddress,
            pAddress + 4UL
        ) { device -> device.read((pAddress - device.begin).toUInt(), 4U)?.toUInt() }

        if (value != null) {
            return value
        }

        if (unsafe) {
            return 0U
        }

        throw TrapException(id, 0x01UL, pAddress, "fetch invalid address: address=%x", pAddress)
    }

    fun read(vAddress: ULong, size: UInt, unsafe: Boolean): ULong {
        val pAddress = translate(vAddress, MMU.Access.READ, unsafe)

        if (pAddress != vAddress) {
            Log.info("virtual address %016x -> physical address %016x", vAddress, pAddress)
        }

        return machine.pRead(pAddress, size, unsafe)
    }

    fun write(vAddress: ULong, size: UInt, value: ULong, unsafe: Boolean) {
        val pAddress = translate(vAddress, MMU.Access.WRITE, unsafe)

        if (pAddress != vAddress) {
            Log.info("virtual address %016x -> physical address %016x", vAddress, pAddress)
        }

        return machine.pWrite(pAddress, size, value, unsafe)
    }

    fun direct(data: ByteArray, vAddress: ULong, write: Boolean): Boolean {
        val pAddress = translate(vAddress, if (write) MMU.Access.WRITE else MMU.Access.READ, true)

        if (pAddress != vAddress) {
            Log.info("virtual address %016x -> physical address %016x", vAddress, pAddress)
        }

        return machine.pDirect(data, pAddress, write)
    }
}
