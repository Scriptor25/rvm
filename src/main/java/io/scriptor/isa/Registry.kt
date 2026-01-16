package io.scriptor.isa

import io.scriptor.util.Log
import io.scriptor.util.Log.format
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Matcher
import java.util.regex.Pattern

class Registry private constructor() {

    private val types: MutableMap<String, Type> = HashMap()
    private val instructions: MutableMap<String, Instruction> = HashMap()

    fun parse(stream: InputStream) {
        val reader = BufferedReader(InputStreamReader(stream))
        reader
            .lines()
            .map { it.trim { it <= ' ' } }
            .filter { !it.isEmpty() }
            .filter { !it.startsWith("#") }
            .forEach(::parse)
    }

    private fun parse(line: String) {
        val typeMatcher: Matcher = TYPE_PATTERN.matcher(line)
        if (typeMatcher.matches()) {
            val type: Type = parseType(typeMatcher)
            types[type.label] = type
            return
        }

        val instructionMatcher: Matcher = INSTRUCTION_PATTERN.matcher(line)
        if (instructionMatcher.matches()) {
            val instruction: Instruction = parseInstruction(types, instructionMatcher)
            instructions[instruction.mnemonic] = instruction
            return
        }

        Log.warn("unhandled line pattern '%s'", line)
    }

    companion object {
        val instance: Registry = Registry()

        fun contains(mode: UInt, value: UInt): Boolean = contains(mode, value.toInt())

        fun contains(mode: UInt, value: Int): Boolean {
            for (instruction in instance.instructions.values) {
                if (instruction.test(mode, value)) {
                    return true
                }
            }
            return false
        }

        operator fun get(mode: UInt, value: UInt): Instruction = get(mode, value.toInt())

        operator fun get(mode: UInt, value: Int): Instruction {
            var candidate: Instruction? = null

            for (instruction in instance.instructions.values) {
                if (instruction.test(mode, value)) {
                    check(candidate == null) {
                        format("ambiguous candidates for instruction %08x: %s and %s", value, candidate, instruction)
                    }
                    candidate = instruction
                }
            }

            return checkNotNull(candidate) { format("no candidate for instruction %08x", value) }
        }

        operator fun get(mnemonic: String): Instruction? = instance.instructions[mnemonic]

        private val OPERAND_PATTERN = Pattern.compile("^(\\w+)\\s*\\[(.+)](?:!(.+))?$")
        private val SEGMENT_PATTERN = Pattern.compile("^\\s*(\\d+)(?::(\\d+))?(?:<<(\\d+))?\\s*$")
        private val TYPE_PATTERN = Pattern.compile("^type\\s+(\\w+)\\s+(.+)$")
        private val INSTRUCTION_PATTERN =
            Pattern.compile("^(\\w+(?:\\.\\w+)*)\\s*\\[([^]]+)](?:\\?(\\d+))?\\s*(?:\\((\\w+)\\))?(.*)$")

        private fun parseOperand(token: String): Operand {
            val operandMatcher = OPERAND_PATTERN.matcher(token)
            require(operandMatcher.matches()) { format("invalid operand token '%s'", token) }

            val strings = operandMatcher.group(2)
                .split("\\|".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val segments: MutableList<Segment> = ArrayList()
            for (segment in strings) {
                val segmentMatcher = SEGMENT_PATTERN.matcher(segment)
                require(segmentMatcher.matches())

                val hi = segmentMatcher.group(1).toInt()
                val lo = if (segmentMatcher.group(2) != null) segmentMatcher.group(2).toInt() else hi
                val shift = if (segmentMatcher.group(3) != null) segmentMatcher.group(3).toInt() else 0

                segments.add(Segment(hi, lo, shift))
            }

            val operand = Operand(
                operandMatcher.group(1),
                segments,
                HashSet(),
            )

            if (operandMatcher.group(3) != null) {
                val values = operandMatcher.group(3)
                    .trim { it <= ' ' }
                    .split(",".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                for (value in values) {
                    if (value.isBlank()) {
                        continue
                    }
                    operand.exclude.add(value.trim { it <= ' ' }.toInt(0x10))
                }
            }

            return operand
        }

        private fun parseType(matcher: Matcher): Type {
            val operands: MutableMap<String, Operand> = HashMap()

            val segments = matcher.group(2)
                .trim { it <= ' ' }
                .split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()

            for (token in segments) {
                if (token.isBlank()) {
                    continue
                }
                val operand = parseOperand(token)
                operands[operand.label] = operand
            }

            return Type(matcher.group(1), operands)
        }

        private fun parseInstruction(
            types: MutableMap<String, Type>,
            matcher: Matcher,
        ): Instruction {
            val value = matcher.group(2)
                .trim { it <= ' ' }
                .replace("\\s+".toRegex(), "")

            val ilen = (value.length.toUInt() + 7U) shr 3

            var mask = 0
            var bits = 0
            for (i in 0..<value.length) {
                val c = value[(value.length - 1) - i]
                if (c == '0' || c == '1') {
                    mask = mask or (1 shl i)
                    if (c == '1') {
                        bits = bits or (1 shl i)
                    }
                }
            }

            val restriction = if (matcher.group(3) != null) matcher.group(3).toUInt() else 0U
            val operands: MutableMap<String, Operand> = HashMap()

            if (matcher.group(4) != null) {
                val typename = matcher.group(4).trim { it <= ' ' }
                require(typename in types) { format("invalid typename '%s'", typename) }

                val type = types[typename]!!
                operands.putAll(type.operands)
            }

            if (matcher.group(5) != null) {
                val segments = matcher.group(5)
                    .trim { it <= ' ' }
                    .split("\\s+".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                for (token in segments) {
                    if (token.isBlank()) {
                        continue
                    }
                    val operand = parseOperand(token)
                    operands[operand.label] = operand
                }
            }

            return Instruction(matcher.group(1), ilen, mask, bits, restriction, operands)
        }
    }
}
