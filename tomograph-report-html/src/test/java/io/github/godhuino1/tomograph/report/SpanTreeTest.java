package io.github.godhuino1.tomograph.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tree assembly, with an emphasis on the case that matters: a file that is not a complete trace.
 */
class SpanTreeTest {

    private static CapturedSpan span(String spanId, String parentSpanId, long start, long end) {
        return new CapturedSpan("svc", "scope", "trace1", spanId, parentSpanId,
                "chat gpt-4o", 3, start, end, 0, Map.of());
    }

    @Test
    void linksChildrenToTheirParentAndFindsTheRoot() {
        SpanTree tree = SpanTree.of(List.of(
                span("a", null, 0, 100),
                span("b", "a", 10, 40),
                span("c", "a", 50, 90),
                span("d", "b", 15, 20)));

        assertEquals(List.of("a"), tree.roots().stream().map(CapturedSpan::spanId).toList());
        assertEquals(List.of("b", "c"), tree.childrenOf("a").stream().map(CapturedSpan::spanId).toList());
        assertEquals(List.of("d"), tree.childrenOf("b").stream().map(CapturedSpan::spanId).toList());
        assertTrue(tree.childrenOf("d").isEmpty());
        assertTrue(tree.byId("c").isPresent());
        assertTrue(tree.byId("zz").isEmpty());
        assertFalse(tree.isPartial());
    }

    @Test
    void aSpanWhoseParentIsMissingIsShownAsARootAndCounted() {
        // The realistic cause: an export batch was cut off, so the parent never made it into
        // this file. Dropping the child would hide a step the agent really performed; showing it
        // silently as a root would claim the run started there. So it is shown AND counted.
        SpanTree tree = SpanTree.of(List.of(
                span("a", null, 0, 100),
                span("orphan", "not-in-this-file", 10, 20)));

        assertEquals(2, tree.roots().size());
        assertEquals(1, tree.orphanCount());
        assertTrue(tree.isPartial());
    }

    @Test
    void computesTheSpanOfTheWholeFileAndCountsErrors() {
        SpanTree tree = SpanTree.of(List.of(
                span("a", null, 1_000, 5_000),
                new CapturedSpan("svc", "scope", "trace1", "b", "a", "execute_tool x", 1,
                        1_500, 2_000, 2, Map.of())));

        assertEquals(4_000L, tree.totalDurationNanos());
        assertEquals(1, tree.errorCount());
        assertEquals(List.of("trace1"), tree.traceIds());
    }

    @Test
    void anEmptyFileProducesAnEmptyTreeRatherThanThrowing() {
        SpanTree tree = SpanTree.of(List.of());

        assertTrue(tree.all().isEmpty());
        assertTrue(tree.roots().isEmpty());
        assertEquals(0L, tree.totalDurationNanos());
        assertTrue(tree.traceIds().isEmpty());
        assertFalse(tree.isPartial());
    }
}
