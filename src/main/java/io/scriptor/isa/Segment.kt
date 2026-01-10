package io.scriptor.isa

import io.scriptor.util.Log.format

data class Segment(val hi: UInt, val lo: UInt, val shift: UInt) {

    override fun toString(): String {
        return format("%d:%d<<%d", hi, lo, shift)
    }
}
