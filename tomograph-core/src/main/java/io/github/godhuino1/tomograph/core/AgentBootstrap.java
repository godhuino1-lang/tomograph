package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.SpanSinkProvider;
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
 *
 * <p>Both extension points are discovered through {@link ServiceLoader} rather than
 * compiled in: instrumentation modules decide <em>what</em> to capture, span sink
 * providers decide <em>where it goes</em>. The core knows neither OTLP nor LangChain4j.
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
            ClassLoader loader = resolveLoader();
            List<TomographModule> modules = loadModules(loader);
            SpanSink sink = resolveSink(options, loader);
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
     * Prefers the thread context loader: in a Spring Boot fat jar the application's own
     * modules and sinks live in a child loader, not in the loader that defined this class.
     */
    private static ClassLoader resolveLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return (loader != null) ? loader : TomographModule.class.getClassLoader();
    }

    private static List<TomographModule> loadModules(ClassLoader loader) {
        List<TomographModule> found = new ArrayList<>();
        for (TomographModule module : ServiceLoader.load(TomographModule.class, loader)) {
            TomographLog.debug("discovered module " + module.id() + " via " + loader);
            found.add(module);
        }
        return List.copyOf(found);
    }

    /**
     * Asks every {@link SpanSinkProvider} on the classpath for a sink and takes the first
     * one that is configured.
     *
     * <p>First-wins is deliberate. Fanning out to several destinations is a v1.0 concern:
     * inventing a merge policy now would risk duplicate or reordered traces, which is a
     * worse failure than having one destination. It also means a provider must answer
     * honestly by returning {@code null} when it is not configured — see
     * {@code OtlpSpanSinkProvider} for why that matters.
     *
     * <p>A provider that throws is skipped rather than fatal. The fallback sink always
     * exists, so the worst possible outcome here is losing telemetry, never losing the
     * host application.
     */
    private static SpanSink resolveSink(AgentOptions options, ClassLoader loader) {
        for (SpanSinkProvider provider : ServiceLoader.load(SpanSinkProvider.class, loader)) {
            try {
                SpanSink sink = provider.create(options, TomographVersion.get());
                if (sink != null) {
                    TomographLog.info("span sink: " + provider.id());
                    registerShutdownFlush(sink);
                    return sink;
                }
                TomographLog.debug("span sink provider " + provider.id() + " is not configured");
            } catch (Throwable t) {
                TomographLog.error("span sink provider " + provider.id()
                        + " failed to start; trying the next one", t);
            }
        }
        TomographLog.info("span sink: stderr (no provider configured)");
        return new StderrSpanSink();
    }

    /**
     * Flushes whatever is still queued when the JVM exits.
     *
     * <p>Without this, buffered spans are simply lost. A sink queues on the hot path and
     * writes from a background thread, so the last batch is usually still in memory when
     * the process ends — and "the last run" is precisely the one somebody is trying to
     * debug. Missing exactly that trace is a uniquely annoying failure.
     *
     * <p>The hook swallows everything. It runs during shutdown, where throwing achieves
     * nothing except a confusing stack trace in somebody else's logs.
     */
    private static void registerShutdownFlush(SpanSink sink) {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    sink.close();
                } catch (Throwable t) {
                    TomographLog.warn("failed to flush spans during shutdown: " + t);
                }
            }, "tomograph-shutdown-flush"));
        } catch (Throwable t) {
            TomographLog.warn("could not register the shutdown flush hook: " + t);
        }
    }

    /**
     * The sink of last resort: every span to stderr, at debug level.
     *
     * <p>It exists so that {@code resolveSink} always has something to return — the agent
     * must never be in a state where captured data has nowhere to go — and so that a run
     * with no provider configured still visibly proves the engine-to-sink path works.
     * Debug level keeps it from flooding a real service's logs.
     */
    private static final class StderrSpanSink implements SpanSink {

        @Override
        public void accept(TomographSpan span) {
            TomographLog.debug("span " + span.kind() + " " + span.name()
                    + " " + span.durationNanos() + "ns attrs=" + span.attributes().keySet());
        }
    }
}
