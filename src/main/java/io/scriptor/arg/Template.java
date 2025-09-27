package io.scriptor.arg;

import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.scriptor.util.Unit.*;

public record Template<T extends Payload>(
        int id,
        @NotNull Pattern pattern,
        @NotNull Predicate<Matcher> next,
        @NotNull Function<Matcher, T> pipe
) {

    public static final int TEMPLATE_LOAD = 1;
    public static final int TEMPLATE_MEMORY = 2;
    public static final int TEMPLATE_REGISTER = 3;

    public static final List<Template<?>> TEMPLATES = List.of(
            new Template<>(TEMPLATE_LOAD,
                           Pattern.compile("^(?:--load|-l)(=|\\s+)?(?:([^:\\s]+)(?::([0-9a-fA-F]+))?)?$"),
                           matcher -> matcher.group(1) == null,
                           matcher -> {
                               final var filename = matcher.group(2);
                               final var base     = matcher.group(3);

                               final long offset;
                               if (base == null) {
                                   offset = 0L;
                               } else {
                                   offset = Long.parseUnsignedLong(base, 0x10);
                               }

                               return new LoadPayload(filename, offset);
                           }),
            new Template<>(TEMPLATE_MEMORY,
                           Pattern.compile("^(?:--memory|-m)(=|\\s+)?(?:(\\d+)([KMG]))?$"),
                           matcher -> matcher.group(1) == null,
                           matcher -> {
                               final var value = Long.parseUnsignedLong(matcher.group(2), 10);
                               final var unit  = matcher.group(3);
                               final var size = switch (unit) {
                                   case "K" -> KiB(value);
                                   case "M" -> MiB(value);
                                   case "G" -> GiB(value);
                                   default -> throw new IllegalArgumentException("%d:%s".formatted(value, unit));
                               };

                               return new MemoryPayload(size);
                           }),
            new Template<>(TEMPLATE_REGISTER,
                           Pattern.compile("^(?:--register|-r)(=|\\s+)?(?:(\\d+):([0-9a-fA-F]+))?$"),
                           matcher -> matcher.group(1) == null,
                           matcher -> {
                               final var register = Integer.parseUnsignedInt(matcher.group(2), 10);
                               final var value    = Long.parseUnsignedLong(matcher.group(3), 0x10);

                               return new RegisterPayload(register, value);
                           })
    );

    public static PayloadMap parse(final @NotNull String @NotNull [] args) {
        final Map<Integer, List<Payload>> values = new HashMap<>();

        for (int i = 0; i < args.length; ++i) {
            for (final var template : TEMPLATES) {
                var matcher = template.pattern().matcher(args[i]);
                if (!matcher.matches())
                    continue;

                var increment = false;
                if (template.next().test(matcher)) {
                    increment = true;
                    matcher = template.pattern().matcher("%s %s".formatted(args[i], args[i + 1]));
                }

                if (!matcher.matches()) {
                    Log.warn("arg '%s %s' does not match pattern '%s'", args[i], args[i + 1], template.pattern());
                    continue;
                }

                if (increment)
                    ++i;

                final var value = template.pipe().apply(matcher);

                values.computeIfAbsent(template.id(), _ -> new ArrayList<>()).add(value);
                break;
            }
        }

        return new PayloadMap(values);
    }
}
