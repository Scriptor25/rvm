package io.scriptor.conf

import io.scriptor.util.Log.format
import java.io.InputStream

class Parser {

    private val stream: InputStream

    private var buffer: Int
    private var token: Token

    constructor(stream: InputStream) {
        this.stream = stream
        this.buffer = stream.read()
        this.token = next()
    }

    fun parse(): Node<*> = when {
        at(TokenType.OTHER, "{") -> parseObject()
        at(TokenType.OTHER, "[") -> parseArray()
        at(TokenType.STRING, TokenType.SYMBOL) -> StringNode(skip().string)
        at(TokenType.INTEGER) -> IntegerNode(skip().integer)
        at(TokenType.FLOATING) -> FloatingNode(skip().floating)
        at("true", "false") -> BooleanNode(skip().string.toBoolean())
        else -> throw NoSuchElementException()
    }

    fun parseObject(): ObjectNode {
        val node = ObjectNode()

        expect(TokenType.OTHER, "{")
        while (!at(TokenType.OTHER, "}")) {
            val key = expect(TokenType.SYMBOL, TokenType.STRING).string
            expect(TokenType.OTHER, ":")
            node[key] = parse()

            if (!at(TokenType.OTHER, "}")) {
                expect(TokenType.OTHER, ",")
            }
        }
        expect(TokenType.OTHER, "}")

        return node
    }

    fun parseArray(): ArrayNode {
        val node = ArrayNode()

        expect(TokenType.OTHER, "[")
        while (!at(TokenType.OTHER, "]")) {
            node.add(parse())

            if (!at(TokenType.OTHER, "]")) {
                expect(TokenType.OTHER, ",")
            }
        }
        expect(TokenType.OTHER, "]")

        return node
    }

    private fun at(type: TokenType, value: String): Boolean {
        return token.type == type && token.string == value
    }

    private fun at(vararg types: TokenType): Boolean {
        for (type in types) {
            if (token.type == type) {
                return true
            }
        }
        return false
    }

    private fun at(vararg values: String): Boolean {
        for (value in values) {
            if (token.string == value) {
                return true
            }
        }
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
        for (type in types) {
            if (token.type == type) {
                return skip()
            }
        }
        throw NoSuchElementException(format("types=%s", types.contentToString()))
    }

    private fun expect(vararg values: String): Token {
        for (value in values) {
            if (token.string == value) {
                return skip()
            }
        }
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

        while (buffer >= 0) when (state) {
            State.NONE -> when (buffer) {
                '#'.code -> {
                    state = State.COMMENT
                    read()
                }

                '0'.code -> {
                    state = State.NUMBER
                    read()

                    when (buffer) {
                        'b'.code -> {
                            radix = 2
                            read()
                        }

                        'x'.code -> {
                            radix = 16
                            read()
                        }

                        else -> {
                            radix = 8
                            value.appendCodePoint('0'.code)
                        }
                    }
                }

                '"'.code -> {
                    state = State.STRING
                    read()
                }

                else -> when {
                    buffer.toChar().isWhitespace() -> {
                        read()
                    }

                    buffer.toChar().isDigit() -> {
                        state = State.NUMBER
                    }

                    buffer.toChar().isLetter() -> {
                        state = State.SYMBOL
                    }

                    else -> {
                        value.appendCodePoint(buffer)
                        read()
                        return Token(TokenType.OTHER, value.toString(), 0U, 0.0)
                    }
                }
            }

            State.COMMENT -> {
                if (buffer == '\n'.code) {
                    state = State.NONE
                }
                read()
            }

            State.SYMBOL -> when {
                buffer.toChar().isLetterOrDigit() -> {
                    value.appendCodePoint(buffer)
                    read()
                }

                else -> return Token(TokenType.SYMBOL, value.toString(), 0U, 0.0)
            }

            State.NUMBER -> when {
                radix == 10 && !floating && buffer == '.'.code -> {
                    value.appendCodePoint(buffer)
                    read()

                    floating = true
                }

                !floating && buffer == 'u'.code -> {
                    read()

                    val string = value.toString()
                    return Token(TokenType.INTEGER, string, string.toULong(radix), 0.0)
                }

                buffer.toChar().isLetterOrDigit() -> {
                    value.appendCodePoint(buffer)
                    read()
                }

                else -> {
                    val string = value.toString()

                    return if (floating) {
                        Token(TokenType.FLOATING, string, 0U, string.toDouble())
                    } else {
                        Token(TokenType.INTEGER, string, string.toLong(radix).toULong(), 0.0)
                    }
                }
            }

            State.STRING -> when (buffer) {
                '"'.code -> {
                    read()
                    return Token(TokenType.STRING, value.toString(), 0U, 0.0)
                }

                else -> {
                    value.appendCodePoint(buffer)
                    read()
                }
            }
        }

        return Token(TokenType.EOF, "", 0U, 0.0)
    }
}
