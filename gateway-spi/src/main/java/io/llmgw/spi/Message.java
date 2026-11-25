package io.llmgw.spi;

/** One chat message. Role is limited to the three values the gateway understands. */
public record Message(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT }
}