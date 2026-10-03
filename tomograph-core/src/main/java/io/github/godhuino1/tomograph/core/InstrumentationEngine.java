package io.github.godhuino1.tomograph.core;

import io.github.godhuino1.tomograph.api.AgentOptions;
import io.github.godhuino1.tomograph.api.MethodCutPoint;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographModule;

import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single {@link ClassFileTransformer} Tomograph registers with the JVM.
 *
 * <p>Its only jobs are routing and damage control:
 * <ol>
 *   <li>Route a loaded class to the modules that declared interest in it. There are two ways to
 *       declare interest, and they cost very different amounts (see below).</li>
 *   <li>Guarantee that no module can break the host application (see {@link #transform}).</li>
 * </ol>
 *
 * <h2>The two routing paths, and why one of them is not free</h2>
 *
 * <p><b>By class name</b> is one hash lookup, and a class nobody declared costs exactly that much.
 *
 * <p><b>By method signature</b> ({@link TomographModule#targetMethods()}) cannot be a lookup: the
 * engine is handed a class whose name it has never seen and must decide whether the class declares
 * a particular method. Parsing every loaded class would be unacceptable, so it is filtered:
 * a method's name always appears in the class's constant pool, so a byte-level search for the name
 * rejects almost every class before any parsing. Only when the name is present does
 * {@link DeclaredMethods} confirm the descriptor.
 *
 * <p>Order matters: the cheap test is first, the exact test second. A false positive from the cheap
 * test costs one parse. There are no false negatives - if the class declares the method, its name is
 * in the pool by construction - and that is what makes the filter sound rather than merely fast.
 *
 * <p>The engine also implements {@link TomographModule.Runtime}, so modules receive
 * the span sink without core having to know anything about them.
 */
public final class InstrumentationEngine implements ClassFileTransformer, TomographModule.Runtime {

    private final Map<String, List<TomographModule>> modulesByClassName;
    private final List<SignatureRoute> signatureRoutes;
    private final Set<String> moduleIds;
    private final SpanSink sink;
    private final AgentOptions options;

    public InstrumentationEngine(List<TomographModule> modules, SpanSink sink, AgentOptions options) {
        this.sink = sink;
        this.options = options;

        Map<String, List<TomographModule>> index = new HashMap<>();
        Map<MethodCutPoint, List<TomographModule>> byCutPoint = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (TomographModule module : modules) {
            ids.add(module.id());
            for (String className : module.targetClassNames()) {
                index.computeIfAbsent(className, key -> new ArrayList<>(2)).add(module);
            }
            for (MethodCutPoint cutPoint : module.targetMethods()) {
                byCutPoint.computeIfAbsent(cutPoint, key -> new ArrayList<>(2)).add(module);
            }
        }
        index.replaceAll((key, value) -> List.copyOf(value));
        this.modulesByClassName = Collections.unmodifiableMap(index);
        this.moduleIds = Collections.unmodifiableSet(ids);

        List<SignatureRoute> routes = new ArrayList<>(byCutPoint.size());
        byCutPoint.forEach((cutPoint, interested) -> routes.add(new SignatureRoute(
                cutPoint, List.copyOf(interested), cutPoint.name().getBytes(StandardCharsets.UTF_8))));
        this.signatureRoutes = List.copyOf(routes);

        // Modules are installed here, by the engine, because the engine is the Runtime they are
        // handed. It used to be the caller's job, and that contract was invisible: the integration
        // test built an engine directly, never called onInstall, and the module silently reported
        // nothing - the probe's sink stayed at its no-op default. A lifecycle step that can be
        // forgotten by the person writing the test that verifies the module is a step in the wrong
        // place.
        //
        // One failing installer does not stop the others: a module that cannot start is a module
        // that produces no data, and the others should still produce theirs.
        for (TomographModule module : modules) {
            try {
                module.onInstall(this);
            } catch (Throwable t) {
                TomographLog.error("module " + module.id() + " failed to install and was skipped", t);
            }
        }
    }

    /** One cut point plus the modules that want it, with the name pre-encoded for the byte search. */
    private static final class SignatureRoute {

        private final MethodCutPoint cutPoint;
        private final List<TomographModule> modules;
        private final byte[] nameUtf8;

        private SignatureRoute(MethodCutPoint cutPoint, List<TomographModule> modules, byte[] nameUtf8) {
            this.cutPoint = cutPoint;
            this.modules = modules;
            this.nameUtf8 = nameUtf8;
        }
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

    /** How many distinct method cut points are being matched by signature. */
    public int watchedMethodCount() {
        return signatureRoutes.size();
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
        List<TomographModule> matched = match(className, classfileBuffer);
        if (matched.isEmpty()) {
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

    /** Which modules want this class, by name, by signature, or both. Never returns duplicates. */
    private List<TomographModule> match(String className, byte[] classfileBuffer) {
        List<TomographModule> byName = modulesByClassName.get(className);

        if (signatureRoutes.isEmpty()) {
            return (byName == null) ? List.of() : byName;
        }

        List<TomographModule> result = null;
        if (byName != null) {
            result = new ArrayList<>(byName);
        }
        for (SignatureRoute route : signatureRoutes) {
            if (!ByteScan.contains(classfileBuffer, route.nameUtf8)) {
                continue;
            }
            if (!DeclaredMethods.declares(classfileBuffer, route.cutPoint.name(), route.cutPoint.descriptor())) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>(2);
            }
            for (TomographModule module : route.modules) {
                // A class can match both ways. Without this check the module would instrument it
                // twice, which for a method-instrumenting module means two spans per call.
                if (!result.contains(module)) {
                    result.add(module);
                }
            }
        }
        return (result == null) ? List.of() : result;
    }

}
