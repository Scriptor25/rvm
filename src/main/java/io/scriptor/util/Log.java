package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Log {

    private static final Queue<String> queue = new ConcurrentLinkedQueue<>();

    public static void handle() {
        while (!Thread.interrupted()) {
            while (!queue.isEmpty()) {
                System.err.println(queue.poll());
            }

            Thread.onSpinWait();
        }
    }

    public static void info(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        queue.add(("[ info ] " + message).formatted(args));
    }

    public static void warn(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        queue.add(("[ warning ] " + message).formatted(args));
    }

    public static void error(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        queue.add(("[ error ] " + message).formatted(args));
    }

    private static void prepare(final @NotNull Object[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i] instanceof Throwable throwable) {
                final var stream = new ByteArrayOutputStream();
                throwable.printStackTrace(new PrintStream(stream));
                args[i] = stream.toString();
            }
        }
    }

    private Log() {
    }
}
