package io.scriptor.isa

import io.scriptor.util.Log.format

@OptIn(ExperimentalUnsignedTypes::class)
data class Instruction(
    val mnemonic: String,
    val ilen: UInt,
    val mask: UInt,
    val bits: UInt,
    val restriction: UInt,
    val operands: Array<Operand>,
) {

    fun test(value: UInt): Boolean {
        if (bits != (value and mask)) {
            return false
        }
        for (operand in operands) {
            if (operand.excludes(value)) {
                return false
            }
        }
        return true
    }

    fun decode(instruction: UInt, label: String): UInt {
        for (operand in operands) {
            if (operand.label == label) {
                return operand.extract(instruction)
            }
        }
        throw NoSuchElementException(label)
    }

    fun decode(instruction: UInt, values: UIntArray, vararg labels: String) {
        require(values.size >= labels.size)

        for (i in labels.indices) {
            values[i] = decode(instruction, labels[i])
        }
    }

    override fun toString(): String {
        return format(
            "%s [%d -> %08x & %08x] %s",
            mnemonic,
            ilen,
            bits,
            mask,
            operands.contentToString(),
        )
    }
}
