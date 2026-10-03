package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the one class whose bugs reach the host application.
 *
 * <p>{@code InstrumentationEngine} runs on the JVM's class-loading path, inside somebody
 * else's process. Two properties matter and both are asserted here rather than assumed:
 *
 * <ol>
 *   <li><b>Routing is exact.</b> A class nobody declared interest in must reach no module
 *       at all — the engine is a hot path held under a JVM lock, and "call everything and
 *       let modules decide" would turn every class load into N calls.</li>
 *   <li><b>Failure is contained and total.</b> If any module throws, the rewrite for that
 *       class is discarded entirely and the original bytes are used. Half-rewritten classes
 *       are how {@code VerifyError} appears at a call site that has nothing to do with
 *       instrumentation.</li>
 * </ol>
 */
class InstrumentationEngineTest {

    private static final SpanSink NO_OP_SINK = span -> { };
    private static final byte[] ORIGINAL = {1, 2, 3};
    private static final byte[] REWRITTEN = {9, 8, 7};

    private static InstrumentationEngine engine(TomographModule... modules) {
        return new InstrumentationEngine(List.of(modules), NO_OP_SINK, AgentOptions.empty());
    }

    // --- routing ---------------------------------------------------------------------

    @Test
    void leavesClassesNobodyDeclaredInterestInCompletelyAlone() {
        StubModule module = StubModule.declining("m", Set.of("app/Watched"));

        assertNull(engine(module).transform(null, "app/Unwatched", null, null, ORIGINAL));
        assertTrue(module.seen.isEmpty(),
                "the module should never have been called, but saw " + module.seen);
    }

    @Test
    void callsTheModuleForAClassItDeclared() {
        StubModule module = StubModule.declining("m", Set.of("app/Watched"));

        engine(module).transform(null, "app/Watched", null, null, ORIGINAL);

        assertEquals(List.of("app/Watched"), module.seen);
    }

    @Test
    void toleratesANullClassName() {
        StubModule module = StubModule.declining("m", Set.of("app/Watched"));

        assertNull(engine(module).transform(null, null, null, null, ORIGINAL));
        assertTrue(module.seen.isEmpty());
    }

    @Test
    void reportsWhatItIsWatching() {
        InstrumentationEngine engine = engine(
                StubModule.declining("alpha", Set.of("a/A", "a/B")),
                StubModule.declining("beta", Set.of("b/B")));

        assertEquals(3, engine.watchedClassCount());
        assertEquals(Set.of("alpha", "beta"), engine.moduleIds());
    }

    // --- transformation results ------------------------------------------------------

    @Test
    void returnsNullWhenTheModuleDeclinesToChangeTheClass() {
        StubModule module = StubModule.declining("m", Set.of("app/Target"));

        assertNull(engine(module).transform(null, "app/Target", null, null, ORIGINAL),
                "null from a module means 'leave this class alone', which the JVM reads as no transform");
    }

    @Test
    void returnsTheRewrittenBytesOtherwise() {
        StubModule module = StubModule.rewriting("m", Set.of("app/Target"), REWRITTEN);

        assertArrayEquals(REWRITTEN, engine(module).transform(null, "app/Target", null, null, ORIGINAL));
    }

    @Test
    void treatsReturningTheSameArrayAsNoChange() {
        StubModule echo = StubModule.echoing("echo", Set.of("app/Target"));

        assertNull(engine(echo).transform(null, "app/Target", null, null, ORIGINAL),
                "handing back the very same array must not be reported as a transformation");
    }

    @Test
    void chainsModulesForTheSameClassAndFeedsEachOneThePreviousOutput() {
        StubModule first = StubModule.rewriting("first", Set.of("app/Target"), REWRITTEN);
        StubModule second = StubModule.rewriting("second", Set.of("app/Target"), new byte[]{5, 5});

        byte[] result = engine(first, second).transform(null, "app/Target", null, null, ORIGINAL);

        assertArrayEquals(new byte[]{5, 5}, result);
        assertArrayEquals(REWRITTEN, second.lastInput,
                "the second module must see what the first one produced, not the original");
    }

    // --- failure containment ---------------------------------------------------------

