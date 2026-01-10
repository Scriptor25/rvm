package io.scriptor.util

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class ArgContext {
    private val values: MutableMap<String, MutableList<String>> = HashMap()

    fun parse(args: Array<String>) {
        var i = 0
        while (i < args.size) {
            if (flags.contains(args[i])) {
                values.computeIfAbsent(args[i]) { ArrayList() }
                i++
                continue
            }
            if (options.contains(args[i])) {
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

    operator fun contains(name: String): Boolean {
        return values.containsKey(name)
    }

    operator fun <T> get(
        name: String,
        present: Function<String, T>,
        empty: Supplier<T>,
    ): T {
        if (values.containsKey(name) && !values[name]!!.isEmpty()) {
            return present.apply(values[name]!![0])
        }
        return empty.get()
    }

    operator fun get(name: String): String {
        if (values.containsKey(name) && !values[name]!!.isEmpty()) {
            return values[name]!![0]
        }
        throw NoSuchElementException(name)
    }

    operator fun get(name: String, action: Consumer<String>) {
        if (values.containsKey(name) && !values[name]!!.isEmpty()) {
            for (entry in values[name]!!) {
                action.accept(entry)
            }
        }
    }

    companion object {
        private val flags = mutableSetOf("--debug")
        private val options = mutableSetOf("--config", "--load", "--port")
    }
}
