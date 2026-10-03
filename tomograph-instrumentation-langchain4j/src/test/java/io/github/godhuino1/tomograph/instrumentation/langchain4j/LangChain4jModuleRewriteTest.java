package io.github.godhuino1.tomograph.instrumentation.langchain4j;

import io.github.godhuino1.tomograph.api.MethodCutPoint;
import io.github.godhuino1.tomograph.api.TomographSpan;
import io.github.godhuino1.tomograph.semconv.GenAiAttributes;
import io.github.godhuino1.tomograph.semconv.TomographAttributes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LangChain4j module, exercised without LangChain4j.
 *
 * <h2>Why fakes are enough here, and what they cannot prove</h2>
 *
 * <p>The fakes below copy the shape that matters: a request with a model name and parameters, a
 * response with an id, a model name, a finish reason and a {@code tokenUsage()} whose own accessors
 * are {@code inputTokenCount()} / {@code outputTokenCount()}, and a model class whose
 * {@code chat(String)} delegates to {@code doChat}. Those names are the interface between the module
 * and the framework, and getting one wrong costs an attribute rather than an error — so they are
 * worth asserting on.
 *
 * <p>What fakes cannot prove is that the <b>real</b> framework still has those accessors at the
 * version actually pinned. That is what the integration test against a real jar is for, and it is
 * why ADR 0008 lists the reflective extraction as a cost rather than a free win.
 */
class LangChain4jModuleRewriteTest {

    // --- fakes shaped like the framework -------------------------------------------------

    public static class FakeRequest {

        public String modelName() {
            return "fake-model";
        }

        public Integer maxOutputTokens() {
            return 256;
        }

        public Double temperature() {
            return 0.7;
        }

        public Integer topK() {
            return 40;
        }

        public List<String> stopSequences() {
            return List.of("STOP", "END");
        }
    }

    public static class FakeTokenUsage {

        public Integer inputTokenCount() {
            return 120;
        }

        public Integer outputTokenCount() {
            return 45;
        }
    }

    public enum FakeFinishReason {
        STOP,
        LENGTH
    }

    public static class FakeResponse {

        public String id() {
            return "resp-1";
        }

        public String modelName() {
            return "fake-model-v2";
        }

        public FakeTokenUsage tokenUsage() {
            return new FakeTokenUsage();
        }

        public FakeFinishReason finishReason() {
            return FakeFinishReason.STOP;
        }
    }

    /**
     * Mirrors {@code ChatModel.provider()}, using the real enum's constant names - including
     * {@code OPEN_AI} rather than {@code OPENAI}, which is what the first version of the integration
     * test guessed and the compiler rejected.
     */
    public enum FakeProvider {
        OPEN_AI,
        AMAZON_BEDROCK,
        OLLAMA,
        OTHER
    }

    /** The cut point: the innermost funnel, exactly as in the real framework. */
    public static class FakeModel {

        public FakeProvider provider() {
            return FakeProvider.OPEN_AI;
        }

        public FakeResponse doChat(FakeRequest request) {
            return new FakeResponse();
        }

        /** The convenience entry point that delegates inward. It must not produce a second span. */
        public String chat(String prompt) {
            return doChat(new FakeRequest()).id();
        }
    }

    // A second family where the response has no token usage at all, to check degradation.

    public static class PlainRequest {

        public String modelName() {
            return "plain-model";
        }
    }

    public static class PlainResponse {

        public String id() {
            return "plain-1";
        }
    }

    public static class PlainModel {

        public PlainResponse doChat(PlainRequest request) {
            return new PlainResponse();
        }
    }

    private static final MethodCutPoint FAKE_CUT_POINT = MethodCutPoint.of(
            "doChat", "(L" + internalName(FakeRequest.class) + ";)L" + internalName(FakeResponse.class) + ";");

    private static final MethodCutPoint PLAIN_CUT_POINT = MethodCutPoint.of(
            "doChat", "(L" + internalName(PlainRequest.class) + ";)L" + internalName(PlainResponse.class) + ";");

