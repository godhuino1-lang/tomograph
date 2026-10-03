package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographModule;
import io.github.godhuino1.tomograph.api.TomographSpan;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Entry point shared by both agent attachment modes.
 *
 * <p>Everything here runs inside somebody else's production JVM, at a moment they
 * did not choose. Two rules follow from that:
 *
 * <ol>
 *   <li><b>Never let an exception escape.</b> A throw out of {@code premain} aborts
 *       JVM startup — Tomograph would turn "I wanted observability" into "my service
 *       is down". Everything is wrapped.</li>
 *   <li><b>Say what happened, on stderr.</b> If nothing appears in the logs, the user
 *       cannot tell "no agent ran" from "agent ran and found nothing".</li>
 * </ol>
 */
public final class AgentBootstrap {

    private AgentBootstrap() {
    }

    /** Invoked by the JVM when the jar is passed via {@code -javaagent}. */
    public static void premain(String agentArgs, Instrumentation inst) {
        start("premain", agentArgs, inst);
    }

    /**
     * Invoked when the jar is loaded into an already-running JVM (attach API).
     *
     * <p>Note for the record: on the development machine the attach mechanism itself
     * is unavailable inside the DSH sandbox (named pipes are blocked), so this path is
     * verified on Linux CI rather than locally. See ARCHITECTURE.md.
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        start("agentmain", agentArgs, inst);
    }

    private static void start(String entryPoint, String agentArgs, Instrumentation inst) {
        AgentOptions options = AgentOptions.parse(agentArgs);
        TomographLog.info("Tomograph " + TomographVersion.get()
                + " | entry=" + entryPoint
                + " | jvm=" + System.getProperty("java.version")
                + " | retransformSupported=" + inst.isRetransformClassesSupported()
                + " | " + options);

        try {
            List<TomographModule> modules = loadModules();
            SpanSink sink = new StderrSpanSink();
            InstrumentationEngine engine = new InstrumentationEngine(modules, sink, options);

            for (TomographModule module : modules) {
                module.onInstall(engine);
            }

            boolean canRetransform = options.getBoolean("retransform", true);
            inst.addTransformer(engine, canRetransform);

            TomographLog.info("installed " + modules.size() + " module(s) " + engine.moduleIds()
                    + ", watching " + engine.watchedClassCount() + " class name(s)");
            if (modules.isEmpty()) {
                TomographLog.info("no TomographModule found on the classpath. This is expected in the "
                        + "skeleton: instrumentation modules land in v0.1.");
            }
        } catch (Throwable t) {
            // Discipline #1, again: observability must never be the reason a service fails.
            TomographLog.error("startup failed; the host application is left completely untouched", t);
        }
    }

    /**
     * Discovers modules through {@link ServiceLoader}, preferring the thread context
     * loader because in a Spring Boot fat jar the application's own modules live in
     * a child loader, not in the loader that defined this class.
     */
    private static List<TomographModule> loadModules() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = TomographModule.class.getClassLoader();
        }
        List<TomographModule> found = new ArrayList<>();
        for (TomographModule module : ServiceLoader.load(TomographModule.class, loader)) {
            TomographLog.debug("discovered module " + module.id() + " via " + loader);
            found.add(module);
        }
        return List.copyOf(found);
    }

    /**
     * Placeholder sink used until the OTLP exporter module exists (v0.1).
     *
     * <p>It is deliberately trivial: its purpose in the skeleton is to prove the
     * engine-to-sink path works end to end. It writes only at debug level so that
     * loading the agent on a real service does not flood stderr.
     */
    private static final class StderrSpanSink implements SpanSink {

        @Override
        public void accept(TomographSpan span) {
            TomographLog.debug("span " + span.kind() + " " + span.name()
                    + " " + span.durationNanos() + "ns attrs=" + span.attributes().keySet());
        }
    }
}
