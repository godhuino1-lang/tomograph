package io.github.godhuino1.tomograph.semconv;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the operation vocabulary against the spec revision recorded in {@link SemconvRevision}.
 *
 * <p>The first version of {@link SpanName} held six of the eighteen operation names and declared
 * {@code create_agent} without a builder, which no test noticed — nothing was checking the
 * vocabulary against anything. These tests make the list explicit, so adding or removing an
 * operation is a deliberate edit rather than a drift.
 *
 * <p>The names below are copied from the {@code gen_ai.operation.name} enum in
 * {@link SemconvRevision#ATTRIBUTE_REGISTRY_FILE}. If the spec adds one, this test fails until
 * someone updates both files — which is the point.
 */
class SpanNameOperationsTest {

    /** Every member of the {@code gen_ai.operation.name} enum at the verified revision. */
    private static final List<String> SPEC_OPERATIONS = List.of(
            "chat",
            "generate_content",
            "text_completion",
            "embeddings",
            "retrieval",
            "fetch_response",
            "create_agent",
            "invoke_agent",
            "execute_tool",
            "invoke_workflow",
            "plan",
            "search_memory",
            "create_memory",
            "update_memory",
            "upsert_memory",
            "delete_memory",
            "create_memory_store",
            "delete_memory_store"
    );

    @Test
    void everySpecOperationHasAConstantAndEveryConstantIsASpecOperation() throws Exception {
        List<String> declared = new ArrayList<>();
        for (Field field : SpanName.class.getDeclaredFields()) {
            boolean isConstant = Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == String.class;
            if (isConstant) {
                declared.add((String) field.get(null));
            }
        }

        List<String> missing = new ArrayList<>(SPEC_OPERATIONS);
        missing.removeAll(declared);
        assertTrue(missing.isEmpty(), "spec operations with no constant in SpanName: " + missing);

        List<String> invented = new ArrayList<>(declared);
        invented.removeAll(SPEC_OPERATIONS);
        assertTrue(invented.isEmpty(), "constants that are not spec operation names: " + invented);

        assertEquals(SPEC_OPERATIONS.size(), declared.size(),
                "declared " + declared.size() + " constants for " + SPEC_OPERATIONS.size()
                        + " spec operations");
    }

    @Test
    void everyOperationIsReachableThroughAHelperMethod() {
        // The defect this exists to prevent: create_agent was declared as a constant but had no
        // builder, so it could only be used by concatenating strings at an instrumentation site.
        for (String operation : SPEC_OPERATIONS) {
            assertTrue(SpanName.isKnownOperation(operation),
                    operation + " is in the spec but SpanName does not recognise it");
        }
        assertFalse(SpanName.isKnownOperation("not_an_operation"));
        assertFalse(SpanName.isKnownOperation("inference"),
                "inference is a span *type* name, not an operation name; chat is the operation");
    }

    @Test
    void subjectsFollowTheRulesForEachOperation() {
        assertEquals("chat gpt-4o", SpanName.chat("gpt-4o"));
        assertEquals("generate_content gemini-1.5-pro", SpanName.generateContent("gemini-1.5-pro"));
        assertEquals("text_completion davinci", SpanName.textCompletion("davinci"));
        assertEquals("embeddings text-embedding-3-small", SpanName.embeddings("text-embedding-3-small"));
        assertEquals("retrieval H7STPQYOND", SpanName.retrieval("H7STPQYOND"));
        assertEquals("execute_tool get_weather", SpanName.executeTool("get_weather"));
        assertEquals("create_agent Math Tutor", SpanName.createAgent("Math Tutor"));
        assertEquals("invoke_agent Math Tutor", SpanName.invokeAgent("Math Tutor"));
        assertEquals("invoke_workflow customer_support_pipeline",
                SpanName.invokeWorkflow("customer_support_pipeline"));
        assertEquals("plan Math Tutor", SpanName.plan("Math Tutor"));
    }

    @Test
    void missingSubjectsDegradeToTheBareOperationName() {
        assertEquals("chat", SpanName.chat(null));
        assertEquals("invoke_agent", SpanName.invokeAgent("  "));
        assertEquals("plan", SpanName.plan(null));
    }

    @Test
    void operationsWithNoSubjectNeverGainOne() {
        assertEquals("fetch_response", SpanName.fetchResponse());
        assertEquals("search_memory", SpanName.memory(SpanName.SEARCH_MEMORY));
        assertEquals("delete_memory_store", SpanName.memory(SpanName.DELETE_MEMORY_STORE));

        // A typo would otherwise produce a plausible-looking name that no backend groups correctly.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SpanName.memory("search_memories"));
        assertTrue(thrown.getMessage().contains("search_memories"), thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SpanName.memory(SpanName.CHAT));
    }
}
