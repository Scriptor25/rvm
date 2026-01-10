package io.scriptor.util

import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

object Resource {

    /**
     * read a system resource as stream.
     * 
     * @param name     resource name
     * @param consumer stream consumer
     */
    fun read(
        resource: Boolean,
        name: String,
        consumer: IOConsumer<InputStream>,
    ) {
        try {
            (if (resource) ClassLoader.getSystemResourceAsStream(name) else FileInputStream(name)).use { stream ->
                consumer.accept(stream)
            }
        } catch (e: IOException) {
            Log.error("failed to read resource name '%s': %s", name, e)
            throw RuntimeException(e)
        }
    }

    fun <R> read(
        resource: Boolean,
        name: String,
        function: IOFunction<InputStream, R>,
    ): R {
        try {
            return (if (resource) ClassLoader.getSystemResourceAsStream(name) else FileInputStream(name)).use { stream ->
                function.accept(stream)
            }
        } catch (e: IOException) {
            Log.error("failed to read resource name '%s': %s", name, e)
            throw RuntimeException(e)
        }
    }

    fun interface IOConsumer<T> {
        fun accept(arg: T)
    }

    fun interface IOFunction<T, R> {
        fun accept(arg: T): R
    }
}
