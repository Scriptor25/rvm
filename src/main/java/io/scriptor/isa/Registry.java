package io.scriptor.isa;

import io.scriptor.Log;
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

    private static final Pattern OPERAND_PATTERN =
            Pattern.compile("^(\\w+)\\s*\\[(.+)](?:!(.+))?$");
    private static final Pattern SEGMENT_PATTERN =
            Pattern.compile("^\\s*(\\d+)(?::(\\d+))?(?:<<(\\d+))?\\s*$");
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("^type\\s+(\\w+)\\s+(.+)$");
    private static final Pattern INSTRUCTION_PATTERN =
            Pattern.compile("^(\\w+(?:\\.\\w+)?)\\s*\\[([^]]+)]\\s*(?:\\((\\w+)\\))?(.*)$");

    private final Map<String, TypeDefinition> types = new HashMap<>();
    private final Map<String, InstructionDefinition> instructions = new HashMap<>();

    public @NotNull InstructionDefinition get(final int value) {
        final var definitions = instructions.values()
                                            .stream()
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

    private static @NotNull TypeDefinition parseType(final @NotNull Matcher mType) {
        final var type = new TypeDefinition(mType.group(1), new HashMap<>());

        for (final var token : mType.group(2).trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }

            final var operand = parseOperand(token);
            type.operands().put(operand.label(), operand);
        }

        return type;
    }

    private static @NotNull InstructionDefinition parseInstruction(
            final @NotNull Map<String, TypeDefinition> types,
            final @NotNull Matcher mInstruction
    ) {
        final var instruction = parseInstructionEntry(mInstruction);

        if (mInstruction.group(3) != null) {
            final var type = types.get(mInstruction.group(3).trim());
            instruction.operands().putAll(type.operands());
        }

        if (mInstruction.group(4) != null) {
            for (final var token : mInstruction.group(4).trim().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }

                final var operand = parseOperand(token);
                instruction.operands().put(operand.label(), operand);
            }
        }

        return instruction;
    }

    private static @NotNull InstructionDefinition parseInstructionEntry(final @NotNull Matcher mInstruction) {
        final var value = mInstruction.group(2).trim().replaceAll("\\s+", "");

        int mask = 0, bits = 0;
        for (int i = 0; i < value.length(); ++i) {
            final var c = value.charAt(31 - i);
            if (c == '0' || c == '1') {
                mask |= (1 << i);
                if (c == '1') {
                    bits |= (1 << i);
                }
            }
        }

        return new InstructionDefinition(mInstruction.group(1), mask, bits, new HashMap<>());
    }
}
