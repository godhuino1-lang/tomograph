package io.github.godhuino1.tomograph.exporter.otlp;

import com.sun.net.httpserver.HttpServer;
import io.github.godhuino1.tomograph.api.TomographSpan;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips through a real HTTP server, using only the JDK's own
 * {@code com.sun.net.httpserver} — no mocking framework, no test container.
 *
 * <p>These are the tests that matter most for this class, because its contract is about
 * behaviour under failure rather than about producing a string correctly: the agent must
 * keep the host application running even when the telemetry backend is unreachable.
 */
class OtlpHttpSpanExporterTest {

    @Test
    void postsThePayloadToTheTracesPathWithJsonContentType() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedCustomHeader = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedCustomHeader.set(exchange.getRequestHeaders().getFirst("x-tenant"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            OtlpExporterConfig config = new OtlpExporterConfig(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces"),
                    "test-service",
                    Map.of("x-tenant", "acme"));

            try (OtlpHttpSpanExporter exporter = new OtlpHttpSpanExporter(config, 16, 8, 3_600_000L, "test")) {
                exporter.accept(sampleSpan());
                exporter.flush();

                assertEquals(1L, exporter.acceptedCount());
                assertEquals(1L, exporter.exportedCount());
                assertEquals(0L, exporter.failedCount());
                assertEquals(0L, exporter.droppedCount());
            }

            assertEquals("/v1/traces", receivedPath.get());
            assertTrue(receivedContentType.get().startsWith("application/json"), receivedContentType.get());
            assertEquals("acme", receivedCustomHeader.get());

            String body = receivedBody.get();
            assertNotNull(body);
            assertTrue(body.contains("\"service.name\""), body);
            assertTrue(body.contains("\"stringValue\":\"test-service\""), body);
            assertTrue(body.contains("\"name\":\"chat gpt-4o\""), body);
            assertTrue(body.contains("\"code\":1"), body);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotThrowWhenTheBackendIsUnreachable() {
        // Discipline #1: observability must never be the reason a service fails.
        OtlpExporterConfig config = new OtlpExporterConfig(
                URI.create("http://127.0.0.1:1/v1/traces"), "test-service", Map.of());

        try (OtlpHttpSpanExporter exporter = new OtlpHttpSpanExporter(config, 16, 8, 3_600_000L, "test")) {
            exporter.accept(sampleSpan());

            exporter.flush(); // must not throw

            assertEquals(1L, exporter.failedCount());
            assertEquals(0L, exporter.exportedCount());
        }
    }

    @Test
    void treatsANonSuccessStatusAsFailureRatherThanSilence() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        try {
            OtlpExporterConfig config = new OtlpExporterConfig(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces"),
                    "test-service", Map.of());

            try (OtlpHttpSpanExporter exporter = new OtlpHttpSpanExporter(config, 16, 8, 3_600_000L, "test")) {
                exporter.accept(sampleSpan());
                exporter.flush();

                assertEquals(1L, exporter.failedCount());
                assertEquals(0L, exporter.exportedCount());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dropsRatherThanBlocksWhenTheQueueIsFull() {
        // The flusher interval is an hour, so nothing drains during this test and the
        // behaviour under a full queue is deterministic.
        OtlpExporterConfig config = new OtlpExporterConfig(
                URI.create("http://127.0.0.1:1/v1/traces"), "test-service", Map.of());

        try (OtlpHttpSpanExporter exporter = new OtlpHttpSpanExporter(config, 2, 2, 3_600_000L, "test")) {
            for (int i = 0; i < 5; i++) {
                exporter.accept(sampleSpan());
            }

            assertEquals(2L, exporter.acceptedCount());
            assertEquals(3L, exporter.droppedCount());
        }
    }

    @Test
    void ignoresSpansAndToleratesNullAfterClose() {
        OtlpExporterConfig config = new OtlpExporterConfig(
                URI.create("http://127.0.0.1:1/v1/traces"), "test-service", Map.of());

        OtlpHttpSpanExporter exporter = new OtlpHttpSpanExporter(config, 4, 4, 3_600_000L, "test");
        exporter.accept(null);
        exporter.close();
        exporter.accept(sampleSpan()); // after close: silently ignored, not thrown
        exporter.close();              // idempotent

        assertEquals(0L, exporter.acceptedCount());
    }

    private static TomographSpan sampleSpan() {
        return TomographSpan
                .builder("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7",
                        TomographSpan.Kind.LLM_CALL, "chat gpt-4o")
                .start(1_700_000_000_000_000_000L)
                .duration(1_500_000L)
                .attribute("gen_ai.request.model", "gpt-4o")
                .attribute("gen_ai.usage.input_tokens", 1234)
                .status(TomographSpan.Status.OK, null)
                .build();
    }
}
