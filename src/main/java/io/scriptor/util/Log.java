package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public final class Log {

    public static void info(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        System.err.printf("[ info ] %s%n", message.formatted(args));
    }

    public static void warn(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        System.err.printf("[ warning ] %s%n", message.formatted(args));
    }

    public static void error(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        System.err.printf("[ error ] %s%n", message.formatted(args));
    }

    public static void direct(final @NotNull String message, final @NotNull Object... args) {
        prepare(args);
        System.err.printf("%s%n", message.formatted(args));
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
