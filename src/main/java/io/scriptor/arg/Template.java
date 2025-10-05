package io.scriptor.arg;

import com.carrotsearch.hppc.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.scriptor.util.Unit.*;

public record Template<T extends Payload>(
        int id,
        @NotNull Pattern name,
        @Nullable Function<ObjectObjectMap<String, String>, T> pipe
) {

    public static final int TEMPLATE_LOAD = 1;
    public static final int TEMPLATE_MEMORY = 2;
    public static final int TEMPLATE_DEBUG = 3;

    public static final List<Template<?>> TEMPLATES = List.of(
            new Template<>(TEMPLATE_LOAD,
                           Pattern.compile("^--load|-l$"),
                           arg -> {
                               final var filename = arg.get("filename");
                               final var offset   = arg.get("offset");

                               return new LoadPayload(filename,
                                                      offset != null ? Long.parseUnsignedLong(offset, 0x10) : 0);
                           }),
            new Template<>(TEMPLATE_MEMORY,
                           Pattern.compile("^--memory|-m$"),
                           arg -> {
                               final var size = arg.get("size");
                               final var unit = arg.get("unit");

                               final var n = Integer.parseUnsignedInt(size, 10);
                               final var value = switch (unit) {
                                   case "K" -> KiB(n);
                                   case "M" -> MiB(n);
                                   case "G" -> GiB(n);
                                   default -> n;
                               };

                               return new MemoryPayload(value);
                           }),
            new Template<>(TEMPLATE_DEBUG,
                           Pattern.compile("^--debug|-d$"),
                           null)
    );

    public static PayloadMap parse(final @NotNull String @NotNull [] args) {
        final IntObjectMap<ObjectIndexedContainer<Payload>> values = new IntObjectHashMap<>();

        for (int i = 0; i < args.length; ++i) {
            for (final var template : TEMPLATES) {
                final var nameMatcher = template.name().matcher(args[i]);
                if (!nameMatcher.matches())
                    continue;

                if (template.pipe() == null) {

                    final ObjectIndexedContainer<Payload> list;
                    if (values.containsKey(template.id())) {
                        list = values.get(template.id());
                    } else {
                        list = new ObjectArrayList<>();
                        values.put(template.id(), list);
                    }

                    list.add(new FlagPayload(true));
                    continue;
                }

                final var attributes = args[++i].split(",");

                final ObjectObjectMap<String, String> map = new ObjectObjectHashMap<>();
                for (final var attribute : attributes) {
                    if (attribute.contains("=")) {
                        final var entry = attribute.split("=");
                        map.put(entry[0], entry[1]);
                        continue;
                    }
                    map.put(attribute, "");
                }

                final var value = template.pipe().apply(map);

                final ObjectIndexedContainer<Payload> list;
                if (values.containsKey(template.id())) {
                    list = values.get(template.id());
                } else {
                    list = new ObjectArrayList<>();
                    values.put(template.id(), list);
                }

                list.add(value);
                break;
            }
        }

        return new PayloadMap(values);
    }
}
