package dev.scriptor.isa

import dev.scriptor.util.Log.format

data class Type(val label: String, val operands: Map<String, Operand>) {

    override fun toString(): String {
        return format("type %s %s", label, operands)
    }
}
