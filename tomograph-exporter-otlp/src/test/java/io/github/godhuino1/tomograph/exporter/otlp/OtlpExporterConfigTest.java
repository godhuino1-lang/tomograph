package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.AgentOptions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoint resolution has a four-level precedence chain that is easy to get subtly wrong
 * and produces failures that look like "the backend is down" (a 404, or spans quietly
 * going to the wrong place). So every level is pinned here.
 */
class OtlpExporterConfigTest {

    @Test
    void commandLineOptionsOutrankEnvironmentVariables() {
        OtlpExporterConfig config = OtlpExporterConfig.resolve(
                AgentOptions.parse("otlp.endpoint=http://from-flag/v1/traces"),
                Map.of("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://from-env/v1/traces"));

        assertEquals("http://from-flag/v1/traces", config.tracesEndpoint().toString());
    }

    @Test
    void signalSpecificEndpointIsUsedVerbatim() {
        // Spec rule: OTEL_EXPORTER_OTLP_TRACES_ENDPOINT is a complete URL. Appending
        // /v1/traces to it breaks gateways that route on a different path.
        OtlpExporterConfig config = OtlpExporterConfig.resolve(
                AgentOptions.empty(),
                Map.of("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://collector/custom/path"));

        assertEquals("http://collector/custom/path", config.tracesEndpoint().toString());
    }

    @Test
    void baseEndpointGetsTheTracesPathAppended() {
        OtlpExporterConfig config = OtlpExporterConfig.resolve(
                AgentOptions.empty(),
                Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318"));

        assertEquals("http://collector:4318/v1/traces", config.tracesEndpoint().toString());
    }

    @Test
    void signalSpecificEndpointBeatsTheBaseEndpoint() {
        OtlpExporterConfig config = OtlpExporterConfig.resolve(
                AgentOptions.empty(),
                Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://base:4318",
                        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://specific:4318/v1/traces"));

        assertEquals("http://specific:4318/v1/traces", config.tracesEndpoint().toString());
    }

    @Test
    void fallsBackToTheOtlpHttpDefault() {
        OtlpExporterConfig config = OtlpExporterConfig.resolve(AgentOptions.empty(), Map.of());

        assertEquals("http://localhost:4318/v1/traces", config.tracesEndpoint().toString());
        assertEquals("unknown_service:java", config.serviceName());
    }

    @Test
    void serviceNamePrecedenceMatchesTheSpec() {
        assertEquals("from-env", OtlpExporterConfig
                .resolve(AgentOptions.empty(), Map.of("OTEL_SERVICE_NAME", "from-env")).serviceName());

        assertEquals("from-flag", OtlpExporterConfig
                .resolve(AgentOptions.parse("service.name=from-flag"),
                        Map.of("OTEL_SERVICE_NAME", "from-env")).serviceName());
    }

    @Test
    void parsesHeaderListsAndSkipsMalformedPairs() {
        OtlpExporterConfig config = OtlpExporterConfig.resolve(AgentOptions.empty(), Map.of(
                "OTEL_EXPORTER_OTLP_HEADERS", "authorization=Bearer abc,x-tenant=acme,broken,=novalue,noeq=",
                "OTEL_EXPORTER_OTLP_TRACES_HEADERS", "x-tenant=override"));

        assertEquals("Bearer abc", config.headers().get("authorization"));
        // Signal-specific headers win over generic ones.
        assertEquals("override", config.headers().get("x-tenant"));
        assertFalse(config.headers().containsKey("broken"));
        assertFalse(config.headers().containsKey(""));
    }

    @Test
    void toStringNeverRevealsHeaderValues() {
        OtlpExporterConfig config = OtlpExporterConfig.resolve(AgentOptions.empty(),
                Map.of("OTEL_EXPORTER_OTLP_HEADERS", "authorization=super-secret-token"));

        String rendered = config.toString();

        assertTrue(rendered.contains("authorization"), rendered);
        assertFalse(rendered.contains("super-secret-token"), rendered);
    }

    @Test
    void rejectsAnUnparseableEndpointWithAClearMessage() {
        // Better to fail loudly at startup with the offending value than to spend an hour
        // wondering why nothing arrives.
        IllegalArgumentException failure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OtlpExporterConfig.resolve(AgentOptions.parse("otlp.endpoint=http://bad host/x"), Map.of()));
        assertTrue(failure.getMessage().contains("bad host"), failure.getMessage());
    }
}
