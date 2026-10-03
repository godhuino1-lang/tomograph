package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.TomographSpan;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Turns captured spans into an OTLP/HTTP JSON {@code ExportTraceServiceRequest}.
 *
 * <h2>Specification details implemented by hand — each one a silent-failure risk</h2>
 *
 * <p><b>All five were verified</b> against {@link OtlpSpecRevision#SPEC_FILE} in
 * {@link OtlpSpecRevision#REPOSITORY} at release {@link OtlpSpecRevision#VERIFIED_RELEASE}
 * (commit {@link OtlpSpecRevision#VERIFIED_COMMIT}). Every one held. The check also corrected
 * how strict one of them is, and surfaced two deliberate deviations from {@code SHOULD}
 * clauses, which are recorded in {@link OtlpHttpSpanExporter}.
 *
 * <ul>
 *   <li><b>64-bit integers are JSON strings.</b> The specification states this for
 *       {@code int64}/{@code fixed64} and names the timestamp fields —
 *       {@code startTimeUnixNano}, {@code endTimeUnixNano} — as covered. Bare numbers lose
 *       precision in JavaScript-based backends, which shows up as timestamps landing in 1970.</li>
 *   <li><b>{@code traceId}/{@code spanId} are hex, not base64.</b> The specification carves
 *       these two out of the standard proto3 JSON mapping by name — "they are not
 *       base64-encoded as is defined in the standard Protobuf JSON Mapping" — and gives a
 *       worked example. Base64 here produces spans that backends silently drop.</li>
 *   <li><b>Enum values MUST be integers.</b> Stricter than proto3's JSON mapping, which also
 *       accepts enum name strings: OTLP says names "MUST NOT be used". So {@code kind} is
 *       {@code 1}/{@code 3} and {@code status.code} is {@code 0}/{@code 1}/{@code 2}, never
 *       {@code "SPAN_KIND_CLIENT"}.</li>
 *   <li><b>{@code intValue} is quoted</b>, being an {@code int64} like any other.</li>
 *   <li><b>{@code status.code} of 0 means UNSET</b>, so a span with no status must still emit
 *       {@code {"code":0}} rather than omitting the field — otherwise some backends render
 *       an unset status as an error.</li>
 * </ul>
 *
 * <p>One further rule is satisfied by construction rather than by care: senders SHOULD NOT
 * emit empty envelopes, and the exporter only sends a non-empty batch.
 */
public final class OtlpPayloadBuilder {

    static final int SPAN_KIND_INTERNAL = 1;
    static final int SPAN_KIND_CLIENT = 3;
    static final int STATUS_CODE_UNSET = 0;
    static final int STATUS_CODE_OK = 1;
    static final int STATUS_CODE_ERROR = 2;

    private final String serviceName;
    private final String scopeName;
    private final String scopeVersion;

    public OtlpPayloadBuilder(String serviceName, String scopeName, String scopeVersion) {
        this.serviceName = serviceName;
        this.scopeName = scopeName;
        this.scopeVersion = scopeVersion;
    }

    public String build(List<TomographSpan> spans) {
        StringBuilder sb = new StringBuilder(640 + spans.size() * 320);
        sb.append("{\"resourceSpans\":[{");
        appendResource(sb);
        sb.append(",\"scopeSpans\":[{");
        appendScope(sb);
        sb.append(",\"spans\":[");
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendSpan(sb, spans.get(i));
        }
        sb.append("]}]}]}");
        return sb.toString();
    }

    private void appendResource(StringBuilder sb) {
        sb.append("\"resource\":{\"attributes\":[");
        int index = 0;
        index = appendAttribute(sb, index, "service.name", serviceName);
        index = appendAttribute(sb, index, "telemetry.sdk.name", "tomograph");
        index = appendAttribute(sb, index, "telemetry.sdk.language", "java");
        appendAttribute(sb, index, "telemetry.sdk.version", scopeVersion);
        sb.append("]}");
    }

    private void appendScope(StringBuilder sb) {
        sb.append("\"scope\":{\"name\":").append(OtlpJson.quote(scopeName))
                .append(",\"version\":").append(OtlpJson.quote(scopeVersion)).append('}');
    }

    private void appendSpan(StringBuilder sb, TomographSpan span) {
        sb.append("{\"traceId\":").append(OtlpJson.quote(span.traceId()))
                .append(",\"spanId\":").append(OtlpJson.quote(span.spanId()));

        if (span.parentSpanId() != null && !span.parentSpanId().isBlank()) {
            sb.append(",\"parentSpanId\":").append(OtlpJson.quote(span.parentSpanId()));
        }

        sb.append(",\"name\":").append(OtlpJson.quote(span.name()))
                .append(",\"kind\":").append(otlpKind(span.kind()))
                .append(",\"startTimeUnixNano\":\"").append(span.startEpochNanos()).append('"')
                .append(",\"endTimeUnixNano\":\"").append(span.endEpochNanos()).append('"');

        if (!span.attributes().isEmpty()) {
            sb.append(",\"attributes\":[");
            int index = 0;
            for (Map.Entry<String, Object> entry : span.attributes().entrySet()) {
                index = appendAttribute(sb, index, entry.getKey(), entry.getValue());
            }
            sb.append(']');
        }

        appendStatus(sb, span);
        sb.append('}');
    }

    private static void appendStatus(StringBuilder sb, TomographSpan span) {
        int code = switch (span.status()) {
            case OK -> STATUS_CODE_OK;
            case ERROR -> STATUS_CODE_ERROR;
            case UNSET -> STATUS_CODE_UNSET;
        };
        sb.append(",\"status\":{\"code\":").append(code);
        if (span.statusMessage() != null && !span.statusMessage().isBlank()) {
            sb.append(",\"message\":").append(OtlpJson.quote(span.statusMessage()));
        }
        sb.append('}');
    }

    private static int appendAttribute(StringBuilder sb, int index, String key, Object value) {
        if (key == null || key.isBlank()) {
            return index;
        }
        if (index > 0) {
            sb.append(',');
        }
        sb.append("{\"key\":").append(OtlpJson.quote(key))
                .append(",\"value\":").append(anyValue(value)).append('}');
        return index + 1;
    }

    /** Maps a Java value onto an OTLP {@code AnyValue}, one of proto3's several typed wrappers. */
    static String anyValue(Object value) {
        if (value == null) {
            return "{\"stringValue\":\"\"}";
        }
        if (value instanceof Boolean b) {
            return "{\"boolValue\":" + b + "}";
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return "{\"intValue\":\"" + value + "\"}";
        }
        if (value instanceof Float || value instanceof Double) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                // JSON cannot represent these; proto3's JSON mapping falls back to strings.
                return "{\"stringValue\":" + OtlpJson.quote(String.valueOf(d)) + "}";
            }
            return "{\"doubleValue\":" + d + "}";
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder sb = new StringBuilder("{\"arrayValue\":{\"values\":[");
            int i = 0;
            for (Object item : collection) {
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append(anyValue(item));
            }
            return sb.append("]}}").toString();
        }
        return "{\"stringValue\":" + OtlpJson.quote(String.valueOf(value)) + "}";
    }

    /**
     * CLIENT for anything that leaves the process (a model endpoint, a vector store) and
     * INTERNAL for work that happens inside it (the agent loop, a tool call handled
     * in-process). This drives how backends draw the service map, so it is a semantic
     * choice, not a formatting one.
     */
    static int otlpKind(TomographSpan.Kind kind) {
        return switch (kind) {
            case LLM_CALL, EMBEDDING, RETRIEVAL -> SPAN_KIND_CLIENT;
            case AGENT_RUN, TOOL_CALL, INTERNAL -> SPAN_KIND_INTERNAL;
        };
    }
}
