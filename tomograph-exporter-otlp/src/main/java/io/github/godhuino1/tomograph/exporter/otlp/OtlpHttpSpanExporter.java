package io.github.godhuino1.tomograph.exporter.otlp;

import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographSpan;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends spans to an OTLP/HTTP endpoint, from a background thread, without ever blocking
 * or throwing on the host application's threads.
 *
 * <h2>The contract this class exists to honour</h2>
 *
 * <p>{@link SpanSink} runs on the hot path of somebody else's production service. Two
 * rules follow, and they are the whole design:
 *
 * <ol>
 *   <li><b>{@link #accept} never blocks.</b> A bounded queue with drop-on-full. If the
 *       backend is slow, we lose telemetry — never throughput. Silently losing data is
 *       bad, so drops are counted and reported rather than hidden.</li>
 *   <li><b>{@link #accept} never throws.</b> Any failure in exporting is logged and
 *       counted. Observability must never be the reason a service fails.</li>
 * </ol>
 *
 * <h2>Deviations from the specification, recorded rather than left implicit</h2>
 *
 * <p>Both were checked against {@link OtlpSpecRevision#SPEC_FILE}; neither breaks a MUST.
 *
 * <ol>
 *   <li><b>No retries.</b> The specification says requests answered with 429, 502, 503 or 504
 *       SHOULD be retried, and that all other 4xx/5xx MUST NOT be. We retry nothing. Retrying
 *       properly requires a queue that outlives the request, and an unbounded one grows
 *       fastest in precisely the situation where the host is already in trouble. The
 *       specification's other requirement here — that a client record that data was not
 *       delivered — is met: failures are counted in {@link #failedCount()} and logged.</li>
 *   <li><b>No gzip.</b> Servers MUST support gzip and clients MAY use it; we send
 *       uncompressed and never set {@code Content-Encoding}. That costs bandwidth on large
 *       batches, and is the first thing to add if payload size ever becomes a problem.</li>
 * </ol>
 *
 * <p>Also absent: {@code HttpClient.close()}. It exists only from Java 21, and this module
 * targets Java 17.
 */
public final class OtlpHttpSpanExporter implements SpanSink {

    private final OtlpExporterConfig config;
    private final OtlpPayloadBuilder payloadBuilder;
    private final HttpClient http;
    private final BlockingQueue<TomographSpan> queue;
    private final int batchSize;
    private final long flushIntervalMillis;

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong exported = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private final Object drainLock = new Object();
    private volatile boolean closed;
    private final Thread flusher;

    public OtlpHttpSpanExporter(OtlpExporterConfig config,
                                int queueCapacity,
                                int batchSize,
                                long flushIntervalMillis,
                                String sdkVersion) {
        this.config = config;
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.payloadBuilder = new OtlpPayloadBuilder(
                config.serviceName(), "io.github.godhuino1.tomograph", sdkVersion);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Daemon: a forgotten exporter must not keep a JVM alive, and we cannot rely on
        // anybody calling close() inside somebody else's application.
        this.flusher = new Thread(this::flushLoop, "tomograph-otlp-exporter");
        this.flusher.setDaemon(true);
        this.flusher.start();
    }

    public static OtlpHttpSpanExporter create(OtlpExporterConfig config, String sdkVersion) {
        return new OtlpHttpSpanExporter(config, 4096, 512, 1000L, sdkVersion);
    }

    @Override
    public void accept(TomographSpan span) {
        if (span == null || closed) {
            return;
        }
        if (queue.offer(span)) {
            accepted.incrementAndGet();
        } else {
            dropped.incrementAndGet();
        }
    }

    /** Drains everything currently queued. Called by the background loop and at shutdown. */
    @Override
    public void flush() {
        drain();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        flusher.interrupt();
        drain();
    }

    private void flushLoop() {
        while (!closed) {
            try {
                Thread.sleep(flushIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                drain();
            } catch (Throwable t) {
                TomographLog.error("OTLP flusher hit an unexpected error", t);
            }
        }
    }

    private void drain() {
        synchronized (drainLock) {
            List<TomographSpan> batch = new ArrayList<>(batchSize);
            while (batch.size() < batchSize) {
                TomographSpan span = queue.poll();
                if (span == null) {
                    break;
                }
                batch.add(span);
            }
            if (!batch.isEmpty()) {
                send(batch);
            }
        }
    }

    private void send(List<TomographSpan> batch) {
        String body = payloadBuilder.build(batch);

        HttpRequest.Builder request = HttpRequest.newBuilder(config.tracesEndpoint())
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        config.headers().forEach(request::header);

        try {
            HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                exported.addAndGet(batch.size());
            } else {
                failed.addAndGet(batch.size());
                TomographLog.warn("OTLP endpoint returned HTTP " + status + "; dropped "
                        + batch.size() + " span(s)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed.addAndGet(batch.size());
        } catch (Throwable t) {
            failed.addAndGet(batch.size());
            TomographLog.warn("OTLP export failed (" + t.getClass().getSimpleName()
                    + "); dropped " + batch.size() + " span(s)");
        }
    }

    public long acceptedCount() {
        return accepted.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long exportedCount() {
        return exported.get();
    }

    public long failedCount() {
        return failed.get();
    }
}
