package io.scriptor.fdt

import java.util.function.Consumer
import java.util.function.Function

interface Buildable<T> {

    fun build(): T

    fun build(consumer: Consumer<T>) {
        consumer.accept(build())
    }

    fun <V> map(mapper: Function<T, V>): V {
        return mapper.apply(build())
    }
}
