package io.github.godhuino1.tomograph.examples.fakeagent;

import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographModule;
import io.github.godhuino1.tomograph.api.TomographSpan;
import io.github.godhuino1.tomograph.semconv.TraceContext;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * A third-party instrumentation module, written entirely from the outside.
 *
 * <p>It is declared in {@code META-INF/services/io.github.godhuino1.tomograph.api.TomographModule},
 * discovered by {@code ServiceLoader} when the agent starts, and asked to instrument exactly one
 * class. Nothing in the agent knows this module exists.
 *
 * <h2>It rewrites bytecode, and it uses ASM, and the agent still has no dependencies</h2>
 *
 * <p>Those three facts are consistent, and the reason is worth stating because it is the project's
 * deployment model: <b>instrumentation modules are application-classpath plugins, not contents of
 * the agent jar</b>. The module declares ASM as its own dependency, and the application that wants
 * LangChain4j (or, here, this fake model) observed puts both jars on its classpath. The agent jar
 * stays free of third-party code (ADR 0003), which matters because it is injected into somebody
 * else's JVM where a shaded library can collide with the host's own copy.
 *
 * <h2>What the rewrite does, and why it is safe</h2>
 *
 * <p>It inserts one call at the entry of {@code chat(String)}: {@code ExampleProbe.entered("chat")}.
 * Nothing else changes, and in particular the method's result does not. That is the property a
 * regression test asserts by loading the rewritten bytes and calling the method.
 *
 * <p>{@code COMPUTE_MAXS} is enough rather than {@code COMPUTE_FRAMES} because the injected code
 * adds no local variable and no branch target, so the original stack map frames remain valid. The
 * learning exercise teaches the same rule for the same reason; getting it wrong here would put
 * frame recomputation - and therefore class loading - on the critical path of the host.
 */
public final class FakeChatModelModule implements TomographModule {

    /** Internal name, slash-separated — class file format, not source format. */
    private static final String TARGET =
            "io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel";

    /** The one method this module rewrites, identified by name and descriptor. */
    private static final String METHOD_NAME = "chat";
    private static final String METHOD_DESCRIPTOR = "(Ljava/lang/String;)Ljava/lang/String;";

    private static final String PROBE_OWNER =
            "io/github/godhuino1/tomograph/examples/fakeagent/ExampleProbe";

    /** One trace for this module instance; each instrumented class becomes a child span. */
    private final TraceContext trace = TraceContext.newRoot();

    private SpanSink sink = span -> { };

    @Override
    public String id() {
        return "example-fakeagent";
    }

    @Override
    public Set<String> targetClassNames() {
        return Set.of(TARGET);
    }

    @Override
    public void onInstall(Runtime runtime) {
        this.sink = runtime.sink();
        TomographLog.info("module " + id() + " installed, options=" + runtime.options());
    }

    @Override
    public byte[] instrument(ClassLoader loader, String internalClassName, byte[] classfileBuffer) {
        // Checked here even though the engine routes by class name: a module has to be safe on its
        // own terms. Relying on somebody else's routing means that a change to the engine - or a
        // second caller - quietly turns this into a module that rewrites whatever it is handed.
        if (!TARGET.equals(internalClassName)) {
            return null;
        }

        // Rewrite first, report second. If the rewrite throws, the engine discards it and the class
        // loads untouched - and no span is emitted claiming a success that did not happen.
        byte[] rewritten = rewrite(classfileBuffer);

        TomographLog.info("module " + id() + " rewrote " + internalClassName
                + " (" + classfileBuffer.length + " -> " + rewritten.length + " bytes, loader="
                + describe(loader) + ")");

        TraceContext span = trace.childSpan();

        // INTERNAL, not LLM_CALL: this span describes something happening inside the JVM during
        // class loading. Labelling it a model call would put a network hop to an AI provider into
        // a backend's service map - a hop that never happened.
        sink.accept(TomographSpan
                .builder(span.traceId(), span.spanId(), TomographSpan.Kind.INTERNAL,
                        "instrument " + internalClassName)
                .parent(trace.spanId())
                .attribute("tomograph.module", id())
                .attribute("tomograph.classfile.bytes", classfileBuffer.length)
                .status(TomographSpan.Status.OK, null)
                .build());

        return rewritten;
    }

    /** Inserts the probe call at the entry of the target method, leaving everything else alone. */
    private static byte[] rewrite(byte[] original) {
        ClassReader reader = new ClassReader(original);
        // COMPUTE_MAXS, not COMPUTE_FRAMES: see the class comment. Passing the reader in lets ASM
        // reuse the original constant pool.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);

                // The descriptor is part of the match. Matching on the name alone is how a module
                // ends up inserting code for one overload into another - see MethodCutPoint and
                // ADR 0006 for the real case where two methods differ only in return type.
                if (!METHOD_NAME.equals(name) || !METHOD_DESCRIPTOR.equals(descriptor)) {
                    return next;
                }

                return new MethodVisitor(Opcodes.ASM9, next) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitLdcInsn(METHOD_NAME);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE_OWNER, "entered",
                                "(Ljava/lang/String;)V", false);
                    }
                };
            }
        }, 0);

        return writer.toByteArray();
    }

    private static String describe(ClassLoader loader) {
        return (loader == null) ? "bootstrap" : loader.getName();
    }
}
