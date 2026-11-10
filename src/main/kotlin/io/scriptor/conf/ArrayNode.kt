package io.scriptor.conf

import kotlin.reflect.KClass
import kotlin.reflect.cast

class ArrayNode : Node<Any>, Iterable<Node<*>> {

    private val list: MutableList<Node<*>> = ArrayList()

    fun add(value: Node<*>) {
        list.add(value)
    }

    fun remove(index: Int) {
        list.removeAt(index)
    }

    override operator fun <N : Node<*>> get(type: KClass<N>, index: Int): N {
        return type.cast(list[index])
    }

    override operator fun iterator(): MutableIterator<Node<*>> {
        return list.iterator()
    }
}
