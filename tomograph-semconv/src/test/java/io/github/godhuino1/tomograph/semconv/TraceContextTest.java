package io.github.godhuino1.tomograph.semconv;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextTest {

    /** The example from the W3C Trace Context specification. */
    private static final String SPEC_EXAMPLE =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void generatesIdsInTheShapeTheSpecRequires() {
        for (int i = 0; i < 10_000; i++) {
            TraceContext context = TraceContext.newRoot();

            assertEquals(32, context.traceId().length(), context.traceId());
            assertEquals(16, context.spanId().length(), context.spanId());
            assertTrue(TraceContext.isValidTraceId(context.traceId()), context.traceId());
            assertTrue(TraceContext.isValidSpanId(context.spanId()), context.spanId());
            assertEquals(context.traceId().toLowerCase(), context.traceId());
            assertEquals(context.spanId().toLowerCase(), context.spanId());
        }
    }

    @Test
    void generatedIdsDoNotCollideInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(seen.add(TraceContext.newTraceId()), "duplicate trace id after " + i + " draws");
        }
    }

    @Test
    void aChildSpanKeepsTheTraceAndTakesANewSpanId() {
        TraceContext root = TraceContext.newRoot();
        TraceContext child = root.childSpan();

        assertEquals(root.traceId(), child.traceId());
        assertEquals(root.sampled(), child.sampled());
        assertNotEquals(root.spanId(), child.spanId());
    }

    @Test
    void rendersTheTraceparentHeader() {
        TraceContext context = new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", true);

        assertEquals(SPEC_EXAMPLE, context.traceparent());
        assertEquals(55, context.traceparent().length());
    }

    @Test
    void parsesTheSpecificationExample() {
        TraceContext context = TraceContext.parse(SPEC_EXAMPLE);

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", context.traceId());
        assertEquals("00f067aa0ba902b7", context.spanId());
        assertTrue(context.sampled());
        assertEquals(SPEC_EXAMPLE, context.traceparent());
    }

    @Test
    void readsTheSampledFlagFromBitZeroOnly() {
        assertTrue(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").sampled());
        assertTrue(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-03").sampled());
        assertFalse(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00").sampled());
        // Bit 1 is "random trace id", not sampling; it must not be mistaken for it.
        assertFalse(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-02").sampled());
    }

    @Test
    void acceptsAFutureVersionAndIgnoresTrailingFields() {
        // The spec requires forward compatibility, so an upgraded peer cannot break us.
        TraceContext context = TraceContext.parse(
                "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra-fields");

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", context.traceId());
        assertTrue(context.sampled());
    }

    @Test
    void rejectsAllZeroIdsBecauseTheyMeanNoIdAtAll() {
        assertFalse(TraceContext.isValidTraceId("0".repeat(32)));
        assertFalse(TraceContext.isValidSpanId("0".repeat(16)));
        assertThrows(IllegalArgumentException.class, () -> new TraceContext(
                "0".repeat(32), "00f067aa0ba902b7", true));
        assertThrows(IllegalArgumentException.class, () -> new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736", "0".repeat(16), true));
    }

    @Test
    void rejectsUppercaseHexEvenThoughHexIsNormallyCaseInsensitive() {
        assertFalse(TraceContext.isValidTraceId("4BF92F3577B34DA6A3CE929D0E0E4736"));
        assertFalse(TraceContext.isValidSpanId("00F067AA0BA902B7"));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01"));
    }

    @Test
    void rejectsWrongLengthsAndNonHexCharacters() {
        assertFalse(TraceContext.isValidTraceId("4bf92f3577b34da6a3ce929d0e0e473"));   // 31
        assertFalse(TraceContext.isValidTraceId("4bf92f3577b34da6a3ce929d0e0e47366")); // 33
        assertFalse(TraceContext.isValidTraceId("4bf92f3577b34da6a3ce929d0e0e473g"));
        assertFalse(TraceContext.isValidTraceId(null));
        assertFalse(TraceContext.isValidSpanId(""));
    }

    @Test
    void rejectsMalformedTraceparentValuesWithAnExplanation() {
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(null));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(""));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse("00-abc-def"));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra"));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-zz"));
    }

    @Test
    void roundTripsThroughTraceparent() {
        TraceContext original = TraceContext.newRoot(false);
        TraceContext reparsed = TraceContext.parse(original.traceparent());

        assertEquals(original, reparsed);
        assertEquals(original.hashCode(), reparsed.hashCode());
    }

    @Test
    void toStringIsTheTraceparentSoLogsArePasteable() {
        TraceContext context = TraceContext.newRoot();
        assertEquals(context.traceparent(), context.toString());
    }
}
