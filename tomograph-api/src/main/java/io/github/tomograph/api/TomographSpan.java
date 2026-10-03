package io.github.tomograph.api;

import java.util.Map;
import java.util.Objects;

/**
 * One captured unit of work inside an agent run.
 *
 * <p>Field names follow the OpenTelemetry GenAI semantic conventions where a
 * convention exists; anything Tomograph-specific is prefixed {@code tomograph.}
 * (see the {@code tomograph-semconv} module, added in v0.1).
 *
 * <p>Immutable and safe to hand to any thread.
 */
public record TomographSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        Kind kind,
        String name,
        long startEpochNanos,
        long durationNanos,
        Map<String, Object> attributes,
        Status status,
        String statusMessage) {

    /** The five semantic kinds Tomograph commits to capturing (v0.1 acceptance criterion). */
    public enum Kind {
        /** One end-to-end agent run: a user request flowing through the loop. */
        AGENT_RUN,
        /** One model invocation (request + response). */
        LLM_CALL,
        /** One tool/function invocation, including its side effects. */
        TOOL_CALL,
        /** One retrieval step (vector search, keyword search, document lookup). */
        RETRIEVAL,
        /** One embedding computation. */
        EMBEDDING,
        /** Anything else Tomograph measures: queueing, serialization, retries. */
        INTERNAL
    }

    public enum Status {
        UNSET,
        OK,
        ERROR
    }

    public TomographSpan {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        attributes = (attributes == null) ? Map.of() : Map.copyOf(attributes);
        status = (status == null) ? Status.UNSET : status;
    }

    public static Builder builder(String traceId, String spanId, Kind kind, String name) {
        return new Builder(traceId, spanId, kind, name);
    }

    public long endEpochNanos() {
        return startEpochNanos + durationNanos;
    }

    /** Small builder: instrumented call sites fill 6-12 attributes and would be unreadable otherwise. */
    public static final class Builder {

        private final String traceId;
        private final String spanId;
        private final Kind kind;
        private final String name;
        private String parentSpanId;
        private long startEpochNanos = System.nanoTime();
        private long durationNanos;
        private final Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        private Status status = Status.UNSET;
        private String statusMessage;

        private Builder(String traceId, String spanId, Kind kind, String name) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.kind = kind;
            this.name = name;
        }

        public Builder parent(String parentSpanId) {
            this.parentSpanId = parentSpanId;
            return this;
        }

        public Builder start(long epochNanos) {
            this.startEpochNanos = epochNanos;
            return this;
        }

        public Builder duration(long nanos) {
            this.durationNanos = nanos;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        /** Null values are dropped: OTLP has no null attribute, and a missing key means "not captured". */
        public Builder attributes(Map<String, Object> values) {
            if (values != null) {
                values.forEach(this::attribute);
            }
            return this;
        }

        public Builder status(Status status, String message) {
            this.status = status;
            this.statusMessage = message;
            return this;
        }

        public Builder error(Throwable t) {
            if (t != null) {
                this.status = Status.ERROR;
                this.statusMessage = t.getClass().getName() + ": " + t.getMessage();
            }
            return this;
        }

        public TomographSpan build() {
            return new TomographSpan(traceId, spanId, parentSpanId, kind, name,
                    startEpochNanos, durationNanos, attributes, status, statusMessage);
        }
    }
}
