package io.github.godhuino1.tomograph.semconv;

import java.util.Objects;
import java.util.Set;

/**
 * Span naming rules for the GenAI conventions.
 *
 * <p><b>Verified</b> against {@link SemconvRevision#SPAN_MODEL_FILE} and
 * {@link SemconvRevision#ATTRIBUTE_REGISTRY_FILE} in {@link SemconvRevision#REPOSITORY} at commit
 * {@link SemconvRevision#VERIFIED_COMMIT}. The first version of this class held six of the
 * eighteen operation names, and declared {@code create_agent} without providing a builder for it.
 *
 * <p>OpenTelemetry is prescriptive about span names because backends group and aggregate by them.
 * Getting one wrong produces no error — it produces a dashboard where every row says "chat" with
 * no way to tell models apart, or a "create_agent" row that cannot be filtered by agent. So names
 * are built here, once, rather than concatenated at each instrumentation site.
 *
 * <h2>Provenance note, because it nearly went wrong</h2>
 *
 * <p>Re-checking this class, two suspicions arose: that {@code chat} might have been renamed
 * {@code inference} (the span type is {@code gen_ai.inference.client}), and that the
 * {@code generate_content} example in the javadoc was an invented string from an older draft.
 * Both suspicions were wrong — the registry lists both {@code chat} and {@code generate_content}
 * as members. Acting on either would have broken working code while "fixing" it. A plausible
 * correction needs the same evidence as an original claim.
 *
 * <h2>The subject rules</h2>
 *
 * <p>Each operation appends a subject, and the subject differs by operation. Where the conventions
 * define a degradation rule, a missing subject yields the bare operation name rather than
 * {@code "chat null"}: a slightly under-specified name is useful, a literally wrong one is not.
 *
 * <ul>
 *   <li>{@code chat} / {@code generate_content} / {@code text_completion} / {@code embeddings}
 *       — subject is the requested model</li>
 *   <li>{@code retrieval} — subject is the <b>data source</b>, not a model</li>
 *   <li>{@code create_agent} / {@code invoke_agent} / {@code plan} — subject is the agent name</li>
 *   <li>{@code execute_tool} — subject is the tool name; provider-specific refinements append
 *       further subjects (skill name, skill resource, executable name) and are not built here yet</li>
 *   <li>{@code invoke_workflow} — subject is the workflow name</li>
 *   <li>{@code fetch_response} and the memory operations — <b>no subject</b>. For
 *       {@code fetch_response} the conventions say so explicitly, because the response identifier
 *       is high cardinality.</li>
 * </ul>
 */
public final class SpanName {

    // --- inference -------------------------------------------------------------------

    /** Chat completion. */
    public static final String CHAT = "chat";

    /** Multimodal content generation, e.g. Gemini's generateContent. */
    public static final String GENERATE_CONTENT = "generate_content";

    /** Legacy text completion endpoint. */
    public static final String TEXT_COMPLETION = "text_completion";

    // --- other client operations -----------------------------------------------------

    public static final String EMBEDDINGS = "embeddings";
    public static final String RETRIEVAL = "retrieval";

    /** Fetching a previously generated response by id, without performing inference. */
    public static final String FETCH_RESPONSE = "fetch_response";

    // --- agents, tools, workflows ----------------------------------------------------

    public static final String CREATE_AGENT = "create_agent";
    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String EXECUTE_TOOL = "execute_tool";
    public static final String INVOKE_WORKFLOW = "invoke_workflow";

    /** An agent planning or task-decomposition phase. */
    public static final String PLAN = "plan";

    // --- memory ----------------------------------------------------------------------
    //
    // None of these carry a subject: the conventions specify the bare operation name.

    public static final String SEARCH_MEMORY = "search_memory";
    public static final String CREATE_MEMORY = "create_memory";
    public static final String UPDATE_MEMORY = "update_memory";
    public static final String UPSERT_MEMORY = "upsert_memory";
    public static final String DELETE_MEMORY = "delete_memory";
    public static final String CREATE_MEMORY_STORE = "create_memory_store";
    public static final String DELETE_MEMORY_STORE = "delete_memory_store";

    private static final Set<String> MEMORY_OPERATIONS = Set.of(
            SEARCH_MEMORY, CREATE_MEMORY, UPDATE_MEMORY, UPSERT_MEMORY,
            DELETE_MEMORY, CREATE_MEMORY_STORE, DELETE_MEMORY_STORE);

    private SpanName() {
    }

    /** One chat completion round trip. {@code model} may be null before the framework resolves it. */
    public static String chat(String model) {
        return withSubject(CHAT, model);
    }

    /** One multimodal generation round trip. */
    public static String generateContent(String model) {
        return withSubject(GENERATE_CONTENT, model);
    }

    /** One legacy text completion round trip. */
    public static String textCompletion(String model) {
        return withSubject(TEXT_COMPLETION, model);
    }

    /** One embedding computation. */
    public static String embeddings(String model) {
        return withSubject(EMBEDDINGS, model);
    }

    /**
     * One document / vector retrieval step.
     *
     * <p>This is the one name whose subject is a data source rather than a model or tool, which is
     * what the conventions specify for retrieval spans.
     */
    public static String retrieval(String dataSourceId) {
        return withSubject(RETRIEVAL, dataSourceId);
    }

    /**
     * Fetching a stored response by its identifier. Performs no inference and consumes no tokens.
     *
     * <p>Deliberately takes no argument: the response id is high cardinality and the conventions
     * exclude it from the span name.
     */
    public static String fetchResponse() {
        return FETCH_RESPONSE;
    }

    /** Creating a remote agent resource. */
    public static String createAgent(String agentName) {
        return withSubject(CREATE_AGENT, agentName);
    }

    /** One end-to-end agent run. */
    public static String invokeAgent(String agentName) {
        return withSubject(INVOKE_AGENT, agentName);
    }

    /** One tool/function invocation. */
    public static String executeTool(String toolName) {
        return withSubject(EXECUTE_TOOL, toolName);
    }

    /** One workflow execution coordinating several agents or GenAI calls. */
    public static String invokeWorkflow(String workflowName) {
        return withSubject(INVOKE_WORKFLOW, workflowName);
    }

    /** An agent's planning phase. */
    public static String plan(String agentName) {
        return withSubject(PLAN, agentName);
    }

    /**
     * A memory operation.
     *
     * <p>Memory span names carry no subject, so this validates that the caller passed one of the
     * seven memory operations and returns it unchanged. The alternative — accepting any string —
     * would let a typo produce a plausible-looking span name that no backend groups correctly.
     *
     * @throws IllegalArgumentException if {@code operation} is not a GenAI memory operation
     */
    public static String memory(String operation) {
        Objects.requireNonNull(operation, "operation");
        if (!MEMORY_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("not a GenAI memory operation: " + operation);
        }
        return operation;
    }

    /** True if the name would come out of one of the helpers above. */
    public static boolean isKnownOperation(String operation) {
        return MEMORY_OPERATIONS.contains(operation)
                || ALL_OPERATIONS.contains(operation);
    }

    private static final Set<String> ALL_OPERATIONS = Set.of(
            CHAT, GENERATE_CONTENT, TEXT_COMPLETION, EMBEDDINGS, RETRIEVAL, FETCH_RESPONSE,
            CREATE_AGENT, INVOKE_AGENT, EXECUTE_TOOL, INVOKE_WORKFLOW, PLAN);

    private static String withSubject(String operation, String subject) {
        Objects.requireNonNull(operation, "operation");
        if (subject == null || subject.isBlank()) {
            return operation;
        }
        return operation + " " + subject.trim();
    }
}
