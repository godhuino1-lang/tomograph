package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.AgentOptions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where to send spans, resolved the way the OpenTelemetry specification says to resolve it.
 *
 * <p>Precedence, highest first:
 * <ol>
 *   <li>agent options on the command line — {@code -javaagent:tomograph-agent.jar=otlp.endpoint=...}.
 *       The person who typed a flag outranks an environment variable they may not know is set.</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_TRACES_ENDPOINT} — signal-specific, treated as a complete URL.</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT} — base URL, {@code /v1/traces} is appended.</li>
 *   <li>{@code http://localhost:4318/v1/traces} — the OTLP/HTTP default.</li>
 * </ol>
 *
 * <p>The signal-specific endpoint is deliberately not suffixed: the spec says it is used
 * verbatim, because a user who sets it may be pointing at a gateway with a different path
 * layout. Getting this wrong produces 404s that look like "the backend is down".
 */
public final class OtlpExporterConfig {

    static final String DEFAULT_BASE_ENDPOINT = "http://localhost:4318";
    static final String TRACES_PATH = "/v1/traces";
    static final String DEFAULT_SERVICE_NAME = "unknown_service:java";

    private final URI tracesEndpoint;
    private final String serviceName;
    private final Map<String, String> headers;

    OtlpExporterConfig(URI tracesEndpoint, String serviceName, Map<String, String> headers) {
        this.tracesEndpoint = tracesEndpoint;
        this.serviceName = serviceName;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public static OtlpExporterConfig resolve(AgentOptions options) {
        return resolve(options, System.getenv());
    }

    /** Env is a parameter so the precedence rules above can actually be tested. */
    static OtlpExporterConfig resolve(AgentOptions options, Map<String, String> env) {
        AgentOptions opts = (options == null) ? AgentOptions.empty() : options;

        String endpoint = firstNonBlank(
                opts.get("otlp.endpoint", null),
                opts.get("otlp.traces.endpoint", null),
                env.get("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"),
                withTracesPath(env.get("OTEL_EXPORTER_OTLP_ENDPOINT")),
                DEFAULT_BASE_ENDPOINT + TRACES_PATH);

        String serviceName = firstNonBlank(
                opts.get("service.name", null),
                env.get("OTEL_SERVICE_NAME"),
                DEFAULT_SERVICE_NAME);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.putAll(parseHeaders(env.get("OTEL_EXPORTER_OTLP_HEADERS")));
        headers.putAll(parseHeaders(env.get("OTEL_EXPORTER_OTLP_TRACES_HEADERS")));

        return new OtlpExporterConfig(toUri(endpoint), serviceName, headers);
    }

    private static String withTracesPath(String base) {
        return (base == null || base.isBlank()) ? null : base + TRACES_PATH;
    }

    /**
     * Parses the spec's {@code key=value,key2=value2} header list. A malformed pair is
     * skipped rather than fatal: refusing to export at all because of one bad header
     * would be a worse outcome than exporting without it.
     */
    private static Map<String, String> parseHeaders(String raw) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return parsed;
        }
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && eq < pair.length() - 1) {
                parsed.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return parsed;
    }

    private static URI toUri(String endpoint) {
        try {
            return new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid OTLP endpoint: " + endpoint, e);
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    public URI tracesEndpoint() {
        return tracesEndpoint;
    }

    public String serviceName() {
        return serviceName;
    }

    public Map<String, String> headers() {
        return headers;
    }

    /** Never prints header values: they routinely carry API keys. */
    @Override
    public String toString() {
        return "OtlpExporterConfig{endpoint=" + tracesEndpoint
                + ", service=" + serviceName
                + ", headerKeys=" + headers.keySet() + "}";
    }
}
