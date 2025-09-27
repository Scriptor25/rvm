package io.scriptor.isa;

import io.scriptor.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.function.Predicate.not;

public final class Registry {

    private static final Registry instance = new Registry();

    public static @NotNull Registry getInstance() {
        return instance;
    }

    public static boolean has(final int mode, final int value) {
        return instance.instructions.values()
                                    .stream()
                                    .filter(instruction -> instruction.restriction() == 0 || instruction.restriction() == mode)
                                    .anyMatch(instruction -> instruction.test(value));
    }

    public static @NotNull Instruction get(final int mode, final int value) {
        final var definitions = instance.instructions.values()
                                                     .stream()
                                                     .filter(instruction -> instruction.restriction() == 0 || instruction.restriction() == mode)
                                                     .filter(instruction -> instruction.test(value))
                                                     .toList();

        if (definitions.isEmpty()) {
            throw new IllegalStateException("no definition for instruction %08x"
                                                    .formatted(value));
        }
        if (definitions.size() > 1) {
            throw new IllegalStateException("ambiguous definitions for instruction %08x: %s"
                                                    .formatted(value, definitions));
        }

        return definitions.getFirst();
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
    private final Map<String, Instruction> instructions = new HashMap<>();

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
            instructions.put(instruction.mnemonic(), instruction);
            return;
        }

        Log.warn("unhandled line pattern '%s'", line);
    }

    private static @NotNull Operand parseOperand(final @NotNull String token) {
        final var mOperand = OPERAND_PATTERN.matcher(token);
        if (!mOperand.matches()) {
            throw new IllegalArgumentException("invalid operand token '%s'".formatted(token));
        }

        final var operand = new Operand(mOperand.group(1), new ArrayList<>(), new HashSet<>());

        final var segments = mOperand.group(2).split("\\|");
        for (final var segment : segments) {
            final var mSegment = SEGMENT_PATTERN.matcher(segment);
            if (!mSegment.matches()) {
                throw new IllegalArgumentException();
            }

            final var hi    = Integer.parseUnsignedInt(mSegment.group(1));
            final var lo    = mSegment.group(2) != null ? Integer.parseUnsignedInt(mSegment.group(2)) : hi;
            final var shift = mSegment.group(3) != null ? Integer.parseUnsignedInt(mSegment.group(3)) : 0;

            operand.segments().add(new Segment(hi, lo, shift));
        }

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
        final var type = new Type(mType.group(1), new HashMap<>());

        for (final var token : mType.group(2).trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }

            final var operand = parseOperand(token);
            type.operands().put(operand.label(), operand);
        }

        return type;
    }

    private static @NotNull Instruction parseInstruction(
            final @NotNull Map<String, Type> types,
            final @NotNull Matcher mInstruction
    ) {
        final var instruction = parseInstructionEntry(mInstruction);

        if (mInstruction.group(4) != null) {
            final var typename = mInstruction.group(4).trim();
            if (!types.containsKey(typename))
                throw new IllegalArgumentException("invalid typename '%s'".formatted(typename));
            final var type = types.get(typename);
            instruction.operands().putAll(type.operands());
        }

        if (mInstruction.group(5) != null) {
            for (final var token : mInstruction.group(5).trim().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }

                final var operand = parseOperand(token);
                instruction.operands().put(operand.label(), operand);
            }
        }

        return instruction;
    }

    private static @NotNull Instruction parseInstructionEntry(final @NotNull Matcher mInstruction) {
        final var value = mInstruction.group(2).trim().replaceAll("\\s+", "");

        final var ilen = (value.length() + 7) / 8;

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

        return new Instruction(mInstruction.group(1), ilen, mask, bits, restriction, new HashMap<>());
    }
}
