package io.github.godhuino1.tomograph.semconv;

/**
 * Which revision of the GenAI semantic conventions this project claims to follow.
 *
 * <p>This class exists because of how the previous "to be verified" note failed. The
 * attribute keys in {@link GenAiAttributes} carried a comment saying they were unverified
 * and naming two URLs to check. By the time anyone followed it, both URLs had gone stale:
 * the GenAI conventions had been moved out of {@code open-telemetry/semantic-conventions}
 * into their own repository, and the old page had become a 396-byte pointer. A verification
 * task without a target revision does not survive contact with a moving specification.
 *
 * <p>So the target is now recorded as data rather than as prose: the repository, the commit,
 * and the files consulted. The numbers below are what {@link GenAiAttributes} was actually
 * compared against, key by key. A future re-verification starts by changing these values and
 * re-reading the two files named here — and {@code GenAiAttributesTest} will fail if the
 * commit stops looking like a commit, which is a crude guard against the fields rotting.
 */
public final class SemconvRevision {

    /** Where the GenAI conventions live now. The old home is a redirect page. */
    public static final String REPOSITORY = "https://github.com/open-telemetry/semantic-conventions-genai";

    /** The commit the keys in this module were verified against. */
    public static final String VERIFIED_COMMIT = "e07f4ebacb08f56db8c4c882d117720333fbca04";

    /** The date of that commit, so a reader can judge how stale this is at a glance. */
    public static final String VERIFIED_COMMIT_DATE = "2026-10-02";

    /** Authoritative list of every {@code gen_ai.*} attribute and its type. */
    public static final String ATTRIBUTE_REGISTRY_FILE = "model/gen-ai/registry.yaml";

    /** Span names, required attributes and which attributes are sampling-relevant. */
    public static final String SPAN_MODEL_FILE = "model/gen-ai/spans.yaml";

    /** Agent entities, including {@code gen_ai.main_agent}. */
    public static final String ENTITY_MODEL_FILE = "model/gen-ai/entities.yaml";

    private SemconvRevision() {
    }
}
