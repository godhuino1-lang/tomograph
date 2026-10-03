package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographModule;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single {@link ClassFileTransformer} Tomograph registers with the JVM.
 *
 * <p>Its only jobs are routing and damage control:
 * <ol>
 *   <li>Route a loaded class to the modules that declared interest in it. The index is
 *       built once at startup, so a class nobody cares about costs <em>one hash lookup</em>.
 *       This is a hot path held under a JVM lock; no linear scans.</li>
 *   <li>Guarantee that no module can break the host application (see
 *       {@link #transform}).</li>
 * </ol>
 *
 * <p>The engine also implements {@link TomographModule.Runtime}, so modules receive
 * the span sink without core having to know anything about them.
 */
public final class InstrumentationEngine implements ClassFileTransformer, TomographModule.Runtime {

    private final Map<String, List<TomographModule>> modulesByClassName;
    private final Set<String> moduleIds;
    private final SpanSink sink;
    private final AgentOptions options;

    public InstrumentationEngine(List<TomographModule> modules, SpanSink sink, AgentOptions options) {
        this.sink = sink;
        this.options = options;

        Map<String, List<TomographModule>> index = new HashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (TomographModule module : modules) {
            ids.add(module.id());
            for (String className : module.targetClassNames()) {
                index.computeIfAbsent(className, key -> new ArrayList<>(2)).add(module);
            }
        }
        index.replaceAll((key, value) -> List.copyOf(value));
        this.modulesByClassName = Collections.unmodifiableMap(index);
        this.moduleIds = Collections.unmodifiableSet(ids);
    }

    @Override
    public SpanSink sink() {
        return sink;
    }

    @Override
    public AgentOptions options() {
        return options;
    }

    public Set<String> moduleIds() {
        return moduleIds;
    }

    public int watchedClassCount() {
        return modulesByClassName.size();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Discipline #1 of this project: instrumentation must never break the host
     * application.</b> If any module throws, or produces bytes the JVM rejects, the
     * result is worse than missing data — a production service that will not start.
     * So a failure anywhere discards the <em>entire</em> rewrite for that class and
     * returns {@code null}, which the JVM reads as "no transformation at all".
     * All-or-nothing is deliberate: half-rewritten classes are how you get
     * {@code VerifyError} at an unrelated call site six hours later.
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if (className == null) {
            return null;
        }
        List<TomographModule> matched = modulesByClassName.get(className);
        if (matched == null) {
            return null;
        }

        byte[] current = classfileBuffer;
        for (TomographModule module : matched) {
            try {
                byte[] rewritten = module.instrument(loader, className, current);
                if (rewritten != null && rewritten != current) {
                    current = rewritten;
                    TomographLog.debug("module " + module.id() + " rewrote " + className
                            + " (" + classfileBuffer.length + " -> " + rewritten.length + " bytes)");
                }
            } catch (Throwable t) {
                TomographLog.error("module " + module.id() + " failed on " + className
                        + "; discarding the whole rewrite for this class", t);
                return null;
            }
        }
        return current == classfileBuffer ? null : current;
    }
}
