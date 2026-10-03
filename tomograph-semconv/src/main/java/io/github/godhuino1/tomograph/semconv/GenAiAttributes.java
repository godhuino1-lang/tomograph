package io.github.godhuino1.tomograph.semconv;

/**
 * OpenTelemetry GenAI semantic convention attribute keys.
 *
 * <p><b>Status: NOT YET VERIFIED against a specific spec revision.</b> These keys were
 * written from working knowledge, because the development sandbox could not reach the
 * specification text: {@code opentelemetry.io} renders its tables client-side (a text fetch
 * returns only navigation), {@code raw.githubusercontent.com} is DNS-blocked, the GitHub
 * contents API was rate-limited, and the CDN mirrors were unreachable.
 *
 * <p><b>The conventions have also moved.</b> Fetching {@code docs/gen-ai/gen-ai-spans.md}
 * from {@code open-telemetry/semantic-conventions} now returns a 396-byte pointer rather
 * than content: the GenAI conventions live in their own repository,
 * {@code https://github.com/open-telemetry/semantic-conventions-genai}, where the attribute
 * registry sits under {@code docs/registry/} and the pages under {@code docs/gen-ai/}.
 * Anything still linking to the old paths — this comment included, until this edit — points
 * at a page that is no longer maintained.
 *
 * <p>Verification is therefore an open task, not an assumption. Before v0.1 ships, each key
 * must be checked against:
 * <ul>
 *   <li>{@code https://github.com/open-telemetry/semantic-conventions-genai} —
 *       {@code docs/registry/attributes/gen-ai.md} for the attribute names and their types</li>
 *   <li>the same repository's span pages for naming rules and required attributes</li>
 * </ul>
 * and any mismatch fixed here. {@link GenAiAttributesTest} pins the current strings, so a
 * correction shows up as a visible diff rather than as silent drift.
 *
 * <p>Why this module exists at all: the project's stated role is to be a <em>consumer</em>
 * of these conventions, never an inventor of its own. Centralising the keys means a
 * spec rename is one file to edit instead of a grep across every instrumentation module.
 */
public final class GenAiAttributes {

    // --- who and what -----------------------------------------------------------------

    /**
     * The AI system, e.g. {@code openai}, {@code anthropic}.
     *
     * @deprecated superseded by {@link #PROVIDER_NAME}. Retained only so we can recognise
     *             traces produced by older instrumentation; new code must use the new key.
     */
    @Deprecated
    public static final String SYSTEM = "gen_ai.system";

    /** Replacement for {@link #SYSTEM}. */
    public static final String PROVIDER_NAME = "gen_ai.provider.name";

    /**
     * The operation being performed. Allowed values live in {@link SpanName} (they are
     * literally the span name prefixes, so keeping them in two places invites drift).
     */
    public static final String OPERATION_NAME = "gen_ai.operation.name";

    // --- request ----------------------------------------------------------------------

    public static final String REQUEST_MODEL = "gen_ai.request.model";
    public static final String REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";
    public static final String REQUEST_TEMPERATURE = "gen_ai.request.temperature";
    public static final String REQUEST_TOP_P = "gen_ai.request.top_p";
    public static final String REQUEST_FREQUENCY_PENALTY = "gen_ai.request.frequency_penalty";
    public static final String REQUEST_PRESENCE_PENALTY = "gen_ai.request.presence_penalty";
    public static final String REQUEST_STOP_SEQUENCES = "gen_ai.request.stop_sequences";
    public static final String REQUEST_CHOICE_COUNT = "gen_ai.request.choice.count";
    public static final String REQUEST_SEED = "gen_ai.request.seed";
    public static final String REQUEST_ENCODING_FORMATS = "gen_ai.request.encoding_formats";

    // --- response ---------------------------------------------------------------------

    public static final String RESPONSE_ID = "gen_ai.response.id";
    public static final String RESPONSE_MODEL = "gen_ai.response.model";
    public static final String RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons";

    // --- usage ------------------------------------------------------------------------

    public static final String USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    // --- tools ------------------------------------------------------------------------

    public static final String TOOL_NAME = "gen_ai.tool.name";
    public static final String TOOL_CALL_ID = "gen_ai.tool.call.id";
    public static final String TOOL_TYPE = "gen_ai.tool.type";
    public static final String TOOL_DESCRIPTION = "gen_ai.tool.description";

    // --- agents -----------------------------------------------------------------------

    public static final String AGENT_ID = "gen_ai.agent.id";
    public static final String AGENT_NAME = "gen_ai.agent.name";
    public static final String AGENT_DESCRIPTION = "gen_ai.agent.description";

    // --- session and data -------------------------------------------------------------

    public static final String CONVERSATION_ID = "gen_ai.conversation.id";
    public static final String DATA_SOURCE_ID = "gen_ai.data_source.id";

    // --- classification ---------------------------------------------------------------

    /** The output media type the request asked for, e.g. {@code text}, {@code json}. */
    public static final String OUTPUT_TYPE = "gen_ai.output.type";

    /** Which token bucket a count belongs to, e.g. {@code input}, {@code output}. */
    public static final String TOKEN_TYPE = "gen_ai.token.type";

    // --- general conventions we always need on a GenAI span ---------------------------

    /** Low-cardinality error classification. Present only on failed spans. */
    public static final String ERROR_TYPE = "error.type";

    public static final String SERVER_ADDRESS = "server.address";
    public static final String SERVER_PORT = "server.port";

    private GenAiAttributes() {
    }
}
