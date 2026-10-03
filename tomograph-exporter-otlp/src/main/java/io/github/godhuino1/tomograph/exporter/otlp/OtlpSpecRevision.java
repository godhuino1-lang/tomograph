package io.github.godhuino1.tomograph.exporter.otlp;

/**
 * Which revision of the OTLP specification the exporter was verified against.
 *
 * <p>Same reasoning as {@code io.github.godhuino1.tomograph.semconv.SemconvRevision}: the
 * keys and the encoding rules in this module were written from working knowledge and marked
 * "to be verified", and by the time anyone checked, the referenced material had moved. A
 * verification task with no target revision does not survive a moving specification, so the
 * target is recorded as data.
 *
 * <p>The check compared the five encoding assumptions listed in {@link OtlpPayloadBuilder}
 * against {@link #SPEC_FILE}. All five held. What the check also produced was a correction to
 * how strict one of them is — OTLP forbids enum name strings outright, where standard proto3
 * JSON mapping permits them — and two deliberate deviations from {@code SHOULD} clauses,
 * recorded in {@link OtlpHttpSpanExporter}.
 */
public final class OtlpSpecRevision {

    public static final String REPOSITORY = "https://github.com/open-telemetry/opentelemetry-proto";

    /** The OTLP release whose specification text was read. */
    public static final String VERIFIED_RELEASE = "1.11.1";

    /** The commit that release was cut from. */
    public static final String VERIFIED_COMMIT = "b3f75588eb23c5fca62264edd05d382de49beb1a";

    public static final String VERIFIED_COMMIT_DATE = "2026-09-29";

    /** Encoding, transport, retry and compression rules; the JSON deviations live here. */
    public static final String SPEC_FILE = "docs/specification.md";

    /** Worked request-body examples, useful when a payload has to be eyeballed by hand. */
    public static final String JSON_EXAMPLES_FILE = "examples/README.md";

    private OtlpSpecRevision() {
    }
}
