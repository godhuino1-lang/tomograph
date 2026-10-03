package io.github.godhuino1.tomograph.examples.fakeagent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The whole chain, against a real socket: attach the agent to a real JVM, let it instrument a
 * class, and assert that the resulting span arrives at an HTTP endpoint as valid OTLP/JSON.
 *
 * <h2>Why this exists on top of the unit tests</h2>
 *
 * <p>Every other test in this repository checks one link: the encoder against a golden payload,
 * the sink against a fake server, the engine against stub modules. None of them proves the links
 * are connected. This project already learned that lesson once the hard way - the
 * {@code startTimeUnixNano} bug that stamped every span January 1970 passed every unit test,
 * because each test asserted on a timestamp the test itself had supplied. Nothing but a real
 * agent talking to a real receiver could reveal it.
 *
 * <p>So this test asserts on properties that only hold if the links are genuinely joined:
 * a hex trace id, a quoted epoch-nanosecond timestamp, the span name produced by the example
 * module, and the module's own attribute.
 *
 * <p>Unlike {@code DynamicAttachTest}, this one runs everywhere: it needs no attach mechanism,
 * only a child JVM and a loopback socket.
 */
class AgentToOtlpEndToEndTest {

    private static final Path AGENT_JAR = resolveAgentJar();

    @Test
    void spansFromAnAttachedAgentReachARealOtlpEndpoint(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.isRegularFile(AGENT_JAR),
                "agent jar not built yet at " + AGENT_JAR + " - run mvn verify from the repository root");

        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            System.out.println("[e2e] agent jar: " + AGENT_JAR);
            System.out.println("[e2e] receiver listening on 127.0.0.1:" + port);

            Path appLog = tempDir.resolve("app.log");
            Process app = startApp(appLog, port);
            assertTrue(app.waitFor(60, TimeUnit.SECONDS), "the application did not finish in time");
            String appOutput = new String(Files.readAllBytes(appLog), StandardCharsets.UTF_8);

            // The application's own output has to be intact - that is the zero-code-change promise.
            assertTrue(appOutput.contains("[example] done"),
                    "the host application did not finish normally:\n" + appOutput);
            assertTrue(appOutput.contains("tomograph") || appOutput.contains("[example] reply"),
                    "unexpected application output:\n" + appOutput);

            String payload = awaitBody(receivedBody, Duration.ofSeconds(20));
            System.out.println("[e2e] received " + payload.length() + " bytes: " + payload);

            assertTrue(receivedContentType.get().startsWith("application/json"),
                    "wrong content type: " + receivedContentType.get());
            assertOtlpPayloadLooksReal(payload);
        } finally {
            server.stop(0);
        }
    }

    /**
     * The properties asserted here are chosen so that a break anywhere in the chain fails the test:
     * a missing module, a mis-wired sink, a wrong encoder, or a wrong clock.
     */
    private static void assertOtlpPayloadLooksReal(String payload) {
        assertTrue(payload.contains("\"resourceSpans\""), "not an ExportTraceServiceRequest: " + payload);
        assertTrue(payload.contains("\"scopeSpans\""), "missing scopeSpans: " + payload);
        assertTrue(payload.contains("\"service.name\""), "missing the resource's service.name: " + payload);

        assertTrue(payload.matches("(?s).*\"traceId\":\"[0-9a-f]{32}\".*"),
                "traceId must be 32 lowercase hex characters (not base64, not uppercase): " + payload);
        assertTrue(payload.matches("(?s).*\"spanId\":\"[0-9a-f]{16}\".*"),
                "spanId must be 16 lowercase hex characters: " + payload);

        // Quoted, because OTLP/JSON writes 64-bit integers as decimal strings. An unquoted number
        // here is the bug that once stamped every span January 1970 in JavaScript-based backends.
        assertTrue(payload.matches("(?s).*\"startTimeUnixNano\":\"\\d+\".*"),
                "startTimeUnixNano must be a quoted decimal string: " + payload);
        assertTrue(payload.matches("(?s).*\"endTimeUnixNano\":\"\\d+\".*"),
                "endTimeUnixNano must be a quoted decimal string: " + payload);

        // Enums must be integers in OTLP/JSON - names are explicitly forbidden.
        assertTrue(payload.contains("\"kind\":1"), "kind must be the integer 1 (INTERNAL): " + payload);
        assertTrue(payload.contains("\"status\":{\"code\":1}"), "status code must be integer 1 (OK): " + payload);

        // The span the example module emits, and the module's own attribute: these only appear if
        // the ServiceLoader discovery, the engine routing and the sink wiring all worked.
        assertTrue(payload.contains("\"name\":\"instrument io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel\""),
                "the example module's span never made it onto the wire: " + payload);
        assertTrue(payload.contains("tomograph.module") && payload.contains("example-fakeagent"),
                "the module attribute is missing: " + payload);
    }

    private static Process startApp(Path log, int port) throws Exception {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String agentArgument = "-javaagent:" + AGENT_JAR
                + "=otlp.endpoint=http://127.0.0.1:" + port + "/v1/traces";
        return new ProcessBuilder(
                javaBinary,
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                agentArgument,
                "-cp", System.getProperty("java.class.path"),
                Main.class.getName())
                // No pipes, so this also works where pipe creation is restricted.
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static String awaitBody(AtomicReference<String> body, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String received = body.get();
            if (received != null) {
                return received;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("nothing arrived at the OTLP endpoint within "
                + timeout.toSeconds() + "s; the agent ran but exported nothing");
    }

    /** Delegates to the one place that knows where the agent jar is built. */
    private static Path resolveAgentJar() {
        return TestAgentJar.resolve();
    }
}
