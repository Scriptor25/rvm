package io.scriptor.impl

import java.util.function.Consumer
import java.util.function.Supplier

data class CSRMeta(
    val mask: ULong,
    val base: Int,
    val get: Supplier<ULong>?,
    val set: Consumer<ULong>?,
    val getHooks: MutableList<Consumer<ULong>>,
    val putHooks: MutableList<Consumer<ULong>>,
)
