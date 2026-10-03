package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.SpanSinkProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interesting behaviour here is <b>staying dormant</b>. An exporter that switches
 * itself on because a default endpoint exists would turn every host application that
 * happens to run a collector into an unexpected telemetry producer, which is exactly the
 * kind of surprise an injected agent must never cause.
 */
class OtlpSpanSinkProviderTest {

    private static final String ENDPOINT = "http://127.0.0.1:1/v1/traces";

    @Test
    void staysDormantWhenNothingIsConfigured() {
        assertNull(OtlpSpanSinkProvider.create(AgentOptions.empty(), "test", Map.of()));
        assertNull(OtlpSpanSinkProvider.create(null, "test", Map.of()));
    }

    @Test
    void activatesOnAnExplicitCommandLineEndpoint() {
        assertActivates(AgentOptions.parse("otlp.endpoint=" + ENDPOINT), Map.of());
        assertActivates(AgentOptions.parse("otlp.traces.endpoint=" + ENDPOINT), Map.of());
    }

    @Test
    void activatesOnTheOptInFlagAlone() {
        assertActivates(AgentOptions.parse("otlp.enabled=true"), Map.of());
    }

    @Test
    void activatesOnStandardOtelEnvironmentVariables() {
        assertActivates(AgentOptions.empty(), Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:1"));
        assertActivates(AgentOptions.empty(), Map.of("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", ENDPOINT));
    }

    @Test
    void ignoresBlankConfigurationInsteadOfTreatingItAsOptIn() {
        assertNull(OtlpSpanSinkProvider.create(AgentOptions.parse("otlp.endpoint=   "), "test", Map.of()));
        assertNull(OtlpSpanSinkProvider.create(AgentOptions.empty(), "test",
                Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "")));
    }

    @Test
    void isDiscoverableThroughServiceLoader() {
        // The entire point of this class: the agent finds it without depending on it.
        // A malformed META-INF/services descriptor fails here and nowhere else.
        boolean found = ServiceLoader.load(SpanSinkProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> OtlpSpanSinkProvider.ID.equals(provider.id()));

        assertTrue(found, "META-INF/services descriptor for SpanSinkProvider is missing or malformed");
    }

    private static void assertActivates(AgentOptions options, Map<String, String> env) {
        SpanSink sink = OtlpSpanSinkProvider.create(options, "test", env);
        assertNotNull(sink, "expected the provider to activate for " + options + " / " + env.keySet());
        assertTrue(sink instanceof OtlpHttpSpanExporter);
        // Close it, or the daemon flusher thread and its HTTP client outlive the test.
        sink.close();
    }
}
