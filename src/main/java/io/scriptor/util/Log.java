package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public final class Log {

    private static final int LEVEL = 1;

    private static final BlockingQueue<Consumer<PrintStream>> queue = new LinkedBlockingQueue<>();
    private static final Consumer<PrintStream> SHUTDOWN = out -> {
        out.println("LOG SHUTDOWN");
    };

    public static void handle() {
        try {
            while (true) {
                final var consumer = queue.take();
                consumer.accept(System.err);
                if (consumer == SHUTDOWN) {
                    break;
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void shutdown() {
        queue.add(SHUTDOWN);
    }

    public static void info(final @NotNull String message) {
        if (LEVEL >= 2) {
            queue.add(out -> out.println("[ info ] " + message));
        }
    }

    public static void info(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        info(message.formatted(args));
    }

    public static void warn(final @NotNull String message) {
        if (LEVEL >= 1) {
            queue.add(out -> out.println("[ warning ] " + message));
        }
    }

    public static void warn(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        warn(message.formatted(args));
    }

    public static void error(final @NotNull String message) {
        if (LEVEL >= 0) {
            queue.add(out -> out.println("[ error ] " + message));
        }
    }

    public static void error(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        error(message.formatted(args));
    }

    public static void inject(final @NotNull Consumer<PrintStream> consumer) {
        queue.add(consumer);
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
