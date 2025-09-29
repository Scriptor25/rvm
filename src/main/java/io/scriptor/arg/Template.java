package io.scriptor.arg;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.scriptor.util.Unit.*;

public record Template<T extends Payload>(
        int id,
        @NotNull Pattern name,
        @Nullable Function<Map<String, String>, T> pipe
) {

    public static final int TEMPLATE_LOAD = 1;
    public static final int TEMPLATE_MEMORY = 2;
    public static final int TEMPLATE_REGISTER = 3;
    public static final int TEMPLATE_DEBUG = 4;

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
            new Template<>(TEMPLATE_REGISTER,
                           Pattern.compile("^--register|-r$"),
                           arg -> {
                               final var name  = arg.get("name");
                               final var value = arg.get("value");

                               final int register = switch (name) {
                                   case "x0", "zero" -> 0;
                                   case "x1", "ra" -> 1;
                                   case "x2", "sp" -> 2;
                                   case "x3", "gp" -> 3;
                                   case "x4", "tp" -> 4;
                                   case "x5", "t0" -> 5;
                                   case "x6", "t1" -> 6;
                                   case "x7", "t2" -> 7;
                                   case "x8", "s0", "fp" -> 8;
                                   case "x9", "s1" -> 9;
                                   case "x10", "a0" -> 10;
                                   case "x11", "a1" -> 11;
                                   case "x12", "a2" -> 12;
                                   case "x13", "a3" -> 13;
                                   case "x14", "a4" -> 14;
                                   case "x15", "a5" -> 15;
                                   case "x16", "a6" -> 16;
                                   case "x17", "a7" -> 17;
                                   case "x18", "s2" -> 18;
                                   case "x19", "s3" -> 19;
                                   case "x20", "s4" -> 20;
                                   case "x21", "s5" -> 21;
                                   case "x22", "s6" -> 22;
                                   case "x23", "s7" -> 23;
                                   case "x24", "s8" -> 24;
                                   case "x25", "s9" -> 25;
                                   case "x26", "s10" -> 26;
                                   case "x27", "s11" -> 27;
                                   case "x28", "t3" -> 28;
                                   case "x29", "t4" -> 29;
                                   case "x30", "t5" -> 30;
                                   case "x31", "t6" -> 31;
                                   default -> -1;
                               };

                               return new RegisterPayload(register, Long.parseUnsignedLong(value, 0x10));
                           }),
            new Template<>(TEMPLATE_DEBUG,
                           Pattern.compile("^--debug|-d$"),
                           null)
    );

    public static PayloadMap parse(final @NotNull String @NotNull [] args) {
        final Map<Integer, List<Payload>> values = new HashMap<>();

        for (int i = 0; i < args.length; ++i) {
            for (final var template : TEMPLATES) {
                final var nameMatcher = template.name().matcher(args[i]);
                if (!nameMatcher.matches())
                    continue;

                if (template.pipe() == null) {
                    values.computeIfAbsent(template.id(), _ -> new ArrayList<>()).add(new FlagPayload(true));
                    continue;
                }

                final var attributes = args[++i].split(",");

                final Map<String, String> map = new HashMap<>();
                for (final var attribute : attributes) {
                    if (attribute.contains("=")) {
                        final var entry = attribute.split("=");
                        map.put(entry[0], entry[1]);
                        continue;
                    }
                    map.put(attribute, "");
                }

                final var value = template.pipe().apply(map);

                values.computeIfAbsent(template.id(), _ -> new ArrayList<>()).add(value);
                break;
            }
        }

        return new PayloadMap(values);
    }
}
