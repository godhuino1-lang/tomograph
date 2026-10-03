package io.github.godhuino1.tomograph.report;

import java.util.Map;

/**
 * One span as read back out of a captured OTLP/JSON payload.
 *
 * <p>Deliberately a plain record rather than the agent's own span model: this module reads
 * <em>files</em>, and the file is OTLP/JSON. Depending on the internal model would mean a report
 * that can only be produced by the exact build that captured the data — which defeats the point of
 * an offline report you can attach to a bug report two years later.
 *
 * <p>Times are epoch nanoseconds and stay {@code long}. Everything upstream keeps them as strings
 * so no JSON consumer loses precision; the first moment that stops being necessary is here, where
 * the value has arrived intact and is only being formatted.
 *
 * @param serviceName      from the resource's {@code service.name}
 * @param scopeName        the instrumentation scope, i.e. which library produced the span
 * @param traceId          32 lowercase hex characters
 * @param spanId           16 lowercase hex characters
 * @param parentSpanId     16 hex characters, or null/empty for a root span
 * @param name             the span name, already in {@code <operation> <subject>} form
 * @param kind             OTLP span kind as an integer (1 = INTERNAL, 2 = SERVER, 3 = CLIENT)
 * @param startEpochNanos  start time, epoch nanoseconds
 * @param endEpochNanos    end time, epoch nanoseconds
 * @param statusCode       OTLP status code as an integer (0 = UNSET, 1 = OK, 2 = ERROR)
 * @param attributes       every attribute, with primitives unwrapped from their {@code AnyValue}
 */
public record CapturedSpan(
        String serviceName,
        String scopeName,
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        int kind,
        long startEpochNanos,
        long endEpochNanos,
        int statusCode,
        Map<String, Object> attributes) {

    public long durationNanos() {
        return endEpochNanos - startEpochNanos;
    }

    /** A span with no parent is the root of its trace. OTLP omits the field rather than sending "". */
    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isEmpty();
    }

    public boolean isError() {
        return statusCode == 2;
    }

    /** Convenience for the report: attributes arrive as plain objects and are usually strings. */
    public String stringAttribute(String key) {
        Object value = attributes.get(key);
        return (value == null) ? null : String.valueOf(value);
    }
}
