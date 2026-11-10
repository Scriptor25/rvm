package io.scriptor.elf

import io.scriptor.util.Log.format

data class Symbol(val addr: ULong, val name: String) {
    override fun toString(): String {
        return format("%016x : '%s'", addr, name)
    }
}
