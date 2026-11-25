package io.llmgw.spi;

import java.util.List;
import java.util.Map;

/**
 * Unified request every provider receives.
 *
 * <p>Nullable fields mean "use the provider default": {@code maxTokens}, {@code temperature},
 * {@code timeoutMs}, {@code metadata}. Validation is the core's job; providers must not assume
 * non-null except for {@code messages}.
 */
public record ChatRequest(
        List<Message> messages,
        Integer maxTokens,
        Double temperature,
        Integer timeoutMs,
        Map<String, String> metadata) {
}