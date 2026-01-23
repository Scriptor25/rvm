package io.scriptor.conf

import kotlin.reflect.KClass
import kotlin.reflect.cast

class ObjectNode : Node<Any>, Iterable<Map.Entry<String, Node<*>>> {
    private val map: MutableMap<String, Node<*>> = HashMap()

    operator fun set(key: String, value: Node<*>) {
        map[key] = value
    }

    fun remove(key: String) {
        map.remove(key)
    }

    override operator fun contains(key: String): Boolean = key in map

    override operator fun <N : Node<*>> get(type: KClass<N>, key: String): N {
        if (key in map) {
            return type.cast(map[key])
        }
        throw NoSuchElementException(key)
    }

    override operator fun iterator(): MutableIterator<Map.Entry<String, Node<*>>> {
        return map.iterator()
    }
}
