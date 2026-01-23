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

    fun execute(instruction: UInt, definition: Instruction): ULong

    fun sleeping(): Boolean

    fun wake()

    fun privilege(): UInt

    fun translate(vaddr: ULong, access: MMU.Access, unsafe: Boolean): ULong

    fun lb(vaddr: ULong): Byte {
        return read(vaddr, 1U, false).toByte()
    }

    fun lbu(vaddr: ULong): UByte {
        return read(vaddr, 1U, false).toUByte()
    }

    fun lh(vaddr: ULong): Short {
        return read(vaddr, 2U, false).toShort()
    }

    fun lhu(vaddr: ULong): UShort {
        return read(vaddr, 2U, false).toUShort()
    }

    fun lw(vaddr: ULong): Int {
        return read(vaddr, 4U, false).toInt()
    }

    fun lwu(vaddr: ULong): UInt {
        return read(vaddr, 4U, false).toUInt()
    }

    fun ld(vaddr: ULong): Long {
        return read(vaddr, 8U, false).toLong()
    }

    fun ldu(vaddr: ULong): ULong {
        return read(vaddr, 8U, false)
    }

    fun lstring(vaddr: ULong): String {
        var ptr = vaddr
        val buffer = ByteArrayOutputStream()
        var b: Byte
        while ((lb(ptr++).also { b = it }).toInt() != 0) {
            buffer.write(b.toInt())
        }
        return buffer.toString()
    }

    fun sb(vaddr: ULong, value: Byte) {
        write(vaddr, 1U, value.toUByte().toULong(), false)
    }

    fun sb(vaddr: ULong, value: UByte) {
        write(vaddr, 1U, value.toULong(), false)
    }

    fun sh(vaddr: ULong, value: Short) {
        write(vaddr, 2U, value.toUShort().toULong(), false)
    }

    fun sh(vaddr: ULong, value: UShort) {
        write(vaddr, 2U, value.toULong(), false)
    }

    fun sw(vaddr: ULong, value: Int) {
        write(vaddr, 4U, value.toUInt().toULong(), false)
    }

    fun sw(vaddr: ULong, value: UInt) {
        write(vaddr, 4U, value.toULong(), false)
    }

    fun sd(vaddr: ULong, value: Long) {
        write(vaddr, 8U, value.toULong(), false)
    }

    fun sd(vaddr: ULong, value: ULong) {
        write(vaddr, 8U, value, false)
    }

    fun fetch(vaddr: ULong, unsafe: Boolean): UInt {
        val paddr = translate(vaddr, MMU.Access.FETCH, unsafe)

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr)
        }

        val device = machine[IODevice::class, paddr]

        if (device != null && device.begin <= paddr && paddr + 4U <= device.end) {
            return device.read((paddr - device.begin).toUInt(), 4U).toUInt()
        }

        if (unsafe) {
            return 0u
        }

        throw TrapException(id, 0x01UL, paddr, "fetch invalid address: address=%x", paddr)
    }

    fun read(vaddr: ULong, size: UInt, unsafe: Boolean): ULong {
        val paddr = translate(vaddr, MMU.Access.READ, unsafe)

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr)
        }

        return machine.pRead(paddr, size, unsafe)
    }

    fun write(vaddr: ULong, size: UInt, value: ULong, unsafe: Boolean) {
        val paddr = translate(vaddr, MMU.Access.WRITE, unsafe)

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr)
        }

        machine.pWrite(paddr, size, value, unsafe)
    }

    fun direct(data: ByteArray, vaddr: ULong, write: Boolean) {
        val paddr = translate(vaddr, if (write) MMU.Access.WRITE else MMU.Access.READ, true)

        if (paddr != vaddr) {
            Log.info("virtual address %016x -> physical address %016x", vaddr, paddr)
        }

        machine.pDirect(data, paddr, write)
    }
}
