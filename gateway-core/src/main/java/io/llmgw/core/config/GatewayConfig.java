package io.llmgw.core.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * Immutable view of {@code config/gateway.yml}.
 *
 * <p>Every field is already validated and resolved: {@code pluginsDir} is absolute (resolved
 * against {@code app.home}), timeouts are positive, and {@code defaultProvider} points at an
 * enabled provider in {@link #providers}.
 *
 * @param version          config schema version, currently 1
 * @param pluginsDir       absolute directory scanned for provider jars
 * @param defaultProvider  provider id used when the request does not name one
 * @param requestTimeoutMs default per-call budget in milliseconds
 * @param maxRetries       extra attempts for retriable failures; 0 disables retries
 * @param providers        per-provider settings keyed by provider id
 */
public record GatewayConfig(
        int version,
        Path pluginsDir,
        String defaultProvider,
        int requestTimeoutMs,
        int maxRetries,
        Map<String, ProviderSettings> providers) {

    /**
     * Settings for one provider. {@code options} keeps arbitrary keys as strings so that
     * providers can read them through {@code ProviderContext.settings()}.
     */
    public record ProviderSettings(boolean enabled, Map<String, String> options) {

        /** Settings used when a provider is discovered but not mentioned in the config file. */
        public static final ProviderSettings DEFAULT = new ProviderSettings(true, Map.of());
    }
}
