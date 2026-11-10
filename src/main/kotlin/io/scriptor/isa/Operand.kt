package io.scriptor.isa

import io.scriptor.util.Log.format

data class Operand(
    val label: String,
    val segments: List<Segment>,
    val exclude: MutableSet<Int>,
) {
    fun excludes(value: Int): Boolean {
        return decode(value) in exclude
    }

    fun decode(instruction: Int): Int {
        var result = 0
        for (segment in segments) {
            val width = (segment.hi - segment.lo) + 1
            val mask = (1 shl width) - 1
            val bits = (instruction shr segment.lo) and mask
            result = result or (bits shl segment.shift)
        }
        return result
    }

    fun encode(instruction: Int, value: Int): Int {
        var result = instruction
        for (segment in segments) {
            val width = (segment.hi - segment.lo) + 1
            val mask = (1 shl width) - 1
            val bits = (value shr segment.shift) and mask
            result = result and (mask shl segment.lo).inv()
            result = result or (bits shl segment.lo)
        }
        return result
    }

    override fun toString(): String {
        return format("%s%s!%s", label, segments, exclude)
    }
}
