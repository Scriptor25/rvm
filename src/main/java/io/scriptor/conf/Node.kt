package io.scriptor.conf

interface Node<T> {
    operator fun contains(key: String): Boolean {
        return false
    }

    operator fun <N : Node<*>> get(type: Class<N>, key: String): N {
        throw UnsupportedOperationException()
    }

    operator fun <N : Node<*>> get(type: Class<N>, index: Int): N {
        throw UnsupportedOperationException()
    }

    fun value(): T {
        throw UnsupportedOperationException()
    }
}
