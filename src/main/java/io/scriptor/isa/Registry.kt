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
    private val instructions: MutableList<Instruction> = ArrayList()

    fun parse(stream: InputStream) {
        val reader = BufferedReader(InputStreamReader(stream))
        reader
            .lines()
            .map<String> { it.trim { it <= ' ' } }
            .filter { !it.isEmpty() }
            .filter { !it.startsWith("#") }
            .forEach { parse(it) }
    }

    private fun parse(line: String) {
        val mType: Matcher = TYPE_PATTERN.matcher(line)
        if (mType.matches()) {
            val type: Type = parseType(mType)
            types[type.label] = type
            return
        }

        val mInstruction: Matcher = INSTRUCTION_PATTERN.matcher(line)
        if (mInstruction.matches()) {
            val instruction: Instruction = parseInstruction(types, mInstruction)
            instructions.add(instruction)
            return
        }

        Log.warn("unhandled line pattern '%s'", line)
    }

    companion object {
        val instance: Registry = Registry()

        fun contains(mode: UInt, value: UInt): Boolean = contains(mode, value.toInt())

        fun contains(mode: UInt, value: Int): Boolean {
            for (instruction in instance.instructions) {
                if ((instruction.restriction == 0U || instruction.restriction == mode) && instruction.test(value)) {
                    return true
                }
            }
            return false
        }

        operator fun get(mode: UInt, value: UInt): Instruction = get(mode, value.toInt())

        operator fun get(mode: UInt, value: Int): Instruction {
            var candidate: Instruction? = null

            for (instruction in instance.instructions) {
                if ((instruction.restriction == 0U || instruction.restriction == mode) && instruction.test(value)) {
                    check(candidate == null) {
                        format("ambiguous candidates for instruction %08x: %s and %s", value, candidate, instruction)
                    }
                    candidate = instruction
                }
            }

            checkNotNull(candidate) { format("no candidate for instruction %08x", value) }
            return candidate
        }

        private val OPERAND_PATTERN = Pattern.compile("^(\\w+)\\s*\\[(.+)](?:!(.+))?$")
        private val SEGMENT_PATTERN = Pattern.compile("^\\s*(\\d+)(?::(\\d+))?(?:<<(\\d+))?\\s*$")
        private val TYPE_PATTERN = Pattern.compile("^type\\s+(\\w+)\\s+(.+)$")
        private val INSTRUCTION_PATTERN =
            Pattern.compile("^(\\w+(?:\\.\\w+)*)\\s*\\[([^]]+)](?:\\?(\\d+))?\\s*(?:\\((\\w+)\\))?(.*)$")

        private fun parseOperand(token: String): Operand {
            val mOperand: Matcher = OPERAND_PATTERN.matcher(token)
            require(mOperand.matches()) { format("invalid operand token '%s'", token) }

            val strings = mOperand.group(2)
                .split("\\|".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val segments: MutableList<Segment> = ArrayList()
            for (segment in strings) {
                val mSegment: Matcher = SEGMENT_PATTERN.matcher(segment)
                require(mSegment.matches())

                val hi = mSegment.group(1).toInt()
                val lo = if (mSegment.group(2) != null) mSegment.group(2).toInt() else hi
                val shift = if (mSegment.group(3) != null) mSegment.group(3).toInt() else 0

                segments.add(Segment(hi, lo, shift))
            }

            val operand = Operand(
                mOperand.group(1),
                segments.toTypedArray(),
                HashSet(),
            )

            if (mOperand.group(3) != null) {
                val values = mOperand.group(3)
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

        private fun parseType(mType: Matcher): Type {
            val operands: MutableList<Operand> = ArrayList()

            val segments = mType.group(2)
                .trim { it <= ' ' }
                .split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()

            for (token in segments) {
                if (token.isBlank()) {
                    continue
                }
                operands.add(parseOperand(token))
            }

            return Type(mType.group(1), operands.toTypedArray())
        }

        private fun parseInstruction(
            types: MutableMap<String, Type>,
            mInstruction: Matcher,
        ): Instruction {
            val value = mInstruction.group(2)
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

            val restriction = if (mInstruction.group(3) != null) mInstruction.group(3).toUInt() else 0U
            val operands: MutableList<Operand> = ArrayList()

            if (mInstruction.group(4) != null) {
                val typename = mInstruction.group(4).trim { it <= ' ' }
                require(types.containsKey(typename)) { format("invalid typename '%s'", typename) }

                val type = types[typename]!!
                operands.addAll(type.operands)
            }

            if (mInstruction.group(5) != null) {
                val segments = mInstruction.group(5)
                    .trim { it <= ' ' }
                    .split("\\s+".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                for (token in segments) {
                    if (token.isBlank()) {
                        continue
                    }
                    operands.add(parseOperand(token))
                }
            }

            return Instruction(mInstruction.group(1), ilen, mask, bits, restriction, operands.toTypedArray())
        }
    }
}
