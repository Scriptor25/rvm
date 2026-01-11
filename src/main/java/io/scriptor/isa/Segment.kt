package io.scriptor.isa

import io.scriptor.util.Log.format

data class Segment(val hi: Int, val lo: Int, val shift: Int) {

    override fun toString(): String {
        return format("%d:%d<<%d", hi, lo, shift)
    }
}
