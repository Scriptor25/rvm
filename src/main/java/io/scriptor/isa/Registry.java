package io.scriptor.isa;

import com.carrotsearch.hppc.IntHashSet;
import io.scriptor.util.DirectList;
import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.function.Predicate.not;

public final class Registry {

    private static final Registry instance = new Registry();

    public static @NotNull Registry getInstance() {
        return instance;
    }

    public static boolean has(final int mode, final int value) {
        final var size = instance.instructions.size();
        for (int i = 0; i < size; ++i) {
            final var instruction = instance.instructions.get(i);
            if ((instruction.restriction() == 0 || instruction.restriction() == mode) && instruction.test(value)) {
                return true;
            }
        }
        return false;
    }

    public static @NotNull Instruction get(final int mode, final int value) {

        Instruction candidate = null;

        final var size = instance.instructions.size();
        for (int i = 0; i < size; ++i) {
            final var instruction = instance.instructions.get(i);
            if ((instruction.restriction() != 0 && instruction.restriction() != mode) || !instruction.test(value)) {
                continue;
            }
            if (candidate != null) {
                throw new IllegalStateException("ambiguous candidates for instruction %08x: %s and %s"
                                                        .formatted(value, candidate, instruction));
            }
            candidate = instruction;
        }

        if (candidate == null) {
            throw new IllegalStateException("no candidate for instruction %08x".formatted(value));
        }

        return candidate;
    }

    private static final Pattern OPERAND_PATTERN =
            Pattern.compile("^(\\w+)\\s*\\[(.+)](?:!(.+))?$");
    private static final Pattern SEGMENT_PATTERN =
            Pattern.compile("^\\s*(\\d+)(?::(\\d+))?(?:<<(\\d+))?\\s*$");
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("^type\\s+(\\w+)\\s+(.+)$");
    private static final Pattern INSTRUCTION_PATTERN =
            Pattern.compile("^(\\w+(?:\\.\\w+)*)\\s*\\[([^]]+)](?:\\?(\\d+))?\\s*(?:\\((\\w+)\\))?(.*)$");

    private final Map<String, Type> types = new HashMap<>();
    private final DirectList<Instruction> instructions = new DirectList<>();

    private Registry() {
    }

    public void parse(final @NotNull InputStream stream) {
        final var reader = new BufferedReader(new InputStreamReader(stream));
        reader.lines()
              .map(String::trim)
              .filter(not(String::isEmpty))
              .filter(not(line -> line.startsWith("#")))
              .forEach(this::parse);
    }

    private void parse(final @NotNull String line) {

        final var mType = TYPE_PATTERN.matcher(line);
        if (mType.matches()) {
            final var type = parseType(mType);
            types.put(type.label(), type);
            return;
        }

        final var mInstruction = INSTRUCTION_PATTERN.matcher(line);
        if (mInstruction.matches()) {
            final var instruction = parseInstruction(types, mInstruction);
            instructions.add(instruction);
            return;
        }

        Log.warn("unhandled line pattern '%s'", line);
    }

    private static @NotNull Operand parseOperand(final @NotNull String token) {
        final var mOperand = OPERAND_PATTERN.matcher(token);
        if (!mOperand.matches()) {
            throw new IllegalArgumentException("invalid operand token '%s'".formatted(token));
        }

        final var           strings  = mOperand.group(2).split("\\|");
        final List<Segment> segments = new ArrayList<>();
        for (final var segment : strings) {
            final var mSegment = SEGMENT_PATTERN.matcher(segment);
            if (!mSegment.matches()) {
                throw new IllegalArgumentException();
            }

            final var hi    = Integer.parseUnsignedInt(mSegment.group(1));
            final var lo    = mSegment.group(2) != null ? Integer.parseUnsignedInt(mSegment.group(2)) : hi;
            final var shift = mSegment.group(3) != null ? Integer.parseUnsignedInt(mSegment.group(3)) : 0;

            segments.add(new Segment(hi, lo, shift));
        }

        final var operand = new Operand(mOperand.group(1), segments.toArray(Segment[]::new), new IntHashSet());

        if (mOperand.group(3) != null) {
            final var values = mOperand.group(3).trim().split(",");
            for (final var value : values) {
                if (value.isBlank()) {
                    continue;
                }
                operand.exclude().add(Integer.parseUnsignedInt(value.trim(), 0x10));
            }
        }

        return operand;
    }

    private static @NotNull Type parseType(final @NotNull Matcher mType) {
        final List<Operand> operands = new ArrayList<>();

        for (final var token : mType.group(2).trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            operands.add(parseOperand(token));
        }

        return new Type(mType.group(1), operands.toArray(Operand[]::new));
    }

    private static @NotNull Instruction parseInstruction(
            final @NotNull Map<String, Type> types,
            final @NotNull Matcher mInstruction
    ) {
        final var value = mInstruction.group(2).trim().replaceAll("\\s+", "");

        final var ilen = (value.length() + 0b111) >> 3;

        int mask = 0, bits = 0;
        for (int i = 0; i < value.length(); ++i) {
            final var c = value.charAt((value.length() - 1) - i);
            if (c == '0' || c == '1') {
                mask |= (1 << i);
                if (c == '1') {
                    bits |= (1 << i);
                }
            }
        }

        final int restriction;
        if (mInstruction.group(3) != null) {
            restriction = Integer.parseUnsignedInt(mInstruction.group(3));
        } else {
            restriction = 0;
        }

        final List<Operand> operands = new ArrayList<>();

        if (mInstruction.group(4) != null) {
            final var typename = mInstruction.group(4).trim();
            if (!types.containsKey(typename))
                throw new IllegalArgumentException("invalid typename '%s'".formatted(typename));

            final var type = types.get(typename);
            operands.addAll(Arrays.asList(type.operands()));
        }

        if (mInstruction.group(5) != null) {
            for (final var token : mInstruction.group(5).trim().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                operands.add(parseOperand(token));
            }
        }

        return new Instruction(mInstruction.group(1), ilen, mask, bits, restriction, operands.toArray(Operand[]::new));
    }
}
