package io.github.godhuino1.tomograph.semconv;

/**
 * OpenTelemetry GenAI semantic convention attribute keys.
 *
 * <p><b>Verified</b> against {@link SemconvRevision#ATTRIBUTE_REGISTRY_FILE} in
 * {@link SemconvRevision#REPOSITORY} at commit {@link SemconvRevision#VERIFIED_COMMIT}
 * ({@link SemconvRevision#VERIFIED_COMMIT_DATE}). The first pass over this file was written
 * from working knowledge and marked unverified; the eventual check found real errors, which
 * is the argument for recording a target revision instead of a vague intention.
 *
 * <p>What the check corrected:
 * <ul>
 *   <li>{@code gen_ai.token.type} <b>no longer exists</b>. It was replaced by
 *       {@link #TOKEN_MODALITY}, which describes text/image/audio rather than
 *       input/output. Shipping the old key would have produced attributes no backend
 *       recognises, and the mistake was invisible because nothing validates attribute names.</li>
 *   <li>{@code gen_ai.system} is also gone from the registry, not merely deprecated. It is
 *       retained here only so that traces produced by older instrumentation can still be
 *       recognised on read.</li>
 *   <li>Cache-token breakdown was missing entirely — the difference between a plausible cost
 *       estimate and a wrong one, since cached input tokens are billed differently.</li>
 *   <li>Tool call arguments and results were missing, which left the "tool calls and their
 *       side effects" acceptance criterion with nowhere to record the side effect.</li>
 * </ul>
 *
 * <p>The registry is considerably larger than this class. Groups deliberately not listed yet
 * because nothing in the project emits them: {@code gen_ai.memory.*},
 * {@code gen_ai.evaluation.*}, {@code gen_ai.retrieval.documents} JSON shapes, and the
 * message-content attributes ({@code gen_ai.input.messages}, {@code gen_ai.output.messages},
 * {@code gen_ai.system_instructions}) whose capture the spec requires callers to opt into.
 * They are named here so that adding one is a lookup rather than an invention.
 *
 * <p>Why this module exists at all: the project's stated role is to be a <em>consumer</em> of
 * these conventions, never an inventor of its own. Centralising the keys means a spec rename
 * is one file to edit instead of a grep across every instrumentation module.
 */
public final class GenAiAttributes {

    // --- who and what -----------------------------------------------------------------

    /**
     * The AI system, e.g. {@code openai}, {@code anthropic}. <b>Removed from the registry.</b>
     *
     * @deprecated replaced by {@link #PROVIDER_NAME}. Kept only so a trace produced by older
     *             instrumentation can still be read; nothing new should emit it.
     */
    @Deprecated
    public static final String SYSTEM = "gen_ai.system";

    /** Who ran the model. The registry enumerates the allowed values, e.g. {@code openai}. */
    public static final String PROVIDER_NAME = "gen_ai.provider.name";

    /**
     * The operation being performed. The registry's value list is long — {@code chat},
     * {@code embeddings}, {@code execute_tool}, {@code invoke_agent}, {@code plan},
     * {@code invoke_workflow}, {@code fetch_response}, the {@code *_memory} family, and more.
     * The values Tomograph emits live in {@link SpanName}, because they are also the span name
     * prefixes and duplicating them here would invite drift.
     */
    public static final String OPERATION_NAME = "gen_ai.operation.name";

    // --- request ----------------------------------------------------------------------

    public static final String REQUEST_MODEL = "gen_ai.request.model";
    public static final String REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";
    public static final String REQUEST_TEMPERATURE = "gen_ai.request.temperature";
    public static final String REQUEST_TOP_P = "gen_ai.request.top_p";

    /**
     * Top-K sampling. The registry explicitly warns that OpenAI's {@code top_logprobs} is a
     * different thing and must not be reported here.
     */
    public static final String REQUEST_TOP_K = "gen_ai.request.top_k";

    public static final String REQUEST_FREQUENCY_PENALTY = "gen_ai.request.frequency_penalty";
    public static final String REQUEST_PRESENCE_PENALTY = "gen_ai.request.presence_penalty";
    public static final String REQUEST_STOP_SEQUENCES = "gen_ai.request.stop_sequences";
    public static final String REQUEST_CHOICE_COUNT = "gen_ai.request.choice.count";
    public static final String REQUEST_SEED = "gen_ai.request.seed";
    public static final String REQUEST_ENCODING_FORMATS = "gen_ai.request.encoding_formats";

    /** Whether the request was made in streaming mode. */
    public static final String REQUEST_STREAM = "gen_ai.request.stream";

    /** Reasoning / thinking effort, e.g. {@code low}, {@code medium}, {@code high}. */
    public static final String REQUEST_REASONING_LEVEL = "gen_ai.request.reasoning.level";

    // --- response ---------------------------------------------------------------------

    public static final String RESPONSE_ID = "gen_ai.response.id";
    public static final String RESPONSE_MODEL = "gen_ai.response.model";
    public static final String RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons";

    /**
     * Lifecycle status of a possibly background response: {@code queued}, {@code in_progress},
     * {@code completed}, {@code incomplete}, {@code failed}, {@code cancelled}. Distinct from
     * finish reasons, which describe why generation stopped once it had started.
     */
    public static final String RESPONSE_STATUS = "gen_ai.response.status";

