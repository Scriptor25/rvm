package io.scriptor.isa

import io.scriptor.util.Log.format

data class Type(val label: String, val operands: Array<Operand>) {

    override fun toString(): String {
        return format("type %s %s", label, operands)
    }
}
