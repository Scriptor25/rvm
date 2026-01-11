package io.scriptor.isa

import io.scriptor.util.Log.format

data class Instruction(
    val mnemonic: String,
    val ilen: UInt,
    val mask: Int,
    val bits: Int,
    val restriction: UInt,
    val operands: Array<Operand>,
) {

    fun test(value: Int): Boolean {
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

    fun decode(instruction: Int, label: String): Int {
        for (operand in operands) {
            if (operand.label == label) {
                return operand.extract(instruction)
            }
        }
        throw NoSuchElementException(label)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun decode(instruction: UInt, values: UIntArray, label0: String) {
        require(values.size >= 1)

        values[0] = decode(instruction.toInt(), label0).toUInt()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun decode(instruction: UInt, values: UIntArray, label0: String, label1: String) {
        require(values.size >= 2)

        values[0] = decode(instruction.toInt(), label0).toUInt()
        values[1] = decode(instruction.toInt(), label1).toUInt()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun decode(instruction: UInt, values: UIntArray, label0: String, label1: String, label2: String) {
        require(values.size >= 3)

        values[0] = decode(instruction.toInt(), label0).toUInt()
        values[1] = decode(instruction.toInt(), label1).toUInt()
        values[2] = decode(instruction.toInt(), label2).toUInt()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun decode(instruction: UInt, values: UIntArray, label0: String, label1: String, label2: String, label3: String) {
        require(values.size >= 4)

        values[0] = decode(instruction.toInt(), label0).toUInt()
        values[1] = decode(instruction.toInt(), label1).toUInt()
        values[2] = decode(instruction.toInt(), label2).toUInt()
        values[3] = decode(instruction.toInt(), label3).toUInt()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun decode(
        instruction: UInt,
        values: UIntArray,
        label0: String,
        label1: String,
        label2: String,
        label3: String,
        label4: String,
    ) {
        require(values.size >= 5)

        values[0] = decode(instruction.toInt(), label0).toUInt()
        values[1] = decode(instruction.toInt(), label1).toUInt()
        values[2] = decode(instruction.toInt(), label2).toUInt()
        values[3] = decode(instruction.toInt(), label3).toUInt()
        values[4] = decode(instruction.toInt(), label4).toUInt()
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
