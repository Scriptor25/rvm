package io.scriptor.util

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class ArgContext {
    private val values: MutableMap<String, MutableList<String>> = HashMap()

    fun parse(args: Array<String>) {
        var i = 0
        while (i < args.size) {
            if (args[i] in flags) {
                values.computeIfAbsent(args[i]) { ArrayList() }
                i++
                continue
            }
            if (args[i] in options) {
                val name = args[i++]
                val value = args[i]
                values.computeIfAbsent(name) { ArrayList() }.add(value)
                i++
                continue
            }
            Log.warn("unused argument '%s'", args[i])
            i++
        }
    }

    operator fun contains(name: String): Boolean = name in values

    operator fun <T> get(
        name: String,
        present: Function<String, T>,
        empty: Supplier<T>,
    ): T {
        if (name in values && !values[name]!!.isEmpty()) {
            return present.apply(values[name]!![0])
        }
        return empty.get()
    }

    operator fun get(name: String): String {
        if (name in values && !values[name]!!.isEmpty()) {
            return values[name]!![0]
        }
        throw NoSuchElementException(name)
    }

    operator fun get(name: String, action: Consumer<String>) {
        if (name in values && !values[name]!!.isEmpty()) {
            for (entry in values[name]!!) {
                action.accept(entry)
            }
        }
    }

    companion object {
        private val flags = mutableSetOf("--debug")
        private val options = mutableSetOf("--config", "--load", "--port", "--level")
    }
}
