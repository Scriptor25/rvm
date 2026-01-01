package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public final class Resource {

    @FunctionalInterface
    public interface IOConsumer<T> {

        void accept(final T arg) throws IOException;
    }

    @FunctionalInterface
    public interface IOFunction<T, R> {

        R accept(final T arg) throws IOException;
    }

    /**
     * read a system resource as stream.
     *
     * @param name     resource name
     * @param consumer stream consumer
     */
    public static void read(
            final boolean resource,
            final @NotNull String name,
            final @NotNull IOConsumer<InputStream> consumer
    ) {
        try (final var stream = resource ? ClassLoader.getSystemResourceAsStream(name) : new FileInputStream(name)) {
            if (stream == null)
                throw new FileNotFoundException("resource name '%s'".formatted(name));

            consumer.accept(stream);
        } catch (final IOException e) {
            Log.error("failed to read resource name '%s': %s", name, e);
            throw new RuntimeException(e);
        }
    }

    public static <R> R read(
            final boolean resource,
            final @NotNull String name,
            final @NotNull IOFunction<InputStream, R> function
    ) {
        try (final var stream = resource ? ClassLoader.getSystemResourceAsStream(name) : new FileInputStream(name)) {
            if (stream == null)
                throw new FileNotFoundException("resource name '%s'".formatted(name));

            return function.accept(stream);
        } catch (final IOException e) {
            Log.error("failed to read resource name '%s': %s", name, e);
            throw new RuntimeException(e);
        }
    }

    private Resource() {
    }
}
