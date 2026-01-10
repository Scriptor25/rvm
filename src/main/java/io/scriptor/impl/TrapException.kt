package io.scriptor.impl

import io.scriptor.util.Log.format

class TrapException(
    val id: Int,
    val trapCause: ULong,
    val trapValue: ULong,
    val trapMessage: String,
) : RuntimeException(
    format(
        "trap id=%02x, cause=%016x, value=%016x: %s",
        id,
        trapCause,
        trapValue,
        trapMessage,
    ),
) {
    constructor(
        id: Int,
        cause: ULong,
        value: ULong,
        format: String,
        vararg args: Any,
    ) : this(id, cause, value, format(format, *args))

    constructor(id: Int, original: TrapException) : this(
        id,
        original.trapCause,
        original.trapValue,
        original.trapMessage,
    )
}
