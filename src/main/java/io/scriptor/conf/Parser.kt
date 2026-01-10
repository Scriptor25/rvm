package io.scriptor.conf

import io.scriptor.util.Log.format
import java.io.InputStream

class Parser(private val stream: InputStream) {
    private var buffer: Int
    private var token: Token

    init {
        this.buffer = stream.read()
        this.token = next()
    }

    fun parse(): Node<*> {
        if (at(TokenType.OTHER, "{")) return parseObject()
        if (at(TokenType.OTHER, "[")) return parseArray()

        if (at(TokenType.STRING, TokenType.SYMBOL)) return StringNode(skip().string)
        if (at(TokenType.INTEGER)) return IntegerNode(skip().integer)
        if (at(TokenType.FLOATING)) return FloatingNode(skip().floating)

        if (at("true", "false")) return BooleanNode(skip().string.toBoolean())

        throw NoSuchElementException()
    }

    fun parseObject(): ObjectNode {
        val node = ObjectNode()

        expect(TokenType.OTHER, "{")
        while (!at(TokenType.OTHER, "}")) {
            val key = expect(TokenType.SYMBOL, TokenType.STRING).string
            expect(TokenType.OTHER, ":")
            val value = parse()

            node[key] = value

            if (!at(TokenType.OTHER, "}")) expect(TokenType.OTHER, ",")
        }
        expect(TokenType.OTHER, "}")

        return node
    }

    fun parseArray(): ArrayNode {
        val node = ArrayNode()

        expect(TokenType.OTHER, "[")
        while (!at(TokenType.OTHER, "]")) {
            val value = parse()

            node.add(value)

            if (!at(TokenType.OTHER, "]")) expect(TokenType.OTHER, ",")
        }
        expect(TokenType.OTHER, "]")

        return node
    }

    private fun at(type: TokenType, value: String): Boolean {
        return token.type == type && token.string == value
    }

    private fun at(vararg types: TokenType): Boolean {
        for (type in types) if (token.type == type) return true
        return false
    }

    private fun at(vararg values: String): Boolean {
        for (value in values) if (token.string == value) return true
        return false
    }

    private fun expect(type: TokenType, value: String) {
        if (token.type == type && token.string == value) {
            token = next()
            return
        }
        throw NoSuchElementException(format("type=%s, value=%s", type, value))
    }

    private fun expect(vararg types: TokenType): Token {
        for (type in types) if (token.type == type) return skip()
        throw NoSuchElementException(format("types=%s", types.contentToString()))
    }

    private fun expect(vararg values: String): Token {
        for (value in values) if (token.string == value) return skip()
        throw NoSuchElementException(format("values=%s", values.contentToString()))
    }

    private fun skip(): Token {
        val x = token
        token = next()
        return x
    }

    private fun read() {
        buffer = stream.read()
    }

    private fun next(): Token {
        var state = State.NONE

        val value = StringBuilder()

        var radix = 10
        var floating = false

        while (buffer >= 0) {
            when (state) {
                State.NONE -> {
                    when (buffer) {
                        '#'.code -> {
                            state = State.COMMENT
                            read()
                        }

                        '0'.code -> {
                            read()
                            when (buffer) {
                                'b'.code -> {
                                    state = State.NUMBER
                                    radix = 2
                                    read()
                                }

                                'x'.code -> {
                                    state = State.NUMBER
                                    radix = 16
                                    read()
                                }

                                else -> {
                                    state = State.NUMBER
                                    radix = 8
                                    value.appendCodePoint('0'.code)
                                }
                            }
                        }

                        '"'.code -> {
                            state = State.STRING
                            read()
                        }

                        else -> {
                            if (Character.isWhitespace(buffer)) {
                                read()
                                continue
                            }
                            if (Character.isDigit(buffer)) {
                                state = State.NUMBER
                                continue
                            }
                            if (Character.isLetter(buffer)) {
                                state = State.SYMBOL
                                continue
                            }
                            value.appendCodePoint(buffer)
                            read()
                            return Token(TokenType.OTHER, value.toString(), 0u, 0.0)
                        }
                    }
                }

                State.COMMENT -> {
                    if (buffer == '\n'.code) state = State.NONE
                    read()
                }

                State.SYMBOL -> {
                    if (Character.isLetterOrDigit(buffer)) {
                        value.appendCodePoint(buffer)
                        read()
                        continue
                    }
                    return Token(TokenType.SYMBOL, value.toString(), 0u, 0.0)
                }

                State.NUMBER -> {
                    if (radix == 10 && !floating && buffer == '.'.code) {
                        floating = true
                        value.appendCodePoint(buffer)
                        read()
                        continue
                    }

                    if (!floating && buffer == 'u'.code) {
                        read()
                        val string = value.toString()
                        return Token(TokenType.INTEGER, string, string.toULong(radix), 0.0)
                    }

                    if (Character.isLetterOrDigit(buffer)) {
                        value.appendCodePoint(buffer)
                        read()
                        continue
                    }

                    val string = value.toString()

                    if (floating)
                        return Token(TokenType.FLOATING, string, 0u, string.toDouble())

                    return Token(TokenType.INTEGER, string, string.toLong(radix).toULong(), 0.0)
                }

                State.STRING -> {
                    if (buffer == '"'.code) {
                        read()
                        return Token(TokenType.STRING, value.toString(), 0u, 0.0)
                    }

                    value.appendCodePoint(buffer)
                    read()
                }
            }
        }

        return Token(TokenType.EOF, "", 0u, 0.0)
    }
}
