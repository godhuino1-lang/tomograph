package io.github.godhuino1.tomograph.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads back a payload that a real agent actually put on a socket.
 *
 * <p>The fixture is copied verbatim from what {@code AgentToOtlpEndToEndTest} received during a
 * real run, not hand-written to match the reader. A hand-written fixture would agree with whatever
 * the reader happens to do; this one agrees with what the exporter happens to emit, which is the
 * pair that has to match.
 */
class OtlpJsonReaderTest {

    /** Captured from a real agent run: one span, one trace, one resource. */
    private static final String REAL_PAYLOAD = """
            {"resourceSpans":[{"resource":{"attributes":[\
            {"key":"service.name","value":{"stringValue":"unknown_service:java"}},\
            {"key":"telemetry.sdk.name","value":{"stringValue":"tomograph"}},\
            {"key":"telemetry.sdk.language","value":{"stringValue":"java"}},\
            {"key":"telemetry.sdk.version","value":{"stringValue":"0.1.0-SNAPSHOT"}}]},\
            "scopeSpans":[{"scope":{"name":"io.github.godhuino1.tomograph","version":"0.1.0-SNAPSHOT"},\
            "spans":[{"traceId":"760949f8de66ffaf77636886fd91dca4",\
            "spanId":"e6657f8bdc9aee99","parentSpanId":"186f1b40b86c10db",\
            "name":"instrument io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel",\
            "kind":1,"startTimeUnixNano":"1791030141538000000","endTimeUnixNano":"1791030141539000000",\
            "attributes":[\
            {"key":"tomograph.module","value":{"stringValue":"example-fakeagent"}},\
            {"key":"tomograph.classfile.bytes","value":{"intValue":"1784"}}],\
            "status":{"code":1}}]}]}]}
            """;

    @Test
    void readsTheResourceAndScopeThatApplyToTheSpanInsideThem() {
        List<CapturedSpan> spans = OtlpJsonReader.read(REAL_PAYLOAD);

        assertEquals(1, spans.size());
        CapturedSpan span = spans.get(0);

        // service.name lives on the resource, three levels above the span. Resolving it there
        // rather than per-span is the nesting the reader has to get right.
        assertEquals("unknown_service:java", span.serviceName());
        assertEquals("io.github.godhuino1.tomograph", span.scopeName());
    }

    @Test
    void keepsEpochNanosecondsExact() {
        CapturedSpan span = OtlpJsonReader.read(REAL_PAYLOAD).get(0);

        // 1791030141538000000 is about 1.79e18. A double stops representing consecutive integers
        // at 2^53, around 9e15, so parsing this as a number rather than a string would lose the
        // low digits - the same class of mistake that once stamped every span January 1970.
        assertEquals(1791030141538000000L, span.startEpochNanos());
        assertEquals(1791030141539000000L, span.endEpochNanos());
        assertEquals(1_000_000L, span.durationNanos());
        assertTrue(span.startEpochNanos() > (1L << 53),
                "the fixture must stay above the double-precision limit to be worth anything");
    }

    @Test
    void unwrapsAnyValueIncludingTheQuotedInt() {
        CapturedSpan span = OtlpJsonReader.read(REAL_PAYLOAD).get(0);

        assertEquals(2, span.attributes().size());
        assertEquals("example-fakeagent", span.stringAttribute("tomograph.module"));
        // intValue is a string on the wire, and comes back as a Long rather than a String.
        assertEquals(1784L, span.attributes().get("tomograph.classfile.bytes"));
    }

    @Test
    void reportsTheParentAndTheStatus() {
        CapturedSpan span = OtlpJsonReader.read(REAL_PAYLOAD).get(0);

        assertEquals("186f1b40b86c10db", span.parentSpanId());
        assertFalse(span.isRoot());
        assertEquals(1, span.statusCode());
        assertFalse(span.isError());
        assertEquals(32, span.traceId().length());
        assertEquals(16, span.spanId().length());
    }

    @Test
    void anAbsentParentMeansRootAndNotAnEmptyString() {
        String rootOnly = """
                {"resourceSpans":[{"resource":{"attributes":[]},"scopeSpans":[{"spans":[\
                {"traceId":"760949f8de66ffaf77636886fd91dca4","spanId":"e6657f8bdc9aee99",\
                "name":"chat gpt-4o","kind":3,"startTimeUnixNano":"100","endTimeUnixNano":"200"}]}]}]}
                """;

        CapturedSpan span = OtlpJsonReader.read(rootOnly).get(0);

        // OTLP omits the field for a root span. Coercing it to "" would make "absent" and
        // "present but empty" indistinguishable, and the tree code depends on telling them apart.
        assertNull(span.parentSpanId());
        assertTrue(span.isRoot());
        assertEquals(0, span.statusCode(), "no status object means UNSET");
        assertEquals(100L, span.startEpochNanos());
    }

    @Test
    void unwrapsNestedAndNonStringAnyValues() {
        String nested = """
                {"resourceSpans":[{"scopeSpans":[{"spans":[{"traceId":"aa","spanId":"bb","name":"n",\
                "startTimeUnixNano":"0","endTimeUnixNano":"0","attributes":[\
                {"key":"a.bool","value":{"boolValue":true}},\
                {"key":"a.double","value":{"doubleValue":2.5}},\
                {"key":"a.array","value":{"arrayValue":{"values":[\
                {"stringValue":"x"},{"intValue":"7"}]}}},\
                {"key":"a.kv","value":{"kvlistValue":{"values":[\
                {"key":"k","value":{"stringValue":"v"}}]}}},\
                {"key":"a.unknown","value":{"someFutureKind":42}}]}]}]}]}
                """;

        CapturedSpan span = OtlpJsonReader.read(nested).get(0);

        assertEquals(Boolean.TRUE, span.attributes().get("a.bool"));
        assertEquals(2.5, span.attributes().get("a.double"));
        assertEquals(List.of("x", 7L), span.attributes().get("a.array"));
        assertEquals(java.util.Map.of("k", "v"), span.attributes().get("a.kv"));
        // An unrecognised AnyValue member yields null rather than an exception: the spec requires
        // receivers to ignore what they do not understand, which is what makes forward
        // compatibility possible at all.
        assertNull(span.attributes().get("a.unknown"));
    }

    @Test
    void toleratesABareNumberWhereTheSpecExpectsAString() {
        String bare = """
                {"resourceSpans":[{"scopeSpans":[{"spans":[{"traceId":"aa","spanId":"bb","name":"n",\
                "startTimeUnixNano":123,"endTimeUnixNano":456}]}]}]}
                """;

        CapturedSpan span = OtlpJsonReader.read(bare).get(0);

        assertEquals(123L, span.startEpochNanos());
        assertEquals(456L, span.endEpochNanos());
    }

    @Test
    void anEmptyPayloadYieldsNoSpansRatherThanFailing() {
        assertTrue(OtlpJsonReader.read("{}").isEmpty());
        assertTrue(OtlpJsonReader.read("{\"resourceSpans\":[]}").isEmpty());
    }
}
