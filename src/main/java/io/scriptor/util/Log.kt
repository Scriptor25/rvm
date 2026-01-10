package io.scriptor.util

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer

object Log {

    private const val LEVEL = 1

    private val queue: BlockingQueue<Consumer<PrintStream>> = LinkedBlockingQueue<Consumer<PrintStream>>()
    private val SHUTDOWN = Consumer<PrintStream> { it.println("LOG SHUTDOWN") }

    fun handle() {
        try {
            while (true) {
                val consumer = queue.take()
                consumer.accept(System.err)
                if (consumer === SHUTDOWN) {
                    break
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun shutdown() {
        queue.add(SHUTDOWN)
    }

    fun info(message: String) {
        if (LEVEL >= 2) {
            queue.add { it.println("[ info ] $message") }
        }
    }

    fun info(format: String, vararg args: Any?) {
        if (LEVEL >= 2) {
            info(format(format, *args))
        }
    }

    fun warn(message: String) {
        if (LEVEL >= 1) {
            queue.add { it.println("[ warning ] $message") }
        }
    }

    fun warn(format: String, vararg args: Any?) {
        if (LEVEL >= 1) {
            warn(format(format, *args))
        }
    }

    fun error(message: String) {
        if (LEVEL >= 0) {
            queue.add { it.println("[ error ] $message") }
        }
    }

    fun error(format: String, vararg args: Any?) {
        if (LEVEL >= 0) {
            error(format(format, *args))
        }
    }

    fun inject(consumer: Consumer<PrintStream>) {
        queue.add(consumer)
    }

    fun format(format: String, vararg args: Any?): String {
        val args = arrayOf(*args)
        prepare(args)
        return format.format(*args)
    }

    private fun prepare(args: Array<Any?>) {
        for (i in args.indices) {
            args[i] = when (val arg = args[i]) {
                is Throwable -> {
                    val stream = ByteArrayOutputStream()
                    arg.printStackTrace(PrintStream(stream))
                    stream.toString()
                }

                is UByte -> arg.toByte()
                is UShort -> arg.toShort()
                is UInt -> arg.toInt()
                is ULong -> arg.toLong()
                else -> arg
            }
        }
    }
}
