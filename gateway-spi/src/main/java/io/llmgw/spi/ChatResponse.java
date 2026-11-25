package io.llmgw.spi;

/**
 * Unified response every provider returns.
 *
 * <p>Success is defined as "no exception thrown", so {@code text} may be empty (for example
 * when generation was cut off by maxTokens). {@code finishReason} is always present.
 */
public record ChatResponse(
        String text,
        String finishReason,
        Usage usage,
        String providerId,
        long latencyMs,
        String requestId) {
}