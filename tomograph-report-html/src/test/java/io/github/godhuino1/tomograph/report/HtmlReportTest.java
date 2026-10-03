package io.github.godhuino1.tomograph.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering tests, aimed at the three ways this file can go wrong: it can be injected into, its
 * layout can silently collapse, and it can recurse forever.
 */
class HtmlReportTest {

    private static CapturedSpan span(String spanId, String parentSpanId, String name,
                                     long start, long end, Map<String, Object> attributes) {
        return new CapturedSpan("checkout-agent", "io.github.godhuino1.tomograph", "trace-1",
                spanId, parentSpanId, name, 3, start, end, 0, attributes);
    }

    private static String renderSample() {
        SpanTree tree = SpanTree.of(List.of(
                span("a", null, "invoke_agent support-bot", 1_000_000, 6_000_000, Map.of()),
                span("b", "a", "chat gpt-4o", 1_500_000, 4_000_000,
                        Map.of("gen_ai.request.model", "gpt-4o",
                                "gen_ai.usage.input_tokens", 120L,
                                "gen_ai.usage.output_tokens", 45L)),
                span("c", "a", "execute_tool lookup_order", 4_200_000, 5_000_000, Map.of())));
        return HtmlReport.render(tree, HtmlReport.Metadata.of("checkout agent", "capture.json"));
    }

    @Test
    void producesOneSelfContainedFileWithNoExternalReferences() {
        String html = renderSample();

        assertTrue(html.startsWith("<!DOCTYPE html>"), "must be a complete document");
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("<style>"), "CSS must be inline");

        // The whole point: it opens from file:// on a machine with no network. Any of these would
        // turn an archived report into a broken page, or leak a request to a third party.
        for (String forbidden : List.of("http://", "https://", "<script", "<link ", "<img ", "src=")) {
            assertFalse(html.contains(forbidden),
                    "a self-contained report must not reference anything external, found: " + forbidden);
        }
    }

    @Test
    void showsTheTreeTheDurationsAndTheGenAiFields() {
        String html = renderSample();

        assertTrue(html.contains("invoke_agent support-bot"), "root span name");
        assertTrue(html.contains("chat gpt-4o"), "child span name");
        assertTrue(html.contains("execute_tool lookup_order"), "second child");
        assertTrue(html.contains("5.00 ms"), "the root's duration: " + html.substring(0, 200));
        assertTrue(html.contains("2.50 ms"), "the chat child's duration");
        assertTrue(html.contains("gpt-4o"), "the model badge");
        assertTrue(html.contains("120"), "input tokens");
        assertTrue(html.contains("45"), "output tokens");
        assertTrue(html.contains("checkout-agent"), "service name");
        assertTrue(html.contains("3 spans"), "span count");
    }

    @Test
    void childrenAreNestedInsideTheirParent() {
        String html = renderSample();

        // The chat span has to appear inside the root's <li>, i.e. after the root's name and
        // before the closing of its subtree. Checking the order of the two names is a cheap proxy
        // for nesting that does not depend on whitespace.
        int root = html.indexOf("invoke_agent support-bot");
        int child = html.indexOf("chat gpt-4o");
        int tool = html.indexOf("execute_tool lookup_order");
        assertTrue(root < child && child < tool,
                "spans must appear in tree order, got " + root + ", " + child + ", " + tool);
    }

    @Test
    void escapesEverythingThatCameFromTheObservedApplication() {
        // A prompt, a tool name or a user id can contain markup. This file is opened by a human in
        // a browser, so unescaped values make the report an injection vector.
        SpanTree tree = SpanTree.of(List.of(
                span("a", null, "<script>alert('xss')</script>", 0, 1,
                        Map.of("gen_ai.tool.name", "\"><img src=x onerror=alert(1)>"))));

        String html = HtmlReport.render(tree, HtmlReport.Metadata.of("t", "f.json"));

        assertFalse(html.contains("<script>alert"), "the span name was not escaped");
        assertFalse(html.contains("<img src=x"), "the attribute value was not escaped");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"),
                "escaped form not found in output");
    }

    @Test
    void warnsLoudlyWhenTheCaptureIsIncomplete() {
        SpanTree tree = SpanTree.of(List.of(
                span("a", null, "invoke_agent x", 0, 1_000_000, Map.of()),
                span("orphan", "not-in-file", "chat gpt-4o", 0, 500_000, Map.of())));

        String html = HtmlReport.render(tree, HtmlReport.Metadata.of("t", "f.json"));

        assertTrue(html.contains("This capture is not a complete trace"), "the partial-data warning is missing");
        assertTrue(html.contains("Do not read this as a complete agent run"),
                "the warning must say what the reader should conclude");
    }

    @Test
    void saysNothingAboutIncompletenessWhenTheCaptureIsComplete() {
        assertFalse(renderSample().contains("This capture is incomplete"));
    }

    @Test
    void survivesACycleInTheFile() {
        // A hand-edited or corrupted payload can claim A's parent is B and B's parent is A.
        // Without the guard this recurses until the stack ends.
        CapturedSpan a = span("a", "b", "chat a", 0, 100, Map.of());
        CapturedSpan b = span("b", "a", "chat b", 0, 100, Map.of());
        SpanTree tree = SpanTree.of(List.of(a, b));

        String html = HtmlReport.render(tree, HtmlReport.Metadata.of("t", "f.json"));

        // The property that matters: both spans must still be visible. An earlier version of
        // SpanTree found no roots in this input and therefore rendered an empty report - a corrupt
        // file that looks like a capture with no data.
        assertTrue(html.contains("chat a") && html.contains("chat b"),
                "spans in a cycle must be shown, not silently dropped");
        assertTrue(html.contains("contains a cycle"), "the cycle must be disclosed");
        assertTrue(html.contains("</html>"), "rendering still completed");
    }

    @Test
    void formatsDurationsAtEveryScale() {
        assertEquals("0 ns", HtmlReport.formatDuration(0));
        assertEquals("999 ns", HtmlReport.formatDuration(999));
        assertEquals("1.50 us", HtmlReport.formatDuration(1_500));
        assertEquals("2.50 ms", HtmlReport.formatDuration(2_500_000));
        assertEquals("1.50 s", HtmlReport.formatDuration(1_500_000_000));
    }

    @Test
    void usesADotAsTheDecimalSeparatorRegardlessOfTheMachineLocale() {
        // With a comma separator these become "left:0,000%" and "width:16,667%", which is invalid
        // CSS: the timeline silently collapses on some machines and not others.
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            String html = renderSample();
            assertTrue(html.contains("left:0.000%"), "left offset must use a dot");
            assertFalse(html.contains("left:0,000%"), "a comma decimal separator leaked into the CSS");
            assertTrue(html.contains("1.50 us") || html.contains("5.00 ms"),
                    "durations must use a dot too");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void anEmptyTreeStillProducesAValidDocument() {
        String html = HtmlReport.render(SpanTree.of(List.of()), HtmlReport.Metadata.of("t", "f.json"));

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("No spans in this file"));
        assertTrue(html.contains("</html>"));
    }
}
