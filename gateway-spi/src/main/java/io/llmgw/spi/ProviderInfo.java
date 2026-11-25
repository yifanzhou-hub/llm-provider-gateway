package io.llmgw.spi;

import java.util.Map;
import java.util.Set;

/**
 * Provider self-description, used by the registry and the {@code providers} command.
 *
 * @param id           unique, lower-case kebab-case, e.g. {@code mock}, {@code completion}
 * @param spiVersion   SPI major version this provider was built against
 * @param capabilities free-form capability tags, e.g. {@code text.chat}, {@code deterministic}
 * @param config       redacted configuration snapshot (keys matching key|token|secret|password
 *                     are replaced with ***)
 */
public record ProviderInfo(
        String id,
        String displayName,
        String spiVersion,
        Set<String> capabilities,
        Map<String, String> config) {
}