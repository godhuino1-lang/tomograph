package io.github.godhuino1.tomograph.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an {@code ExportTraceServiceRequest} in OTLP/JSON back into {@link CapturedSpan}s.
 *
 * <p>This is the inverse of what {@code tomograph-exporter-otlp} writes, and it exists so that the
 * report is a function of a <em>file</em>: any payload Tomograph produced, plus any payload produced
 * by another tool that speaks OTLP/JSON, can be rendered without the original process present.
 *
 * <h2>What the reader has to get right</h2>
 *
 * <p>proto3 JSON is fussy in ways that only show up on data captured from a real system:
 * <ul>
 *   <li><b>64-bit integers arrive as strings.</b> {@code startTimeUnixNano} is a string on the
 *       wire; parsing it as a number would work for small values and silently lose precision for
 *       real epoch nanoseconds (which are ~1.8e18, well past the 2^53 where a double stops being
 *       exact). It is parsed with {@code Long.parseLong}.</li>
 *   <li><b>AnyValue is a tagged union.</b> The value of an attribute is an object naming its type,
 *       and {@code intValue} is itself a string while {@code doubleValue} is a number.</li>
 *   <li><b>Nesting.</b> resource → scopeSpans → spans, and the resource's attributes apply to
 *       every span inside it, so {@code service.name} is resolved at the resource level.</li>
 *   <li><b>Absent means absent.</b> A root span has no {@code parentSpanId} key at all, and a span
 *       with no status has no {@code status} object. Both are left null/0 rather than invented.</li>
 * </ul>
 *
 * <p>Unknown fields are ignored rather than rejected: the specification says receivers must ignore
 * fields they do not recognise, which is what makes forward compatibility possible at all.
 */
public final class OtlpJsonReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OtlpJsonReader() {
    }

    public static List<CapturedSpan> read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    public static List<CapturedSpan> read(String json) {
        try {
            return parse(MAPPER.readTree(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<CapturedSpan> read(InputStream in) throws IOException {
        return parse(MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static List<CapturedSpan> parse(JsonNode root) {
        List<CapturedSpan> spans = new ArrayList<>();

        for (JsonNode resourceSpans : arrayOrEmpty(root.get("resourceSpans"))) {
            String serviceName = resourceAttribute(resourceSpans, "service.name");

            // scopeSpans sits under resourceSpans, so the resource's attributes reach every span
            // inside it without being repeated on each one. Getting this nesting wrong is easy and
            // shows up as "why is service.name missing" much later.
            for (JsonNode scopeSpans : arrayOrEmpty(resourceSpans.get("scopeSpans"))) {
                String scopeName = scopeSpans.path("scope").path("name").asText(null);

                for (JsonNode span : arrayOrEmpty(scopeSpans.get("spans"))) {
                    spans.add(toSpan(span, serviceName, scopeName));
                }
            }
        }
        return spans;
    }

    private static CapturedSpan toSpan(JsonNode span, String serviceName, String scopeName) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (JsonNode attribute : arrayOrEmpty(span.get("attributes"))) {
            String key = attribute.path("key").asText(null);
            if (key != null) {
                attributes.put(key, anyValue(attribute.get("value")));
            }
        }

        return new CapturedSpan(
                serviceName,
                scopeName,
                span.path("traceId").asText(""),
                span.path("spanId").asText(""),
                // Not path(...).asText(""): an absent parentSpanId means "root", and coercing it to
                // an empty string here would make an absent field indistinguishable from a present
                // empty one.
                span.hasNonNull("parentSpanId") ? span.get("parentSpanId").asText() : null,
                span.path("name").asText(""),
                // Enum values in OTLP/JSON are integers, never names.
                span.path("kind").asInt(0),
                parseLong(span, "startTimeUnixNano"),
                parseLong(span, "endTimeUnixNano"),
                span.path("status").path("code").asInt(0),
                // LinkedHashMap rather than Map.copyOf on purpose: Map.copyOf does not promise
                // iteration order, and a report whose attribute order changes between runs cannot
                // be diffed - which is exactly what v1.0's trajectory diff needs to do.
                new LinkedHashMap<>(attributes));
    }

    private static long parseLong(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return 0L;
        }
        // Quotes are the normal case; a producer that emitted a bare number is still read
        // correctly rather than being rejected over a detail the spec permits a parser to accept.
        return value.isTextual() ? Long.parseLong(value.asText()) : value.asLong();
    }

    private static String resourceAttribute(JsonNode resourceSpans, String key) {
        for (JsonNode attribute : arrayOrEmpty(resourceSpans.path("resource").get("attributes"))) {
            if (key.equals(attribute.path("key").asText(null))) {
                Object value = anyValue(attribute.get("value"));
                return (value == null) ? null : String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * Unwraps an {@code AnyValue}: the object names which member is set, and the value is under it.
     */
    private static Object anyValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.has("stringValue")) {
            return value.get("stringValue").asText();
        }
        if (value.has("intValue")) {
            JsonNode intValue = value.get("intValue");
            return intValue.isTextual() ? Long.parseLong(intValue.asText()) : intValue.asLong();
        }
        if (value.has("doubleValue")) {
            return value.get("doubleValue").asDouble();
        }
        if (value.has("boolValue")) {
            return value.get("boolValue").asBoolean();
        }
        if (value.has("bytesValue")) {
            return value.get("bytesValue").asText();
        }
        if (value.has("arrayValue")) {
            List<Object> items = new ArrayList<>();
            for (JsonNode item : arrayOrEmpty(value.get("arrayValue").get("values"))) {
                items.add(anyValue(item));
            }
            return List.copyOf(items);
        }
        if (value.has("kvlistValue")) {
            Map<String, Object> entries = new LinkedHashMap<>();
            for (JsonNode entry : arrayOrEmpty(value.get("kvlistValue").get("values"))) {
                entries.put(entry.path("key").asText(""), anyValue(entry.get("value")));
            }
            return new LinkedHashMap<>(entries);
        }
        return null;
    }

    private static Iterable<JsonNode> arrayOrEmpty(JsonNode node) {
        return (node == null || !node.isArray()) ? List.of() : node;
    }
}
