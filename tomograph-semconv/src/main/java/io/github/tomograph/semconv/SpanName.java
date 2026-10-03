package io.github.tomograph.semconv;

import java.util.Objects;

/**
 * Span naming rules.
 *
 * <p>OpenTelemetry's GenAI conventions are prescriptive about span names, because a
 * backend groups and aggregates by span name. Getting this wrong does not produce an
 * error — it produces a dashboard where every row says "chat" with no way to tell
 * models apart. So names are built here, once, instead of being concatenated at each
 * instrumentation site.
 *
 * <p>The pattern in every case is {@code <operation> <subject>}:
 * <ul>
 *   <li>{@code chat gpt-4o}</li>
 *   <li>{@code execute_tool query_order}</li>
 *   <li>{@code invoke_agent support-bot}</li>
 *   <li>{@code embeddings text-embedding-3-small}</li>
 *   <li>{@code generate_content gemini-1.5-pro}</li>
 * </ul>
 *
 * <p>Missing subjects degrade to the bare operation name rather than to
 * {@code "chat null"}: a slightly under-specified name is useful, a literally wrong
 * one is not.
 */
public final class SpanName {

    public static final String CHAT = "chat";
    public static final String EMBEDDINGS = "embeddings";
    public static final String EXECUTE_TOOL = "execute_tool";
    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String CREATE_AGENT = "create_agent";
    public static final String RETRIEVAL = "retrieval";

    private SpanName() {
    }

    /** One model round trip. {@code model} may be null when the framework has not resolved it yet. */
    public static String chat(String model) {
        return withSubject(CHAT, model);
    }

    /** One embedding computation. */
    public static String embeddings(String model) {
        return withSubject(EMBEDDINGS, model);
    }

    /** One tool/function invocation. */
    public static String executeTool(String toolName) {
        return withSubject(EXECUTE_TOOL, toolName);
    }

    /** One end-to-end agent run. */
    public static String invokeAgent(String agentName) {
        return withSubject(INVOKE_AGENT, agentName);
    }

    /**
     * One document / vector retrieval step.
     *
     * <p>Note this is the one name whose subject is a data source rather than a model
     * or tool, because that is what the GenAI conventions use for retrieval spans.
     */
    public static String retrieval(String dataSourceId) {
        return withSubject(RETRIEVAL, dataSourceId);
    }

    private static String withSubject(String operation, String subject) {
        Objects.requireNonNull(operation, "operation");
        if (subject == null || subject.isBlank()) {
            return operation;
        }
        return operation + " " + subject.trim();
    }
}