    /** Seconds from issuing a streaming request to the first chunk. */
    public static final String RESPONSE_TIME_TO_FIRST_CHUNK = "gen_ai.response.time_to_first_chunk";

    // --- usage ------------------------------------------------------------------------
    //
    // The registry also defines per-modality variants that are subsets of these totals, using
    // the pattern gen_ai.usage.<modality>.<input|output>_tokens and
    // gen_ai.usage.<modality>.cache_read.input_tokens, for modality in text | image | audio.

    public static final String USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /** Input tokens served from a provider-managed cache. Included in the input total. */
    public static final String USAGE_CACHE_READ_INPUT_TOKENS = "gen_ai.usage.cache_read.input_tokens";

    /** Input tokens written to a provider-managed cache. Included in the input total. */
    public static final String USAGE_CACHE_WRITE_INPUT_TOKENS = "gen_ai.usage.cache_write.input_tokens";

    /** Output tokens spent on reasoning. Included in the output total. */
    public static final String USAGE_REASONING_OUTPUT_TOKENS = "gen_ai.usage.reasoning.output_tokens";

    /**
     * The modality being counted: {@code text}, {@code image}, {@code audio}, {@code unknown}.
     *
     * <p>This is what replaced {@code gen_ai.token.type}. The old key described which bucket a
     * count belonged to; the registry now expresses that through the attribute name itself
     * (the per-modality keys above) and uses this one for the modality dimension.
     */
    public static final String TOKEN_MODALITY = "gen_ai.token.modality";

    // --- tools ------------------------------------------------------------------------

    public static final String TOOL_NAME = "gen_ai.tool.name";
    public static final String TOOL_CALL_ID = "gen_ai.tool.call.id";
    public static final String TOOL_TYPE = "gen_ai.tool.type";
    public static final String TOOL_DESCRIPTION = "gen_ai.tool.description";

    /** Arguments passed to a tool call. The spec flags this as potentially sensitive. */
    public static final String TOOL_CALL_ARGUMENTS = "gen_ai.tool.call.arguments";

    /** What the tool returned. The spec flags this as potentially sensitive. */
    public static final String TOOL_CALL_RESULT = "gen_ai.tool.call.result";

    /** The tool definitions offered to the model. Also flagged as potentially sensitive. */
    public static final String TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    // --- agents -----------------------------------------------------------------------

    /**
     * A stable, provider-assigned identifier of a hosted agent resource — not an in-memory
     * instance id, which the registry explicitly discourages because it is transient.
     */
    public static final String AGENT_ID = "gen_ai.agent.id";

    public static final String AGENT_NAME = "gen_ai.agent.name";
    public static final String AGENT_DESCRIPTION = "gen_ai.agent.description";
    public static final String AGENT_VERSION = "gen_ai.agent.version";

    /** The top-level agent in this process; the registry carries these as an entity. */
    public static final String MAIN_AGENT_ID = "gen_ai.main_agent.id";
    public static final String MAIN_AGENT_NAME = "gen_ai.main_agent.name";
    public static final String MAIN_AGENT_DESCRIPTION = "gen_ai.main_agent.description";

    // --- conversation and data --------------------------------------------------------

    public static final String CONVERSATION_ID = "gen_ai.conversation.id";

    /** True only when context compaction is known to have been applied; never set false. */
    public static final String CONVERSATION_COMPACTED = "gen_ai.conversation.compacted";

    public static final String DATA_SOURCE_ID = "gen_ai.data_source.id";

    // --- retrieval and embeddings ------------------------------------------------------

    public static final String RETRIEVAL_QUERY_TEXT = "gen_ai.retrieval.query.text";
    public static final String RETRIEVAL_DOCUMENTS = "gen_ai.retrieval.documents";
    public static final String RETRIEVAL_TOP_K = "gen_ai.retrieval.top_k";
    public static final String EMBEDDINGS_DIMENSION_COUNT = "gen_ai.embeddings.dimension.count";

    // --- output classification --------------------------------------------------------

    /** The output media type the request asked for: {@code text}, {@code json}, {@code image}, {@code speech}. */
    public static final String OUTPUT_TYPE = "gen_ai.output.type";

    // --- workflow, prompt and skill vocabulary ----------------------------------------
    // Not emitted yet. Listed so that the vocabulary stays in one place rather than being
    // reinvented at the first instrumentation site that needs it.

    /** Low-cardinality, application-provided workflow name. Must not be a type name. */
    public static final String WORKFLOW_NAME = "gen_ai.workflow.name";

    public static final String PROMPT_NAME = "gen_ai.prompt.name";
    public static final String PROMPT_VERSION = "gen_ai.prompt.version";

    public static final String SKILL_NAME = "gen_ai.skill.name";
    public static final String SKILL_DESCRIPTION = "gen_ai.skill.description";
    public static final String SKILL_SOURCE_URI = "gen_ai.skill.source.uri";

    // --- general conventions we always need on a GenAI span ---------------------------
    //
    // These are not in the GenAI registry: they are general semantic conventions that a GenAI
    // span still carries, which is why they appear here rather than in a class of their own.

    /** Low-cardinality error classification. Present only on failed spans. */
    public static final String ERROR_TYPE = "error.type";

    public static final String SERVER_ADDRESS = "server.address";
    public static final String SERVER_PORT = "server.port";

    private GenAiAttributes() {
    }
}
