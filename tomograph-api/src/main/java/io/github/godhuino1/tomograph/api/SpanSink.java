package io.github.godhuino1.tomograph.api;

/**
 * Where captured spans go.
 *
 * <p>Implementations must never throw and must never block the calling
 * application thread for long: a span sink runs on the host application's hot
 * path. Queue and drop, do not block.
 */
public interface SpanSink extends AutoCloseable {

    void accept(TomographSpan span);

    /** Best-effort flush. Called on JVM shutdown, may be skipped on hard kill. */
    default void flush() {
        // no-op by default
    }

    @Override
    default void close() {
        flush();
    }
}
