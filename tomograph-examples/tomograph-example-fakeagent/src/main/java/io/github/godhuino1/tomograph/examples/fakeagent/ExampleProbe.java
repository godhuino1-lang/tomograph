package io.github.godhuino1.tomograph.examples.fakeagent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The method injected code calls into.
 *
 * <p>This class is the proof that a rewrite reached the JVM. It is not referenced by
 * {@link FakeChatModel} in source, and no Java compiler would put a call to it in that class — so a
 * call arriving here can only have been inserted as bytecode and then loaded and executed.
 *
 * <p>It also demonstrates the shape a real instrumentation target needs: a static entry point the
 * injected instructions can call, living on a classpath the <em>host application</em> can see.
 * Modules are application plugins, not contents of the agent jar — which is why the agent can stay
 * free of third-party dependencies (ADR 0003) while a module uses ASM.
 */
public final class ExampleProbe {

    private static final AtomicInteger ENTRIES = new AtomicInteger();

    private ExampleProbe() {
    }

    /** Called on entry to the instrumented method. Must stay cheap and must never throw. */
    public static void entered(String method) {
        ENTRIES.incrementAndGet();
        // stderr, not stdout: an agent and its host share stdout, and a stray line there corrupts
        // JSON output or a shell pipeline. The same rule the agent itself follows.
        System.err.println("[example-probe] entered " + method);
    }

    /** How many times the injected call has run in this JVM. */
    public static int entries() {
        return ENTRIES.get();
    }
}
