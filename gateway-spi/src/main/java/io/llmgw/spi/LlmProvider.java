package io.llmgw.spi;

/**
 * Service Provider Interface. Implementations are discovered through
 * {@code META-INF/services/io.llmgw.spi.LlmProvider} and must depend on this module only.
 */
public interface LlmProvider {

    /** SPI major version. Bumping it means a breaking contract change. */
    String SPI_VERSION = "1";

    ProviderInfo info();

    ChatResponse invoke(ChatRequest request, ProviderContext context) throws GatewayException;

    default boolean healthy() {
        return true;
    }
}