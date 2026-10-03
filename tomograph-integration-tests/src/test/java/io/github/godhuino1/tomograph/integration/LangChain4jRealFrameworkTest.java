package io.github.godhuino1.tomograph.integration;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.TomographSpan;
import io.github.godhuino1.tomograph.core.InstrumentationEngine;
import io.github.godhuino1.tomograph.instrumentation.langchain4j.LangChain4jModule;
import io.github.godhuino1.tomograph.semconv.GenAiAttributes;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test that cannot be faked: the module, against the real framework.
 *
 * <h2>What only this test can prove</h2>
 *
 * <p>Everything else about the LangChain4j module is covered by fakes that copy the framework's
 * shape, and those fakes agree with whatever the module believes. This test is the one that checks
 * the beliefs themselves:
 *
 * <ul>
 *   <li>the cut-point descriptor matches a class really compiled against LangChain4j — if the
 *       framework ever moves {@code doChat} or changes its parameters, nothing is instrumented and
 *       every other test still passes;</li>
 *   <li>the reflectively-read accessor names ({@code modelName}, {@code tokenUsage},
 *       {@code inputTokenCount}, {@code finishReason}, {@code provider}, ...) still exist. ADR 0008
 *       lists this as the cost of decoupling the injected bytecode from the framework, and this test
 *       is where that cost is paid down.</li>
 * </ul>
 *
 * <p>It needs no provider module: the model under test is written here. That keeps the test to one
 * artefact, no API key and no network.
 *
 * <h2>Written against the source, not against a compiler</h2>
 *
 * <p>This module cannot be compiled in the environment it was written in, because the jar is not
 * downloadable there. Every API call below was checked against the framework's source (2026-10), but
 * a small compile error on first run is a realistic outcome and would not mean the design is wrong —
 * it would mean a builder overload differs from what the source suggested. What would be meaningful
 * is a <em>failing assertion</em>: that is the framework having moved.
 */
class LangChain4jRealFrameworkTest {

    /**
     * A real {@link ChatModel} implementation.
     *
     * <p>It overrides exactly one method, which is the point the module relies on: {@code doChat} is
     * the innermost funnel and the only thing an implementation must provide. {@code chat(String)} is
     * inherited, so the delegation in the framework is the framework's own code rather than a
     * hand-written imitation of it.
     */
    public static class TestChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("hello from the test"))
                    .id("resp-real-1")
                    .modelName("gpt-4o-mini")
                    .tokenUsage(new TokenUsage(120, 45))
                    .finishReason(FinishReason.STOP)
                    .build();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPENAI;
        }
    }

    private final List<TomographSpan> emitted = new ArrayList<>();

    @Test
    void aRealModelCallProducesASpanWithTheVerifiedAttributes() throws Exception {
        InstrumentationEngine engine = new InstrumentationEngine(
                List.of(new LangChain4jModule()), emitted::add, AgentOptions.empty());

        // No class name is registered anywhere: the engine has to recognise the class by the method
        // it declares, which is the whole reason signature routing exists.
        assertEquals(0, engine.watchedClassCount());
        assertEquals(1, engine.watchedMethodCount());

        String className = TestChatModel.class.getName();
        byte[] rewritten = engine.transform(
                null, className.replace('.', '/'), null, null, bytesOf(TestChatModel.class));
        assertNotNull(rewritten,
                "the engine did not instrument a real ChatModel implementation, so either the "
                        + "cut-point descriptor no longer matches the framework or signature routing broke");

        Class<?> instrumented = new ParentLastLoader(Map.of(className, rewritten)).loadClass(className);
        Object model = instrumented.getDeclaredConstructor().newInstance();

        // The framework's own inherited convenience method, not a copy of it. If the delegation chain
        // ever gains another layer that the module also cuts, this is where two spans would appear.
        Object reply = instrumented.getMethod("chat", String.class).invoke(model, "hi");

        assertEquals("hello from the test", reply);
        assertEquals(1, emitted.size(),
                "one model call must produce exactly one span, got " + emitted.size());

        TomographSpan span = emitted.get(0);
        Map<String, Object> attributes = span.attributes();

        // Note where the model name comes from. The inherited chat(String) builds a request with no
        // model on it, and this model's defaultRequestParameters() is empty, so request.modelName()
        // is null and the name falls back to the response's. That fallback is why the span is called
        // "chat gpt-4o-mini" rather than "chat", and a reader who does not know it would chase the
        // wrong thing when this assertion fails.
        assertEquals("chat gpt-4o-mini", span.name());
        assertEquals(TomographSpan.Kind.LLM_CALL, span.kind());
        assertTrue(span.durationNanos() > 0, "the call took measurable time and the span should say so");

        assertEquals("chat", attributes.get(GenAiAttributes.OPERATION_NAME));
        assertEquals("openai", attributes.get(GenAiAttributes.PROVIDER_NAME),
                "read reflectively off the real ModelProvider enum");
        assertEquals(120, attributes.get(GenAiAttributes.USAGE_INPUT_TOKENS),
                "read reflectively off the real TokenUsage");
        assertEquals(45, attributes.get(GenAiAttributes.USAGE_OUTPUT_TOKENS));
        assertEquals("resp-real-1", attributes.get(GenAiAttributes.RESPONSE_ID));
        assertEquals("gpt-4o-mini", attributes.get(GenAiAttributes.RESPONSE_MODEL));
        assertEquals("stop", attributes.get(GenAiAttributes.RESPONSE_FINISH_REASONS));
        assertEquals("langchain4j", attributes.get(io.github.godhuino1.tomograph.semconv.TomographAttributes.MODULE));
    }

    /**
     * Parent-last for the class under test: the same class is on the test classpath uninstrumented,
     * so ordinary delegation would return the original and the test would pass while proving nothing.
     */
    private static final class ParentLastLoader extends ClassLoader {

        private final Map<String, byte[]> definitions;

        ParentLastLoader(Map<String, byte[]> definitions) {
            super(LangChain4jRealFrameworkTest.class.getClassLoader());
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

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource);
            return in.readAllBytes();
        }
    }
}
