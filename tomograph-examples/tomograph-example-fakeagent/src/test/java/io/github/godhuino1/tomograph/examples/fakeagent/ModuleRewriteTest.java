package io.github.godhuino1.tomograph.examples.fakeagent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewrite, load, call: the first test in this project that proves a module's rewritten bytes are
 * accepted by the JVM and run.
 *
 * <h2>Why this is not just another unit test</h2>
 *
 * <p>Every other test stops short of the interesting part. The engine's tests assert <em>routing</em>
 * with stub modules that return {@code null}. The end-to-end test asserts that a span arrives over a
 * socket. Neither proves that bytecode produced by a module can be loaded at all — and a rewrite
 * that the verifier rejects does not fail a unit test, it fails in the host application, at
 * class-loading time, as a {@code VerifyError}.
 *
 * <p>So this test does the real thing in miniature: it asks the module for rewritten bytes, defines
 * them with a class loader, instantiates the class, calls the method, and checks both that the
 * injected call ran and that the method's original behaviour is unchanged. The second half matters
 * as much as the first: an instrumentation module that alters results is worse than one that does
 * nothing.
 */
class ModuleRewriteTest {

    private static final String TARGET_CLASS = "io.github.godhuino1.tomograph.examples.fakeagent.FakeChatModel";

    /**
     * Loads the rewritten class while delegating everything else to the parent.
     *
     * <p>Parent-last <em>only</em> for the names it has bytes for - and it must be parent-last,
     * because the unrewritten {@code FakeChatModel} is on the test classpath too, so ordinary
     * delegation would quietly hand back the original class and the test would pass while proving
     * nothing.
     */
    private static final class DefiningLoader extends ClassLoader {

        private final Map<String, byte[]> definitions;

        DefiningLoader(Map<String, byte[]> definitions) {
            super(ModuleRewriteTest.class.getClassLoader());
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

    @Test
    void theRewrittenClassLoadsRunsTheInjectedCallAndBehavesIdentically() throws Exception {
        int entriesBefore = ExampleProbe.entries();
        byte[] original = classBytes(FakeChatModel.class);

        FakeChatModelModule module = new FakeChatModelModule();
        byte[] rewritten = module.instrument(null, TARGET_CLASS.replace('.', '/'), original);

        assertNotNull(rewritten, "the module returned null, so nothing was rewritten");
        assertTrue(rewritten.length > original.length,
                "the rewritten class should be larger than the original: it gained an invocation");

        Class<?> instrumented = new DefiningLoader(Map.of(TARGET_CLASS, rewritten)).loadClass(TARGET_CLASS);
        Object instance = instrumented.getDeclaredConstructor().newInstance();
        Object reply = invokeChat(instrumented, instance, "hello");

        assertEquals("echo(fake-model-v1): hello", reply,
                "instrumentation must not change what the host method returns");
        assertEquals(entriesBefore + 1, ExampleProbe.entries(),
                "the injected call never ran, so either the rewrite or the loading of it failed");
    }

    @Test
    void theSameMethodIsStillCallableWithTheSameBehaviourOnASecondCall() throws Exception {
        byte[] rewritten = new FakeChatModelModule()
                .instrument(null, TARGET_CLASS.replace('.', '/'), classBytes(FakeChatModel.class));
        Class<?> instrumented = new DefiningLoader(Map.of(TARGET_CLASS, rewritten)).loadClass(TARGET_CLASS);
        Object instance = instrumented.getDeclaredConstructor().newInstance();

        assertEquals("echo(fake-model-v1): a", invokeChat(instrumented, instance, "a"));
        assertEquals("echo(fake-model-v1): b", invokeChat(instrumented, instance, "b"));
    }

    @Test
    void theExceptionPathIsUntouched() throws Exception {
        // chat(String) rejects a blank prompt. The rewrite inserts a call at entry, which must not
        // interfere with the method's own validation.
        byte[] rewritten = new FakeChatModelModule()
                .instrument(null, TARGET_CLASS.replace('.', '/'), classBytes(FakeChatModel.class));
        Class<?> instrumented = new DefiningLoader(Map.of(TARGET_CLASS, rewritten)).loadClass(TARGET_CLASS);
        Object instance = instrumented.getDeclaredConstructor().newInstance();

        InvocationTargetException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                InvocationTargetException.class,
                () -> instrumented.getMethod("chat", String.class).invoke(instance, "  "));
        assertTrue(thrown.getCause() instanceof IllegalArgumentException,
                "unexpected exception: " + thrown.getCause());
    }

    @Test
    void aClassThatIsNotTheTargetIsLeftAlone() {
        FakeChatModelModule module = new FakeChatModelModule();

        // No rewriting, no span, no work: the module only ever claims one class.
        assertNull(module.instrument(null, "java/lang/String", new byte[] {1, 2, 3}));
    }

    private static Object invokeChat(Class<?> type, Object instance, String prompt) throws Exception {
        return type.getMethod("chat", String.class).invoke(instance, prompt);
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource);
            return in.readAllBytes();
        }
    }
}
