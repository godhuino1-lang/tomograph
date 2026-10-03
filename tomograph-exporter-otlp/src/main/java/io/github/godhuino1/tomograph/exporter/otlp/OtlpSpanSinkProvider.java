package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.SpanSinkProvider;
import io.github.godhuino1.tomograph.api.TomographLog;

import java.util.Map;

/**
 * Makes the OTLP exporter discoverable by the agent without the agent depending on it.
 *
 * <p>Declared in {@code META-INF/services/io.github.godhuino1.tomograph.api.SpanSinkProvider},
 * so the shaded agent jar picks it up automatically and {@code tomograph-core} keeps zero
 * knowledge of OTLP.
 *
 * <p>Dormant unless configured. The opt-in conditions are deliberately explicit:
 * <ul>
 *   <li>{@code otlp.enabled=true} on the {@code -javaagent} line</li>
 *   <li>{@code otlp.endpoint=...} or {@code otlp.traces.endpoint=...}</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT} or {@code OTEL_EXPORTER_OTLP_TRACES_ENDPOINT}</li>
 * </ul>
 *
 * <p>Note what is <em>not</em> a condition: nothing about the default endpoint. The
 * exporter does have a sensible default ({@code http://localhost:4318/v1/traces}), but
 * treating "a default exists" as "the user asked for it" would mean any application that
 * happens to have a collector on localhost starts exporting the moment Tomograph is
 * attached. Silence is the right default for a tool that injects itself into somebody
 * else's process.
 */
public final class OtlpSpanSinkProvider implements SpanSinkProvider {

    public static final String ID = "otlp";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SpanSink create(AgentOptions options, String sdkVersion) {
        return create(options, sdkVersion, System.getenv());
    }

    /**
     * Env is a parameter so that "not configured" and "configured" can both be tested
     * without mutating the real process environment, which Java cannot portably do.
     */
    static SpanSink create(AgentOptions options, String sdkVersion, Map<String, String> env) {
        if (!isConfigured(options, env)) {
            return null;
        }
        OtlpExporterConfig config = OtlpExporterConfig.resolve(options, env);
        TomographLog.info("OTLP export enabled -> " + config);
        return OtlpHttpSpanExporter.create(config, sdkVersion);
    }

    static boolean isConfigured(AgentOptions options, Map<String, String> env) {
        AgentOptions opts = (options == null) ? AgentOptions.empty() : options;
        return opts.getBoolean("otlp.enabled", false)
                || isSet(opts.get("otlp.endpoint", null))
                || isSet(opts.get("otlp.traces.endpoint", null))
                || isSet(env.get("OTEL_EXPORTER_OTLP_ENDPOINT"))
                || isSet(env.get("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
