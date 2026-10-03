package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.TomographSpan;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks which span is currently open, so a nested model call can be linked to the
 * agent run that triggered it.
 *
 * <h2>This class is known to be wrong, and that is recorded on purpose</h2>
 *
 * The implementation below is a {@link ThreadLocal} stack. It works for the simple
 * case — one agent run per thread, synchronous calls — which is exactly why it is
 * here in the skeleton. It <b>silently produces broken traces</b> in all of these
 * realistic cases:
 *
 * <ul>
 *   <li><b>Virtual threads.</b> A virtual thread can be unmounted mid-call and
 *       resumed on a different carrier thread. Thread-local state then no longer
 *       corresponds to the call stack it was written for.</li>
 *   <li><b>{@code @Async} / {@code CompletableFuture}.</b> Handing work to another
 *       thread drops the ambient context; the nested spans simply lose their parent.</li>
 *   <li><b>Reactive boundaries.</b> Reactor operators may switch threads between
 *       {@code onNext} calls, so a stack built on one thread is popped on another.</li>
 *   <li><b>Pooled threads.</b> A thread reused for a second, unrelated agent run can
 *       still carry the first run's stack if an exception skipped a {@code pop}.</li>
 * </ul>
 *
 * <h2>The fix (required before v1.0, tracked in ROADMAP.md)</h2>
 *
 * Do not rely on ambient thread state. Capture an explicit context snapshot at the
 * instrumented call site — the moment we rewrite a method, we know the lexical
 * nesting — and pass that snapshot into the nested call as an argument or as a
 * field on a per-run context object. In other words: <b>make the parent-child
 * relationship a value we carry, not a property of the thread we happen to be on.</b>
 *
 * <p>This is "hard problem 2" in ARCHITECTURE.md. It is a real piece of engineering,
 * not a detail to paper over.
 */
public final class SpanCollector {

    private static final ThreadLocal<Deque<TomographSpan>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private SpanCollector() {
    }

    /** Opens a span. Callers are responsible for pairing this with {@link #pop()}. */
    public static void push(TomographSpan span) {
        if (span != null) {
            STACK.get().push(span);
        }
    }

    /** The innermost open span, or {@code null} if none. */
    public static TomographSpan current() {
        return STACK.get().peek();
    }

    /** Closes the innermost span and returns it, or returns {@code null} if none was open. */
    public static TomographSpan pop() {
        Deque<TomographSpan> stack = STACK.get();
        TomographSpan span = stack.poll();
        if (stack.isEmpty()) {
            // Do not leave an empty deque behind: pooled threads must not leak entries.
            STACK.remove();
        }
        return span;
    }

    /** Drops all state for the current thread. Intended for tests and run boundaries. */
    public static void clear() {
        STACK.remove();
    }

    /** Depth of the current stack. Used by tests to prove push/pop pairing. */
    public static int depth() {
        return STACK.get().size();
    }
}
