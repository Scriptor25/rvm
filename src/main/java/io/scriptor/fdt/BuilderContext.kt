package io.scriptor.fdt

class BuilderContext<T> {

    private val handles: MutableMap<T, UInt> = HashMap()

    fun get(key: T): UInt {
        if (key in handles)
            return handles[key]!!

        val handle = handles.size.toUInt() + 1u
        handles[key] = handle
        return handle
    }
}
