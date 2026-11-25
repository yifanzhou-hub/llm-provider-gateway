package io.llmgw.spi;

import java.time.Duration;
import java.util.Map;

/**
 * Per-call context handed to a provider. Providers must respect {@link #remainingTimeout()}
 * and must never write to stdout; use {@link #log(String)} instead.
 */
public interface ProviderContext {

    /** Remaining time budget for this call; providers should use it as their network timeout. */
    Duration remainingTimeout();

    String requestId();

    /** Read-only settings for this provider instance. */
    Map<String, String> settings();

    /** Structured log line; the core routes it to stderr. */
    void log(String message);
}