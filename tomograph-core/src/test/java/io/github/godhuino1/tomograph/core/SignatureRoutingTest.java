package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.MethodCutPoint;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographModule;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing by method signature, tested without the framework that motivated it.
 *
 * <p>The fakes below copy the shape of the real thing - an interface, implementations that declare
 * the funnel method, and a second overload that differs only in return type - because that shape is
 * what the routing rules are about. No LangChain4j jar is needed to test a rule about descriptors,
 * which matters in an environment that cannot fetch one.
 */
class SignatureRoutingTest {

    // --- fakes shaped like the framework ------------------------------------------------

    interface FakeChatRequest {
    }

    interface FakeChatResponse {
    }

    /** Stands in for the reactive publisher: same method name, different return type. */
    interface FakePublisher {
    }

    interface BlockingModel {

        Object doChat(FakeChatRequest request);
    }

    interface ReactiveModel {

        FakePublisher doChat(FakeChatRequest request);
    }

    static final class BlockingModelImpl implements BlockingModel {

        @Override
        public FakeChatResponse doChat(FakeChatRequest request) {
            return null;
        }
    }

    static final class ReactiveModelImpl implements ReactiveModel {

        @Override
        public FakePublisher doChat(FakeChatRequest request) {
            return null;
        }
    }

    /** Something entirely unrelated that happens to have a method with the same name. */
    static final class Unrelated {

        public String doChat(String message) {
            return message;
        }
    }

    private static final String REQUEST = "Lio/github/godhuino1/tomograph/core/SignatureRoutingTest$FakeChatRequest;";

    private static final MethodCutPoint BLOCKING_CUT_POINT =
            MethodCutPoint.of("doChat", "(" + REQUEST + ")Lio/github/godhuino1/tomograph/core/"
                    + "SignatureRoutingTest$FakeChatResponse;");

    private static final MethodCutPoint REACTIVE_CUT_POINT =
            MethodCutPoint.of("doChat", "(" + REQUEST + ")Lio/github/godhuino1/tomograph/core/"
                    + "SignatureRoutingTest$FakePublisher;");

    // --- a module that just records what it was handed -----------------------------------

    private static final class RecordingModule implements TomographModule {

        private final String id;
        private final Set<String> classNames;
        private final Set<MethodCutPoint> cutPoints;
        private final List<String> seen = new ArrayList<>();
        private boolean throwOnEverything;

        RecordingModule(String id, Set<String> classNames, Set<MethodCutPoint> cutPoints) {
            this.id = id;
            this.classNames = classNames;
            this.cutPoints = cutPoints;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Set<String> targetClassNames() {
            return classNames;
        }

        @Override
        public Set<MethodCutPoint> targetMethods() {
            return cutPoints;
        }

        @Override
        public byte[] instrument(ClassLoader loader, String internalClassName, byte[] classfileBuffer)
                throws Exception {
            if (throwOnEverything) {
                throw new IllegalStateException("simulated module failure");
            }
            seen.add(internalClassName);
            return null;
        }
    }

    private static InstrumentationEngine engineFor(TomographModule... modules) {
        return new InstrumentationEngine(List.of(modules), span -> { }, AgentOptions.empty());
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        // Fully qualified, because these fakes are nested classes: the resource is
        // SignatureRoutingTest$BlockingModelImpl.class, not BlockingModelImpl.class.
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertTrue(in != null, "could not read " + resource);
            return in.readAllBytes();
        }
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    // --- the rules -----------------------------------------------------------------------

    @Test
    void aClassIsRoutedBySignatureEvenThoughItsNameWasNeverDeclared() throws Exception {
        // This is the whole point: the module has no idea this class exists, and cannot have one -
        // implementations of a framework interface are open-ended.
        RecordingModule module = new RecordingModule("fake-langchain4j", Set.of(), Set.of(BLOCKING_CUT_POINT));
        InstrumentationEngine engine = engineFor(module);

        Class<?> type = BlockingModelImpl.class;
        engine.transform(null, internalName(type), null, null, bytesOf(type));

        assertEquals(List.of(internalName(type)), module.seen);
        assertEquals(1, engine.watchedMethodCount());
        assertEquals(0, engine.watchedClassCount());
    }

