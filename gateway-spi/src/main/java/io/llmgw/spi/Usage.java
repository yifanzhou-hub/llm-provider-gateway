package io.llmgw.spi;

/** Token accounting. Unknown values are reported as 0, never invented. */
public record Usage(int promptTokens, int completionTokens) {

    public static final Usage UNKNOWN = new Usage(0, 0);
}