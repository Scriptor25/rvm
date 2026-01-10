package io.scriptor.isa

import io.scriptor.util.Log.format

data class Operand(
    val label: String,
    val segments: Array<Segment>,
    val exclude: MutableSet<UInt>,
) {
    fun excludes(value: UInt): Boolean {
        return exclude.contains(extract(value))
    }

    fun extract(instruction: UInt): UInt {
        var result = 0u
        for (segment in segments) {
            val width = (segment.hi - segment.lo) + 1u
            val mask = (1u shl width.toInt()) - 1u
            val bits = (instruction shr segment.lo.toInt()) and mask
            result = result or (bits shl segment.shift.toInt())
        }
        return result
    }

    override fun toString(): String {
        return format("%s%s!%s", label, segments.contentToString(), exclude)
    }
}
