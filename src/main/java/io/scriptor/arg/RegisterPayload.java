package io.scriptor.arg;

public record RegisterPayload(int register, long value) implements Payload {
}