    @Test
    void theSameMethodNameWithADifferentReturnTypeIsNotRouted() throws Exception {
        // The real framework declares doChat(ChatRequest) twice: once returning ChatResponse, once
        // returning a reactive Publisher. Instrumenting the wrong one with code written for the
        // other is a VerifyError in the host application, so the descriptor has to be part of the
        // match - not just the name.
        RecordingModule module = new RecordingModule("fake-langchain4j", Set.of(), Set.of(BLOCKING_CUT_POINT));
        InstrumentationEngine engine = engineFor(module);

        Class<?> reactive = ReactiveModelImpl.class;
        engine.transform(null, internalName(reactive), null, null, bytesOf(reactive));

        assertTrue(module.seen.isEmpty(),
                "the reactive overload must not match the blocking cut point, saw: " + module.seen);
    }

    @Test
    void bothCutPointsCanBeDeclaredAndEachMatchesItsOwnClass() throws Exception {
        RecordingModule module = new RecordingModule("fake-langchain4j", Set.of(),
                Set.of(BLOCKING_CUT_POINT, REACTIVE_CUT_POINT));
        InstrumentationEngine engine = engineFor(module);

        engine.transform(null, internalName(BlockingModelImpl.class), null, null, bytesOf(BlockingModelImpl.class));
        engine.transform(null, internalName(ReactiveModelImpl.class), null, null, bytesOf(ReactiveModelImpl.class));

        assertEquals(2, module.seen.size(), "both implementations should be routed, got " + module.seen);
        assertEquals(2, engine.watchedMethodCount());
    }

    @Test
    void anUnrelatedMethodOfTheSameNameIsNotRouted() throws Exception {
        RecordingModule module = new RecordingModule("fake-langchain4j", Set.of(), Set.of(BLOCKING_CUT_POINT));
        InstrumentationEngine engine = engineFor(module);

        Class<?> unrelated = Unrelated.class;
        engine.transform(null, internalName(unrelated), null, null, bytesOf(unrelated));

        // "doChat" is present in this class's constant pool, so the cheap filter lets it through and
        // the descriptor check rejects it. That is the intended division of labour.
        assertTrue(module.seen.isEmpty(), "got: " + module.seen);
    }

    @Test
    void aClassMatchingBothByNameAndBySignatureReachesTheModuleOnce() throws Exception {
        // Without de-duplication the module would instrument the class twice, which for a
        // method-instrumenting module means two spans per call - a wrong call tree with no error.
        Class<?> type = BlockingModelImpl.class;
        RecordingModule module = new RecordingModule("both", Set.of(internalName(type)),
                Set.of(BLOCKING_CUT_POINT));
        InstrumentationEngine engine = engineFor(module);

        engine.transform(null, internalName(type), null, null, bytesOf(type));

        assertEquals(1, module.seen.size(), "the module was called more than once: " + module.seen);
    }

    @Test
    void aSignatureRoutedModuleThatThrowsLeavesTheClassUntouched() throws Exception {
        RecordingModule module = new RecordingModule("broken", Set.of(), Set.of(BLOCKING_CUT_POINT));
        module.throwOnEverything = true;
        InstrumentationEngine engine = engineFor(module);

        Class<?> type = BlockingModelImpl.class;
        Object result = engine.transform(null, internalName(type), null, null, bytesOf(type));

        assertEquals(null, result, "a failing module must make the engine discard the whole rewrite");
    }

    @Test
    void withNoSignatureCutPointsTheEngineBehavesExactlyAsBefore() throws Exception {
        Class<?> type = BlockingModelImpl.class;
        RecordingModule byName = new RecordingModule("by-name", Set.of(internalName(type)), Set.of());
        InstrumentationEngine engine = engineFor(byName);

        engine.transform(null, internalName(type), null, null, bytesOf(type));
        assertEquals(1, byName.seen.size());
        assertEquals(0, engine.watchedMethodCount());

        RecordingModule other = new RecordingModule("nobody", Set.of(), Set.of());
        InstrumentationEngine otherEngine = engineFor(other);
        otherEngine.transform(null, internalName(type), null, null, bytesOf(type));
        assertTrue(other.seen.isEmpty());
    }
}
