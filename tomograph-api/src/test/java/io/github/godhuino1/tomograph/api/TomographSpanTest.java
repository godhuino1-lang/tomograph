package io.github.godhuino1.tomograph.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomographSpanTest {

    /** 2020-01-01T00:00:00Z expressed in nanoseconds since the Unix epoch. */
    private static final long YEAR_2020_NANOS = 1_577_836_800_000_000_000L;

    @Test
    void defaultsToEpochNanosecondsRatherThanToAMonotonicClock() {
        // Regression test for a bug that only a real end-to-end run could reveal: the
        // builder used to default to System.nanoTime(), a monotonic clock whose origin is
        // usually system boot, so every span exported to an OTLP backend was stamped
        // January 1970. Unit tests of the encoder could not see it, because the encoder
        // faithfully wrote whatever number it was handed.
        TomographSpan span = TomographSpan
                .builder("trace", "span", TomographSpan.Kind.LLM_CALL, "chat gpt-4o")
                .build();

        assertTrue(span.startEpochNanos() > YEAR_2020_NANOS,
                "startEpochNanos looks monotonic rather than epoch-based: " + span.startEpochNanos()
                        + " renders as " + Instant.ofEpochSecond(span.startEpochNanos() / 1_000_000_000L));
    }

    @Test
    void endTimeIsStartPlusDuration() {
        TomographSpan span = TomographSpan
                .builder("t", "s", TomographSpan.Kind.TOOL_CALL, "query_order")
                .start(1_000_000_000L)
                .duration(5_000_000L)
                .build();

        assertEquals(1_005_000_000L, span.endEpochNanos());
    }

    @Test
    void durationKeepsNanosecondPrecision() {
        // Durations come from nanoTime() deltas on purpose: sub-millisecond precision is
        // the whole point there, and a monotonic clock is the right tool for it.
        TomographSpan span = TomographSpan
                .builder("t", "s", TomographSpan.Kind.LLM_CALL, "chat")
                .duration(1_234L)
                .build();

        assertEquals(1_234L, span.durationNanos());
    }

    @Test
    void attributesAreCopiedDefensivelyAndNullValuesAreDropped() {
        Map<String, Object> callerOwned = new HashMap<>();
        callerOwned.put("gen_ai.request.model", "gpt-4o");

        TomographSpan span = TomographSpan
                .builder("t", "s", TomographSpan.Kind.LLM_CALL, "chat")
                .attributes(callerOwned)
                .attribute("never-stored", null)
                .build();

        callerOwned.put("added-after-build", "must not appear");

        assertEquals(1, span.attributes().size());
        assertTrue(span.attributes().containsKey("gen_ai.request.model"));
        assertFalse(span.attributes().containsKey("added-after-build"));
        assertFalse(span.attributes().containsKey("never-stored"));
    }

    @Test
    void statusIsNeverNullAndErrorsCaptureTheThrowable() {
        TomographSpan unset = TomographSpan
                .builder("t", "s", TomographSpan.Kind.INTERNAL, "x")
                .build();
        assertEquals(TomographSpan.Status.UNSET, unset.status());

        TomographSpan failed = TomographSpan
                .builder("t", "s", TomographSpan.Kind.TOOL_CALL, "query_order")
                .error(new IllegalStateException("orders db down"))
                .build();
        assertEquals(TomographSpan.Status.ERROR, failed.status());
        assertTrue(failed.statusMessage().contains("orders db down"));
    }

    @Test
    void rejectsMissingIdentityFields() {
        assertThrows(NullPointerException.class,
                () -> TomographSpan.builder(null, "s", TomographSpan.Kind.INTERNAL, "x").build());
        assertThrows(NullPointerException.class,
                () -> TomographSpan.builder("t", null, TomographSpan.Kind.INTERNAL, "x").build());
        assertThrows(NullPointerException.class,
                () -> TomographSpan.builder("t", "s", null, "x").build());
        assertThrows(NullPointerException.class,
                () -> TomographSpan.builder("t", "s", TomographSpan.Kind.INTERNAL, null).build());
    }
}