    @Test
    void discardsTheEntireRewriteWhenAModuleThrows() {
        StubModule good = StubModule.rewriting("good", Set.of("app/Target"), REWRITTEN);
        StubModule bad = StubModule.failing("bad", Set.of("app/Target"));

        byte[] result = engine(good, bad).transform(null, "app/Target", null, null, ORIGINAL);

        assertNull(result, "one module failing must discard every module's work for this class, "
                + "not just the failing module's part");
        assertEquals(1, good.seen.size(), "the working module should have been tried first");
        assertEquals(1, bad.seen.size());
    }

    @Test
    void containsErrorsAsWellAsExceptions() {
        // Instrumentation code can fail in ways that are Errors rather than Exceptions —
        // StackOverflowError from a recursive type walk, for instance. Both must be contained.
        StubModule bang = StubModule.failingWithError("bang", Set.of("app/Target"));

        assertNull(engine(bang).transform(null, "app/Target", null, null, ORIGINAL));
    }

    @Test
    void keepsServingOtherClassesAfterOneFailure() {
        StubModule flaky = StubModule.failingOn("flaky", Set.of("app/Bad", "app/Good"),
                REWRITTEN, Set.of("app/Bad"));
        InstrumentationEngine engine = engine(flaky);

        assertNull(engine.transform(null, "app/Bad", null, null, ORIGINAL));
        assertArrayEquals(REWRITTEN, engine.transform(null, "app/Good", null, null, ORIGINAL),
                "one class failing must not poison the engine for every later class");
    }

    // --- exposing dependencies to modules --------------------------------------------

    @Test
    void exposesTheSinkAndOptionsItWasBuiltWith() {
        SpanSink sink = span -> { };
        AgentOptions options = AgentOptions.parse("sampleRate=0.5");

        InstrumentationEngine engine = new InstrumentationEngine(List.of(), sink, options);

        assertSame(sink, engine.sink());
        assertSame(options, engine.options());
    }

    // --- test doubles -----------------------------------------------------------------

    /** An {@link Error} on purpose: containment must not be limited to {@link Exception}. */
    private static final class BoomError extends Error {
        BoomError(String message) {
            super(message);
        }
    }

    private static final class StubModule implements TomographModule {

        private final String id;
        private final Set<String> targets;
        private final byte[] replacement;
        private final Set<String> failOn;
        private final boolean failWithError;
        private final boolean echo;
        final List<String> seen = new ArrayList<>();
        byte[] lastInput;

        private StubModule(String id, Set<String> targets, byte[] replacement,
                           Set<String> failOn, boolean failWithError, boolean echo) {
            this.id = id;
            this.targets = targets;
            this.replacement = replacement;
            this.failOn = failOn;
            this.failWithError = failWithError;
            this.echo = echo;
        }

        static StubModule declining(String id, Set<String> targets) {
            return new StubModule(id, targets, null, Set.of(), false, false);
        }

        static StubModule rewriting(String id, Set<String> targets, byte[] replacement) {
            return new StubModule(id, targets, replacement, Set.of(), false, false);
        }

        static StubModule echoing(String id, Set<String> targets) {
            return new StubModule(id, targets, null, Set.of(), false, true);
        }

        static StubModule failing(String id, Set<String> targets) {
            return new StubModule(id, targets, null, targets, false, false);
        }

        static StubModule failingWithError(String id, Set<String> targets) {
            return new StubModule(id, targets, null, targets, true, false);
        }

        static StubModule failingOn(String id, Set<String> targets, byte[] replacement, Set<String> failOn) {
            return new StubModule(id, targets, replacement, failOn, false, false);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Set<String> targetClassNames() {
            return targets;
        }

        @Override
        public byte[] instrument(ClassLoader loader, String internalClassName, byte[] classfileBuffer) {
            seen.add(internalClassName);
            lastInput = classfileBuffer;
            if (failOn.contains(internalClassName)) {
                if (failWithError) {
                    throw new BoomError("simulated error from " + id);
                }
                throw new IllegalStateException("simulated failure in " + id);
            }
            if (echo) {
                return classfileBuffer;
            }
            return replacement;
        }
    }
}
