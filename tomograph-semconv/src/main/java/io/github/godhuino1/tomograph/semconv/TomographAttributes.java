package io.github.godhuino1.tomograph.semconv;

/**
 * Attributes Tomograph adds that no OpenTelemetry convention covers.
 *
 * <p>Every key here is prefixed {@code tomograph.} so that a backend can tell our
 * additions apart from standard ones at a glance, and so that a future spec addition
 * can never silently collide with a name we already emitted.
 *
 * <p>The bar for adding a key here is high. Anything that can be expressed with an
 * existing convention must be, because a custom key is invisible to every generic
 * GenAI dashboard and only works for people who read our docs.
 */
public final class TomographAttributes {

    /** Agent build that produced this span. Answers "which build was this trace from?". */
    public static final String VERSION = "tomograph.version";

    /** Instrumentation module id, e.g. {@code langchain4j}. */
    public static final String MODULE = "tomograph.module";

    /** 1-based step index within an agent run: which iteration of the loop this is. */
    public static final String AGENT_STEP = "tomograph.agent.step";

    /**
     * Estimated cost of this span in USD, computed from token usage and a price table.
     *
     * <p>Always an estimate and never a bill: prices change, discounts exist, and the
     * provider is the only authority. Naming it {@code cost} rather than
     * {@code estimated_cost} would invite someone to reconcile invoices against it.
     */
    public static final String ESTIMATED_COST_USD = "tomograph.cost.usd.estimated";

    /**
     * Hash of the fully rendered prompt.
     *
     * <p>Absent prompts cannot be diffed. With this, "the answer changed" can be split
     * into "the prompt changed" vs "the model changed" without storing the prompt text
     * itself, which is often the whole point of not storing it.
     */
    public static final String PROMPT_HASH = "tomograph.prompt.hash";

    /** Replay state this span was produced in: {@code live}, {@code record} or {@code replay}. */
    public static final String REPLAY_MODE = "tomograph.replay.mode";

    /**
     * Set when attribute values were cut short to bound memory.
     *
     * <p>Silent truncation is worse than no data: it makes a wrong conclusion look
     * well-founded. If we shorten something, we say so on the same span.
     */
    public static final String CAPTURE_TRUNCATED = "tomograph.capture.truncated";

    /** Set when a value was redacted before leaving the process. */
    public static final String CAPTURE_REDACTED = "tomograph.capture.redacted";

    /** Size of the class file bytes the instrumenter was handed. Diagnostics for the agent itself. */
    public static final String CLASSFILE_BYTES = "tomograph.classfile.bytes";

    /**
     * Which cut point produced this span, as {@code methodName + descriptor}.
     *
     * <p>Diagnostics, and worth the bytes: when a span looks wrong, the first question is which rule
     * produced it, and with signature-based routing (ADR 0006) more than one rule can be in play.
     */
    public static final String CUT_POINT = "tomograph.cut_point";

    private TomographAttributes() {
    }
}
