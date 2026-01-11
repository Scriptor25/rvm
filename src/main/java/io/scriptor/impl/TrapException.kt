package io.scriptor.impl

import io.scriptor.util.Log.format

class TrapException : RuntimeException {
    val id: Int
    val trapCause: ULong
    val trapValue: ULong
    val trapMessage: String

    constructor(
        id: Int,
        cause: ULong,
        value: ULong,
        message: String,
    ) : super(
        format(
            "trap id=%02x, cause=%016x, value=%016x: %s",
            id,
            cause,
            value,
            message,
        ),
    ) {
        this.id = id
        this.trapCause = cause
        this.trapValue = value
        this.trapMessage = message
    }

    constructor(
        id: Int,
        cause: ULong,
        value: ULong,
        format: String,
        vararg args: Any?,
    ) : this(id, cause, value, format(format, *args))

    constructor(
        id: Int,
        original: TrapException,
    ) : this(
        id,
        original.trapCause,
        original.trapValue,
        original.trapMessage,
    )
}
