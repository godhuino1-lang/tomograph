package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.TomographSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the behaviour of the current {@link SpanCollector} — including the behaviour that is
 * known to be wrong.
 *
 * <p>The thread-isolation test below is deliberate. It documents the exact property that
 * makes this class unsafe for virtual threads and async hand-offs: the stack belongs to a
 * thread, so any work that hops threads loses it. Asserting that today means that when the
 * class is replaced with an explicit carried context (hard problem 2 in ARCHITECTURE.md),
 * the change shows up as a failing test rather than as a quiet reinterpretation.
 */
class SpanCollectorTest {

    @AfterEach
    void clearAfterEachTest() {
        SpanCollector.clear();
    }

    @Test
    void reportsNoCurrentSpanWhenTheStackIsEmpty() {
        assertNull(SpanCollector.current());
        assertEquals(0, SpanCollector.depth());
    }

    @Test
    void popsInLastInFirstOutOrder() {
        TomographSpan outer = span("outer");
        TomographSpan inner = span("inner");

        SpanCollector.push(outer);
        SpanCollector.push(inner);

        assertSame(inner, SpanCollector.current());
        assertEquals(2, SpanCollector.depth());

        assertSame(inner, SpanCollector.pop());
        assertSame(outer, SpanCollector.current());
        assertSame(outer, SpanCollector.pop());
        assertEquals(0, SpanCollector.depth());
    }

    @Test
    void poppingAnEmptyStackReturnsNullRatherThanThrowing() {
        // An unbalanced pop is a bug in instrumentation, but it must never become an
        // exception on the host application's thread.
        assertNull(SpanCollector.pop());
    }

    @Test
    void ignoresNullPushes() {
        SpanCollector.push(null);

        assertEquals(0, SpanCollector.depth());
        assertNull(SpanCollector.current());
    }

    @Test
    void clearDropsEverythingForThisThread() {
        SpanCollector.push(span("a"));
        SpanCollector.push(span("b"));

        SpanCollector.clear();

        assertEquals(0, SpanCollector.depth());
        assertNull(SpanCollector.current());
    }

    @Test
    void keepsStacksSeparatePerThread() throws Exception {
        SpanCollector.push(span("main-thread"));

        // Seeded with a sentinel so that "the other thread saw null" is distinguishable
        // from "the other thread never ran".
        AtomicReference<TomographSpan> seenByOtherThread = new AtomicReference<>(span("sentinel"));
        Thread other = new Thread(() -> seenByOtherThread.set(SpanCollector.current()));
        other.start();
        other.join();

        assertNull(seenByOtherThread.get(),
                "another thread must not see this thread's stack - this is precisely the "
                        + "property that breaks virtual threads and async hand-offs");
        assertNotNull(SpanCollector.current(), "the original thread must still see its own stack");
    }

    private static TomographSpan span(String name) {
        return TomographSpan.builder("trace", "span-" + name, TomographSpan.Kind.INTERNAL, name).build();
    }
}
