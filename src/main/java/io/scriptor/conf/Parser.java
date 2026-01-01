package io.scriptor.conf;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.NoSuchElementException;

public final class Parser {

    private final InputStream stream;
    private int buffer;
    private Token token;

    public Parser(final @NotNull InputStream stream) throws IOException {
        this.stream = stream;
        this.buffer = stream.read();
        this.token = next();
    }

    public @NotNull Node<?> parse() throws IOException {
        if (at(TokenType.OTHER, "{"))
            return parseObject();
        if (at(TokenType.OTHER, "["))
            return parseArray();

        if (at(TokenType.STRING, TokenType.SYMBOL))
            return new StringNode(skip().string());
        if (at(TokenType.INTEGER))
            return new IntegerNode(skip().integer());
        if (at(TokenType.FLOATING))
            return new FloatingNode(skip().floating());

        if (at("true", "false"))
            return new BooleanNode(Boolean.parseBoolean(skip().string()));

        throw new NoSuchElementException();
    }

    public @NotNull ObjectNode parseObject() throws IOException {
        final var node = new ObjectNode();

        expect(TokenType.OTHER, "{");
        while (!at(TokenType.OTHER, "}")) {
            final var key = expect(TokenType.SYMBOL, TokenType.STRING).string();
            expect(TokenType.OTHER, ":");
            final var value = parse();

            node.put(key, value);

            if (!at(TokenType.OTHER, "}"))
                expect(TokenType.OTHER, ",");
        }
        expect(TokenType.OTHER, "}");

        return node;
    }

    public @NotNull ArrayNode parseArray() throws IOException {
        final var node = new ArrayNode();

        expect(TokenType.OTHER, "[");
        while (!at(TokenType.OTHER, "]")) {
            final var value = parse();

            node.add(value);

            if (!at(TokenType.OTHER, "]"))
                expect(TokenType.OTHER, ",");
        }
        expect(TokenType.OTHER, "]");

        return node;
    }

    private boolean at(final @NotNull TokenType type, final @NotNull String value) {
        return token.type() == type && token.string().equals(value);
    }

    private boolean at(final @NotNull TokenType... types) {
        for (final var type : types)
            if (token.type() == type)
                return true;
        return false;
    }

    private boolean at(final @NotNull String... values) {
        for (final var value : values)
            if (token.string().equals(value))
                return true;
        return false;
    }

    private void expect(final @NotNull TokenType type, final @NotNull String value) throws IOException {
        if (token.type() == type && token.string().equals(value)) {
            token = next();
            return;
        }
        throw new NoSuchElementException("type=%s, value=%s".formatted(type, value));
    }

    private @NotNull Token expect(final @NotNull TokenType... types) throws IOException {
        for (final var type : types)
            if (token.type() == type)
                return skip();
        throw new NoSuchElementException("types=%s".formatted(Arrays.toString(types)));
    }

    private @NotNull Token expect(final @NotNull String... values) throws IOException {
        for (final var value : values)
            if (token.string().equals(value))
                return skip();
        throw new NoSuchElementException("values=%s".formatted(Arrays.toString(values)));
    }

    private @NotNull Token skip() throws IOException {
        final var x = token;
        token = next();
        return x;
    }

    private void read() throws IOException {
        buffer = stream.read();
    }

    private @NotNull Token next() throws IOException {
        var state = State.NONE;

        final var value = new StringBuilder();

        var radix    = 10;
        var floating = false;

        while (buffer >= 0) {
            switch (state) {
                case NONE -> {
                    switch (buffer) {
                        case '#' -> {
                            state = State.COMMENT;
                            read();
                        }
                        case '0' -> {
                            read();
                            switch (buffer) {
                                case 'b' -> {
                                    state = State.NUMBER;
                                    radix = 0b10;
                                    read();
                                }
                                case 'x' -> {
                                    state = State.NUMBER;
                                    radix = 0x10;
                                    read();
                                }
                                default -> {
                                    state = State.NUMBER;
                                    radix = 010;
                                    value.appendCodePoint('0');
                                }
                            }
                        }
                        case '"' -> {
                            state = State.STRING;
                            read();
                        }
                        default -> {
                            if (Character.isWhitespace(buffer)) {
                                read();
                                break;
                            }
                            if (Character.isDigit(buffer)) {
                                state = State.NUMBER;
                                break;
                            }
                            if (Character.isLetter(buffer)) {
                                state = State.SYMBOL;
                                break;
                            }
                            value.appendCodePoint(buffer);
                            read();
                            return new Token(TokenType.OTHER, value.toString(), 0L, 0.0D);
                        }
                    }
                }
                case COMMENT -> {
                    if (buffer == '\n')
                        state = State.NONE;
                    read();
                }
                case SYMBOL -> {
                    if (Character.isLetterOrDigit(buffer)) {
                        value.appendCodePoint(buffer);
                        read();
                        break;
                    }
                    return new Token(TokenType.SYMBOL, value.toString(), 0L, 0.0D);
                }
                case NUMBER -> {
                    if (radix == 10 && !floating && buffer == '.') {
                        floating = true;
                        value.appendCodePoint(buffer);
                        read();
                        break;
                    }
                    if (!floating && buffer == 'u') {
                        read();
                        final var string = value.toString();
                        return new Token(TokenType.INTEGER, string, Long.parseUnsignedLong(string, radix), 0.0D);
                    }
                    if (Character.isLetterOrDigit(buffer)) {
                        value.appendCodePoint(buffer);
                        read();
                        break;
                    }
                    final var string = value.toString();
                    if (floating)
                        return new Token(TokenType.FLOATING, string, 0L, Double.parseDouble(string));
                    return new Token(TokenType.INTEGER, string, Long.parseLong(string, radix), 0.0D);
                }
                case STRING -> {
                    if (buffer == '"') {
                        read();
                        return new Token(TokenType.STRING, value.toString(), 0L, 0.0D);
                    }
                    value.appendCodePoint(buffer);
                    read();
                }
            }
        }

        return new Token(TokenType.EOF, "", 0L, 0.0D);
    }
}
