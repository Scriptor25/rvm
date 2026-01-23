package io.scriptor.conf

import kotlin.reflect.KClass

interface Node<T> {
    operator fun contains(key: String): Boolean {
        return false
    }

    operator fun <N : Node<*>> get(type: KClass<N>, key: String): N {
        throw UnsupportedOperationException()
    }

    operator fun <N : Node<*>> get(type: KClass<N>, index: Int): N {
        throw UnsupportedOperationException()
    }

    fun value(): T {
        throw UnsupportedOperationException()
    }
}
