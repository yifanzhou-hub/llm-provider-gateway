package io.llmgw.spi;

/** The only error categories the gateway exposes. Each maps to one CLI exit code. */
public enum ErrorCode {
    INVALID_REQUEST,
    PROVIDER_NOT_FOUND,
    PROVIDER_DISABLED,
    TIMEOUT,
    UPSTREAM_ERROR,
    INVALID_RESPONSE,
    CONFIG_INVALID,
    INTERNAL
}