    private final List<TomographSpan> emitted = new ArrayList<>();

    @AfterEach
    void detach() {
        LangChain4jProbe.install(null);
    }

    // --- tests ---------------------------------------------------------------------------

    @Test
    void oneModelCallProducesExactlyOneSpanWithTheVerifiedAttributes() throws Exception {
        Class<?> instrumented = instrument(FakeModel.class, FAKE_CUT_POINT);
        Object model = instrumented.getDeclaredConstructor().newInstance();

        Object result = instrumented.getMethod("chat", String.class).invoke(model, "hello");

        assertEquals("resp-1", result, "instrumentation must not change what the call returns");
        assertEquals(1, emitted.size(),
                "the convenience chat(String) delegates to doChat, so cutting only the funnel has to "
                        + "produce one span - cutting an outer method too would produce two, and no "
                        + "error would say so");

        TomographSpan span = emitted.get(0);
        assertEquals("chat fake-model", span.name(), "span name follows the conventions: operation + model");
        assertEquals(TomographSpan.Kind.LLM_CALL, span.kind());

        Map<String, Object> attributes = span.attributes();
        assertEquals("chat", attributes.get(GenAiAttributes.OPERATION_NAME));
        assertEquals("openai", attributes.get(GenAiAttributes.PROVIDER_NAME),
                "required by the conventions, and read off the model instance the injected code loads");
        assertEquals("fake-model", attributes.get(GenAiAttributes.REQUEST_MODEL));
        assertEquals(256, attributes.get(GenAiAttributes.REQUEST_MAX_TOKENS));
        assertEquals(0.7, attributes.get(GenAiAttributes.REQUEST_TEMPERATURE));
        assertEquals(40, attributes.get(GenAiAttributes.REQUEST_TOP_K));
        assertEquals("STOP,END", attributes.get(GenAiAttributes.REQUEST_STOP_SEQUENCES));

        assertEquals("resp-1", attributes.get(GenAiAttributes.RESPONSE_ID));
        assertEquals("fake-model-v2", attributes.get(GenAiAttributes.RESPONSE_MODEL));
        assertEquals("stop", attributes.get(GenAiAttributes.RESPONSE_FINISH_REASONS),
                "the conventions use the provider's lowercase reason");
        assertEquals(120, attributes.get(GenAiAttributes.USAGE_INPUT_TOKENS));
        assertEquals(45, attributes.get(GenAiAttributes.USAGE_OUTPUT_TOKENS));

        assertEquals("langchain4j", attributes.get(TomographAttributes.MODULE));
        assertTrue(String.valueOf(attributes.get(TomographAttributes.CUT_POINT)).startsWith("doChat("),
                "the cut point should be recorded, got: " + attributes.get(TomographAttributes.CUT_POINT));
    }

    @Test
    void theSpanCarriesTheMeasuredDuration() throws Exception {
        Class<?> instrumented = instrument(FakeModel.class, FAKE_CUT_POINT);
        Object model = instrumented.getDeclaredConstructor().newInstance();
        instrumented.getMethod("chat", String.class).invoke(model, "hello");

        TomographSpan span = emitted.get(0);
        assertTrue(span.durationNanos() > 0, "duration was not recorded");
        assertEquals(span.startEpochNanos() + span.durationNanos(), span.endEpochNanos());
    }

    @Test
    void aResponseWithoutTokenUsageStillProducesASpan() throws Exception {
        Class<?> instrumented = instrument(PlainModel.class, PLAIN_CUT_POINT);
        Object model = instrumented.getDeclaredConstructor().newInstance();
        instrumented.getMethod("doChat", PlainRequest.class).invoke(model, new PlainRequest());

        assertEquals(1, emitted.size(), "a missing accessor must cost an attribute, not the whole span");
        Map<String, Object> attributes = emitted.get(0).attributes();
        assertNull(attributes.get(GenAiAttributes.USAGE_INPUT_TOKENS));
        assertNull(attributes.get(GenAiAttributes.RESPONSE_MODEL));
        assertEquals("plain-1", attributes.get(GenAiAttributes.RESPONSE_ID));
        assertEquals("chat plain-model", emitted.get(0).name());
    }

