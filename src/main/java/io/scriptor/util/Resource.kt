package io.scriptor.util

import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.function.Consumer
import java.util.function.Function

object Resource {

    fun read(
        isResource: Boolean,
        filename: String,
        consumer: Consumer<InputStream>,
    ) {
        try {
            val stream = if (isResource) {
                ClassLoader.getSystemResourceAsStream(filename)
            } else {
                FileInputStream(filename)
            }
            consumer.accept(stream)
        } catch (e: IOException) {
            Log.error("failed to read resource name '%s': %s", filename, e)
            throw RuntimeException(e)
        }
    }

    fun <R> read(
        isResource: Boolean,
        filename: String,
        function: Function<InputStream, R>,
    ): R {
        try {
            val stream = if (isResource) {
                ClassLoader.getSystemResourceAsStream(filename)
            } else {
                FileInputStream(filename)
            }
            return function.apply(stream)
        } catch (e: IOException) {
            Log.error("failed to read resource name '%s': %s", filename, e)
            throw RuntimeException(e)
        }
    }
}
