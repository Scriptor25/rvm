package io.scriptor.isa

import io.scriptor.util.Log.format

data class Instruction(
    val mnemonic: String,
    val ilen: UInt,
    val mask: Int,
    val bits: Int,
    val restriction: UInt,
    val operands: Map<String, Operand>,
) {

    fun test(mode: UInt, value: Int): Boolean {
        when {
            restriction != 0U && restriction != mode -> {
                return false
            }

            bits != (value and mask) -> {
                return false
            }

            else -> {
                for (operand in operands.values) {
                    if (operand.excludes(value)) {
                        return false
                    }
                }
                return true
            }
        }
    }

    fun decode(instruction: Int, label: String): Int = when (label) {
        in operands -> operands[label]!!.decode(instruction)
        else -> throw NoSuchElementException(label)
    }

    fun encode(instruction: Int, label: String, value: Int): Int = when (label) {
        in operands -> operands[label]!!.encode(instruction, value)
        else -> throw NoSuchElementException(label)
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
            operands,
        )
    }
}
