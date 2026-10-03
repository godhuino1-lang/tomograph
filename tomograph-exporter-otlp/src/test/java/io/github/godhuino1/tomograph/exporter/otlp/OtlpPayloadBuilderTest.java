package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.TomographSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the wire format.
 *
 * <p>The full-payload assertion is deliberately exact rather than a set of
 * {@code contains} checks: with {@code contains}, a structural mistake (an attribute in
 * the wrong array, a missing comma, a status nested one level too deep) still passes.
 * Backends are the ones who would tell us, and only after a release.
 */
class OtlpPayloadBuilderTest {

    private final OtlpPayloadBuilder builder =
            new OtlpPayloadBuilder("svc", "io.github.godhuino1.tomograph", "1.0-test");

    @Test
    void producesTheExactExpectedPayloadForAMinimalSpan() {
        TomographSpan span = TomographSpan
                .builder("aa", "bb", TomographSpan.Kind.AGENT_RUN, "run")
                .start(1L)
                .duration(2L)
                .build();

        String expected = "{\"resourceSpans\":[{\"resource\":{\"attributes\":["
                + "{\"key\":\"service.name\",\"value\":{\"stringValue\":\"svc\"}},"
                + "{\"key\":\"telemetry.sdk.name\",\"value\":{\"stringValue\":\"tomograph\"}},"
                + "{\"key\":\"telemetry.sdk.language\",\"value\":{\"stringValue\":\"java\"}},"
                + "{\"key\":\"telemetry.sdk.version\",\"value\":{\"stringValue\":\"1.0-test\"}}"
                + "]},\"scopeSpans\":[{\"scope\":{\"name\":\"io.github.godhuino1.tomograph\",\"version\":\"1.0-test\"},"
                + "\"spans\":[{\"traceId\":\"aa\",\"spanId\":\"bb\",\"name\":\"run\",\"kind\":1,"
                + "\"startTimeUnixNano\":\"1\",\"endTimeUnixNano\":\"3\",\"status\":{\"code\":0}}]}]}]}";

        assertEquals(expected, builder.build(List.of(span)));
    }

    @Test
    void writesNanosecondTimestampsAsStringsToSurviveJavaScriptBackends() {
        TomographSpan span = TomographSpan
                .builder("aa", "bb", TomographSpan.Kind.LLM_CALL, "chat gpt-4o")
                .start(1_700_000_000_000_000_000L)
                .duration(1_500_000L)
                .build();

        String json = builder.build(List.of(span));

        assertTrue(json.contains("\"startTimeUnixNano\":\"1700000000000000000\""), json);
        assertTrue(json.contains("\"endTimeUnixNano\":\"1700000000001500000\""), json);
    }

    @Test
    void mapsKindsOntoSpanKindNumbers() {
        assertEquals(3, OtlpPayloadBuilder.otlpKind(TomographSpan.Kind.LLM_CALL));
        assertEquals(3, OtlpPayloadBuilder.otlpKind(TomographSpan.Kind.EMBEDDING));
        assertEquals(3, OtlpPayloadBuilder.otlpKind(TomographSpan.Kind.RETRIEVAL));
        assertEquals(1, OtlpPayloadBuilder.otlpKind(TomographSpan.Kind.AGENT_RUN));
        assertEquals(1, OtlpPayloadBuilder.otlpKind(TomographSpan.Kind.TOOL_CALL));
        assertEquals(1, OtlpPayloadBuilder.otlpKind(TomographSpan.Kind.INTERNAL));
    }

    @Test
    void keepsParentSpanIdOnlyWhenPresent() {
        TomographSpan withParent = TomographSpan
                .builder("aa", "bb", TomographSpan.Kind.TOOL_CALL, "tool")
                .parent("cc")
                .build();
        assertTrue(builder.build(List.of(withParent)).contains("\"parentSpanId\":\"cc\""));

        TomographSpan withoutParent = TomographSpan
                .builder("aa", "bb", TomographSpan.Kind.TOOL_CALL, "tool")
                .parent("  ")
                .build();
        assertFalse(builder.build(List.of(withoutParent)).contains("parentSpanId"));
    }

    @Test
    void typesAttributeValuesTheWayProto3JsonRequires() {
        // int64 -> quoted string; double -> number; bool -> literal; everything else -> string.
        assertEquals("{\"intValue\":\"1234\"}", OtlpPayloadBuilder.anyValue(1234));
        assertEquals("{\"intValue\":\"9000000000\"}", OtlpPayloadBuilder.anyValue(9_000_000_000L));
        assertEquals("{\"doubleValue\":0.2}", OtlpPayloadBuilder.anyValue(0.2d));
        assertEquals("{\"boolValue\":false}", OtlpPayloadBuilder.anyValue(false));
        assertEquals("{\"stringValue\":\"gpt-4o\"}", OtlpPayloadBuilder.anyValue("gpt-4o"));
        assertEquals("{\"stringValue\":\"\"}", OtlpPayloadBuilder.anyValue(null));
    }

    @Test
    void fallsBackToStringsForValuesJsonCannotRepresent() {
        // JSON has no NaN or Infinity literal; proto3's JSON mapping uses strings.
        assertEquals("{\"stringValue\":\"NaN\"}", OtlpPayloadBuilder.anyValue(Double.NaN));
        assertEquals("{\"stringValue\":\"Infinity\"}", OtlpPayloadBuilder.anyValue(Double.POSITIVE_INFINITY));
    }

    @Test
    void encodesCollectionsAsArrayValue() {
        assertEquals("{\"arrayValue\":{\"values\":[{\"stringValue\":\"a\"},{\"intValue\":\"2\"}]}}",
                OtlpPayloadBuilder.anyValue(List.of("a", 2)));
    }

    @Test
    void emitsStatusCodesAndMessageForErrors() {
        TomographSpan error = TomographSpan
                .builder("aa", "bb", TomographSpan.Kind.TOOL_CALL, "query_order")
                .error(new IllegalStateException("orders db down"))
                .build();
        String json = builder.build(List.of(error));
        assertTrue(json.contains("\"status\":{\"code\":2,\"message\":\"java.lang.IllegalStateException: orders db down\"}"), json);

        TomographSpan ok = TomographSpan
                .builder("aa", "bb", TomographSpan.Kind.LLM_CALL, "chat")
                .status(TomographSpan.Status.OK, null)
                .build();
        assertTrue(builder.build(List.of(ok)).contains("\"status\":{\"code\":1}"));
    }

    @Test
    void emitsEverySpanInTheBatch() {
        String json = builder.build(List.of(
                TomographSpan.builder("t", "s1", TomographSpan.Kind.LLM_CALL, "one").build(),
                TomographSpan.builder("t", "s2", TomographSpan.Kind.LLM_CALL, "two").build(),
                TomographSpan.builder("t", "s3", TomographSpan.Kind.LLM_CALL, "three").build()));

        assertTrue(json.contains("\"spanId\":\"s1\""));
        assertTrue(json.contains("\"spanId\":\"s2\""));
        assertTrue(json.contains("\"spanId\":\"s3\""));
        // Exactly two commas between three span objects, i.e. no trailing comma.
        assertFalse(json.contains(",]"));
    }
}
