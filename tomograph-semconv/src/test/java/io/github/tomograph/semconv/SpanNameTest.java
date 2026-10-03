package io.github.tomograph.semconv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the span naming contract.
 *
 * <p>These assertions look trivial on purpose. Their value is not that the logic is
 * hard, it is that <b>a rename is a breaking change for every dashboard built on
 * Tomograph</b>, and a test is the cheapest way to force that change to be deliberate
 * instead of accidental.
 */
class SpanNameTest {

    @Test
    void buildsOperationWithSubject() {
        assertEquals("chat gpt-4o", SpanName.chat("gpt-4o"));
        assertEquals("embeddings text-embedding-3-small", SpanName.embeddings("text-embedding-3-small"));
        assertEquals("execute_tool query_order", SpanName.executeTool("query_order"));
        assertEquals("invoke_agent support-bot", SpanName.invokeAgent("support-bot"));
        assertEquals("retrieval orders-kb", SpanName.retrieval("orders-kb"));
    }

    @Test
    void degradesToBareOperationWhenSubjectIsMissing() {
        assertEquals("chat", SpanName.chat(null));
        assertEquals("chat", SpanName.chat(""));
        assertEquals("chat", SpanName.chat("   "));
        assertEquals("execute_tool", SpanName.executeTool(null));
        assertEquals("invoke_agent", SpanName.invokeAgent(null));
    }

    @Test
    void trimsSubjectWithoutCorruptingInternalSpaces() {
        assertEquals("chat gpt-4o", SpanName.chat("  gpt-4o  "));
        assertEquals("invoke_agent support bot", SpanName.invokeAgent("support bot"));
    }

    @Test
    void neverEmitsTheStringNull() {
        // The failure mode this guards against: a dashboard where every row reads "chat null".
        String[] names = {
                SpanName.chat(null), SpanName.embeddings(null),
                SpanName.executeTool(null), SpanName.invokeAgent(null), SpanName.retrieval(null)
        };
        for (String name : names) {
            assertFalse(name.contains("null"), name);
        }
    }
}
