package io.llmgw.spi;

/**
 * The only exception type a provider may throw. Anything else is caught by the core and
 * normalised to {@link ErrorCode#INTERNAL}.
 */
public class GatewayException extends Exception {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final boolean retriable;
    private final String providerId;

    public GatewayException(ErrorCode code, String message, boolean retriable, String providerId) {
        super(message);
        this.code = code;
        this.retriable = retriable;
        this.providerId = providerId;
    }

    public GatewayException(ErrorCode code, String message, boolean retriable, String providerId,
                            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retriable = retriable;
        this.providerId = providerId;
    }

    public ErrorCode code() {
        return code;
    }

    public boolean retriable() {
        return retriable;
    }

    public String providerId() {
        return providerId;
    }
}