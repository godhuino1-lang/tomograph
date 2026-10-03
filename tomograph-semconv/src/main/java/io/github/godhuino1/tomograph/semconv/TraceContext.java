package io.github.godhuino1.tomograph.semconv;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A W3C Trace Context: the trace id, the current span id, and the sampled flag.
 *
 * <p>This lives in {@code tomograph-semconv} rather than in {@code tomograph-core}
 * because the id <em>formats</em> are not ours: they are defined by the W3C Trace Context
 * specification, which OpenTelemetry adopts. Following the project rule, a spec artifact
 * lives in exactly one place, and this is that place.
 *
 * <h2>Why this class exists before any instrumentation does</h2>
 *
 * Without it, instrumented call sites invent their own ids — which is how a trace ends up
 * as a flat list of unrelated points instead of a tree. The tree is the entire product
 * promise, and it is built from two things only: ids that follow one format, and parent
 * links that point at the id above. Getting the format right first is far cheaper than
 * retrofitting it across a dozen instrumentation sites.
 *
 * <h2>Format rules worth stating, because each one is a silent rejection by backends</h2>
 *
 * <ul>
 *   <li>trace id: 16 bytes, written as <b>32 lowercase</b> hex characters.</li>
 *   <li>span id: 8 bytes, written as <b>16 lowercase</b> hex characters.</li>
 *   <li><b>An all-zero id is invalid</b>, for both. It is the spec's "no id" sentinel, so
 *       emitting one produces a span that cannot be linked to anything.</li>
 *   <li>Uppercase hex is not valid in {@code traceparent}. Hex is case-insensitive in most
 *       places, which is exactly why this is easy to get wrong and painful to debug.</li>
 * </ul>
 *
 * <h2>Uniqueness, not unpredictability</h2>
 *
 * Ids come from {@link ThreadLocalRandom}: fast, per-thread, no contention on the hot path.
 * They are <b>not</b> cryptographically unpredictable, and that is a deliberate trade —
 * a trace id is a correlation handle, not an authorization token. If it ever becomes one,
 * this decision has to be revisited rather than assumed.
 *
 * @param traceId 32 lowercase hex characters, never all zero
 * @param spanId  16 lowercase hex characters, never all zero
 * @param sampled whether the sampled flag is set
 */
public record TraceContext(String traceId, String spanId, boolean sampled) {

    /** The only version this project emits. Higher versions are accepted when parsing. */
    public static final String VERSION = "00";

    private static final HexFormat HEX = HexFormat.of();
    private static final int TRACE_ID_LENGTH = 32;
    private static final int SPAN_ID_LENGTH = 16;

    public TraceContext {
        requireValidTraceId(traceId);
        requireValidSpanId(spanId);
    }

    // --- creation ---------------------------------------------------------------------

    /** Starts a new trace with a fresh trace id and span id. Sampled by default. */
    public static TraceContext newRoot() {
        return newRoot(true);
    }

    public static TraceContext newRoot(boolean sampled) {
        return new TraceContext(newTraceId(), newSpanId(), sampled);
    }

    /** The next span down, inside the same trace: new span id, same trace id and flags. */
    public TraceContext childSpan() {
        return new TraceContext(traceId, newSpanId(), sampled);
    }

    public TraceContext withSpanId(String newSpanId) {
        return new TraceContext(traceId, newSpanId, sampled);
    }

    public TraceContext withSampled(boolean newSampled) {
        return new TraceContext(traceId, spanId, newSampled);
    }

    // --- generation -------------------------------------------------------------------

    public static String newTraceId() {
        // Forcing the high half to be non-zero is enough to guarantee the whole id is not
        // all zero, so the low half can be left alone. A retry loop over both halves would
        // be more code for no additional safety.
        long high = nonzeroRandomLong();
        long low = ThreadLocalRandom.current().nextLong();
        return HEX.toHexDigits(high) + HEX.toHexDigits(low);
    }

    public static String newSpanId() {
        return HEX.toHexDigits(nonzeroRandomLong());
    }

    private static long nonzeroRandomLong() {
        long value;
        do {
            value = ThreadLocalRandom.current().nextLong();
        } while (value == 0L);
        return value;
    }

    // --- validation -------------------------------------------------------------------

    public static boolean isValidTraceId(String id) {
        return isLowercaseHex(id, TRACE_ID_LENGTH) && !isAllZero(id);
    }

    public static boolean isValidSpanId(String id) {
        return isLowercaseHex(id, SPAN_ID_LENGTH) && !isAllZero(id);
    }

    private static boolean isLowercaseHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static void requireValidTraceId(String id) {
        if (!isValidTraceId(id)) {
            throw new IllegalArgumentException(
                    "invalid trace id (want 32 lowercase hex characters, not all zero): " + id);
        }
    }

    private static void requireValidSpanId(String id) {
        if (!isValidSpanId(id)) {
            throw new IllegalArgumentException(
                    "invalid span id (want 16 lowercase hex characters, not all zero): " + id);
        }
    }

    // --- propagation ------------------------------------------------------------------

    /** Renders the {@code traceparent} header value, e.g. for an outgoing HTTP call. */
    public String traceparent() {
        return VERSION + "-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
    }

    /**
     * Parses a {@code traceparent} header.
     *
     * <p>Forward compatibility is implemented, not deferred: the specification requires a
     * parser that meets a future version to read the fields it understands and ignore the
     * rest, so an upgraded peer cannot break an older one. Version {@code 00} is the
     * exception — it is a fixed-length format, so trailing fields there are an error rather
     * than something to tolerate.
     *
     * @throws IllegalArgumentException when the value is not a usable traceparent
     */
    public static TraceContext parse(String traceparent) {
        if (traceparent == null) {
            throw new IllegalArgumentException("traceparent must not be null");
        }
        String[] parts = traceparent.split("-", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "traceparent needs at least 4 dash-separated fields: " + traceparent);
        }

        String version = parts[0];
        if (!isLowercaseHex(version, 2) || "ff".equals(version)) {
            throw new IllegalArgumentException("unsupported traceparent version: " + version);
        }
        if (VERSION.equals(version) && parts.length != 4) {
            throw new IllegalArgumentException(
                    "version 00 traceparent must have exactly 4 fields: " + traceparent);
        }
        if (!isLowercaseHex(parts[3], 2)) {
            throw new IllegalArgumentException("trace-flags must be 2 lowercase hex characters: " + parts[3]);
        }

        // The record's own validation rejects malformed or all-zero ids.
        boolean sampled = (Integer.parseInt(parts[3], 16) & 0x01) != 0;
        return new TraceContext(parts[1], parts[2], sampled);
    }

    @Override
    public String toString() {
        return traceparent();
    }
}
