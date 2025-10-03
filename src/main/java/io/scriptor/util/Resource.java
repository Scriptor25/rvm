package io.scriptor.util;

import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public final class Resource {

    @FunctionalInterface
    public interface IOConsumer<T> {

        void accept(final @NotNull T t) throws IOException;
    }

    /**
     * read a system resource as stream.
     *
     * @param name     resource name
     * @param consumer stream consumer
     */
    public static void read(final @NotNull String name, final @NotNull IOConsumer<InputStream> consumer) {
        try (final var stream = ClassLoader.getSystemResourceAsStream(name)) {
            if (stream == null)
                throw new FileNotFoundException("resource name '%s'".formatted(name));

            consumer.accept(stream);
        } catch (final IOException e) {
            Log.error("failed to read resource name '%s': %s", name, e);
            throw new RuntimeException(e);
        }
    }

    private Resource() {
    }
}