    @Test
    void aClassWithoutTheCutPointIsLeftAlone() throws Exception {
        LangChain4jModule module = new LangChain4jModule(Set.of(FAKE_CUT_POINT));

        // PlainModel declares doChat too, but with a different descriptor. This is the same rule the
        // engine applies, at the module level: the descriptor is part of the identity.
        assertNull(module.instrument(null, internalName(PlainModel.class), bytesOf(PlainModel.class)));
    }

    @Test
    void theModuleRoutesBySignatureAndNamesNoClasses() {
        LangChain4jModule module = new LangChain4jModule();

        assertTrue(module.targetClassNames().isEmpty(),
                "naming classes would miss the application's own implementations, which is the whole "
                        + "reason signature routing exists");
        assertEquals(Set.of(LangChain4jCutPoints.BLOCKING_DO_CHAT), module.targetMethods());
    }

    @Test
    void providerNamesAreMappedToTheConventionsRatherThanLowercased() {
        // The real enum uses underscores and the conventions use dotted lowercase, so "lowercase the
        // name" produced amazon_bedrock where the conventions want aws.bedrock. That is a value no
        // backend recognises, and it was invisible from the module's own tests because nothing here
        // knew what the right answer was - the real enum is what showed it.
        assertEquals("openai", LangChain4jProbe.providerName(FakeProvider.OPEN_AI));
        assertEquals("aws.bedrock", LangChain4jProbe.providerName(FakeProvider.AMAZON_BEDROCK));

        // Providers the conventions do not list contribute nothing rather than something plausible.
        assertNull(LangChain4jProbe.providerName(FakeProvider.OLLAMA));
        assertNull(LangChain4jProbe.providerName(FakeProvider.OTHER));

        assertNull(LangChain4jProbe.providerName(null));
        assertNull(LangChain4jProbe.providerName("not an enum"));
    }

    @Test
    void theProbeNeverThrowsOnHostileInput() {
        // Junk objects with no accessors at all: every lookup misses and nothing propagates, because
        // this runs between an application and its model. The sink is installed explicitly here
        // because this test deliberately goes around the instrument() helper, which does it.
        LangChain4jProbe.install(emitted::add);
        LangChain4jProbe.entered("doChat(junk)");
        LangChain4jProbe.exited(new Object(), "doChat(junk)", new Object(), new Object());

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0).attributes().get(GenAiAttributes.REQUEST_MODEL) == null);
    }

    // --- helpers -------------------------------------------------------------------------

    private Class<?> instrument(Class<?> type, MethodCutPoint cutPoint) throws Exception {
        LangChain4jProbe.install(emitted::add);
        LangChain4jModule module = new LangChain4jModule(Set.of(cutPoint));

        String name = type.getName();
        byte[] rewritten = module.instrument(null, name.replace('.', '/'), bytesOf(type));
        assertNotNull(rewritten, "the module did not rewrite " + name);

        return new ParentLastLoader(Map.of(name, rewritten)).loadClass(name);
    }

    /**
     * Parent-last for the classes it has bytes for. Required, not stylistic: the uninstrumented
     * classes are on the test classpath too, so ordinary delegation would return the original and
     * every assertion here would pass while proving nothing.
     */
    private static final class ParentLastLoader extends ClassLoader {

        private final Map<String, byte[]> definitions;

        ParentLastLoader(Map<String, byte[]> definitions) {
            super(LangChain4jModuleRewriteTest.class.getClassLoader());
            this.definitions = definitions;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = definitions.containsKey(name) ? findClass(name) : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource);
            return in.readAllBytes();
        }
    }
}
