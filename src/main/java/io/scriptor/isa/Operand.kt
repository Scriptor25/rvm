package io.scriptor.isa

import io.scriptor.util.Log.format

data class Operand(
    val label: String,
    val segments: Array<Segment>,
    val exclude: MutableSet<Int>,
) {
    fun excludes(value: Int): Boolean {
        return extract(value) in exclude
    }

    fun extract(instruction: Int): Int {
        var result = 0
        for (segment in segments) {
            val width = (segment.hi - segment.lo) + 1
            val mask = (1 shl width) - 1
            val bits = (instruction shr segment.lo) and mask
            result = result or (bits shl segment.shift)
        }
        return result
    }

    override fun toString(): String {
        return format("%s%s!%s", label, segments.contentToString(), exclude)
    }
}
