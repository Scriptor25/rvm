package io.scriptor.elf

import io.scriptor.util.Log.format
import java.util.*

class SymbolTable : Iterable<Symbol> {
    private val map = TreeMap<ULong, String>()

    fun resolve(addr: ULong): String {
        if (map.isEmpty()) {
            return NULL
        }

        val entry = map.floorEntry(addr)
        if (entry == null) {
            return NULL
        }

        if (entry.key == addr) {
            return entry.value
        }

        return format("%s+0x%x", entry.value, addr - entry.key)
    }

    fun put(addr: ULong, name: String) {
        map[addr] = name
    }

    override fun iterator(): MutableIterator<Symbol> {
        return map.entries
            .stream()
            .map { entry -> Symbol(entry.key, entry.value) }
            .iterator()
    }

    companion object {
        private const val NULL = "(null)"
    }
}
