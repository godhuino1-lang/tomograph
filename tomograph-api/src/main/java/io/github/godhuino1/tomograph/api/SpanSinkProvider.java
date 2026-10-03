package io.github.godhuino1.tomograph.api;

/**
 * Supplies the sink that captured spans are written to.
 *
 * <p>Discovered with {@link java.util.ServiceLoader}, exactly like
 * {@link TomographModule}, and for the same reason: <b>the agent core must not know that
 * OTLP exists.</b> Adding a destination — an OTLP endpoint, a local recorder for replay,
 * a completely different protocol — is then a new jar on the classpath rather than a
 * change to the engine. That is what keeps the core small enough to still be
 * understandable in year three.
 *
 * <p><b>Returning {@code null} is the normal case, not an error.</b> Shipping an exporter
 * inside the agent jar must never make every host application start POSTing telemetry
 * somewhere. A provider stays dormant until it is explicitly configured, by a
 * {@code -javaagent} option or by the standard {@code OTEL_*} environment variables.
 */
public interface SpanSinkProvider {

    /** Stable identifier used in logs, e.g. {@code otlp}. */
    String id();

    /**
     * @param options    options parsed from the {@code -javaagent} argument string
     * @param sdkVersion the running Tomograph build, attached to exported telemetry so
     *                   that "which build produced this trace?" stays answerable
     * @return a sink, or {@code null} if this provider is not configured for this process
     */
    SpanSink create(AgentOptions options, String sdkVersion);
}
