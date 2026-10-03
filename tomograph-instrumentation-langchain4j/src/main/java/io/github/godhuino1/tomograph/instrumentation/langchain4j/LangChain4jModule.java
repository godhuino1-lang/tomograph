package io.github.godhuino1.tomograph.instrumentation.langchain4j;

import io.github.godhuino1.tomograph.api.MethodCutPoint;
import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographModule;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * Instruments LangChain4j model calls by rewriting {@code doChat} — the innermost funnel (ADR 0006).
 *
 * <h2>How a class it has never heard of gets instrumented</h2>
 *
 * <p>{@link #targetClassNames()} is empty on purpose. {@code ChatModel} is an interface, so the code
 * lives in implementations whose set is open: providers ship new ones, applications write their own,
 * tests pass doubles. Instead the module declares a {@link MethodCutPoint}, and the engine hands it
 * any class that declares that exact method — which is why the module works against
 * {@code OpenAiChatModel}, {@code OllamaChatModel}, and a class written last week in the application
 * being observed.
 *
 * <h2>What the injected code looks like, and why</h2>
 *
 * <p>At method entry:
 *
 * <pre>
 * LDC "doChat(...)..."; INVOKESTATIC LangChain4jProbe.entered(Ljava/lang/String;)V
 * </pre>
 *
 * <p>Before each {@code ARETURN}, with the response already on the stack:
 *
 * <pre>
 * DUP; LDC methodId; ALOAD 0; ALOAD 1; INVOKESTATIC LangChain4jProbe.exited(...)V
 * </pre>
 *
 * <p>Three details there are easy to get wrong and each produces a {@code VerifyError} rather than a
 * test failure:
 *
 * <ol>
 *   <li><b>{@code DUP} exists because the response has to be both reported and returned.</b> Without
 *       it the probe call would consume the response and {@code ARETURN} would pop nothing.</li>
 *   <li><b>The response is the probe's <em>first</em> parameter even though it is pushed first.</b>
 *       The JVM pops the last declared parameter off the top, and the response is at the bottom of
 *       the group being pushed, so it has to be declared first. Getting this backwards is a
 *       descriptor mismatch at verify time.</li>
 *   <li><b>Everything crossing into the probe is {@code Object}.</b> Not one LangChain4j type
 *       appears in the injected bytecode (ADR 0008): a framework upgrade therefore cannot turn a
 *       missing attribute into a linkage error inside the host.</li>
 * </ol>
 *
 * <p>{@code COMPUTE_MAXS} is enough rather than {@code COMPUTE_FRAMES}: the injected code adds no
 * local variable and no branch target, so the original stack map frames stay valid.
 *
 * <h2>Known limitations of this first version</h2>
 *
 * <ul>
 *   <li><b>A call that throws produces no span.</b> The exit call sits at the return instruction, so
 *       an exception skips it. That is the wrong way round for debugging, and it is the first thing
 *       to fix - reporting failures is the point of the project. It needs a catch-all handler, and
 *       therefore frame recomputation, so it is v0.2 work rather than a tweak.</li>
 *   <li>Streaming, async and reactive paths are not cut yet (ADR 0006 schedules them).</li>
 * </ul>
 */
public final class LangChain4jModule implements TomographModule {

    /** Internal name of the probe the injected code calls. Must stay in step with the class. */
    private static final String PROBE_INTERNAL_NAME =
            "io/github/godhuino1/tomograph/instrumentation/langchain4j/LangChain4jProbe";

    private final Set<MethodCutPoint> cutPoints;

    public LangChain4jModule() {
        this(Set.of(LangChain4jCutPoints.BLOCKING_DO_CHAT));
    }

    /**
     * For tests: the cut point is injectable so the rewriting logic can be exercised against fake
     * request/response types, without publishing test classes inside the framework's package.
     */
    LangChain4jModule(Set<MethodCutPoint> cutPoints) {
        this.cutPoints = Set.copyOf(cutPoints);
    }

    @Override
    public String id() {
        return "langchain4j";
    }

    /** Empty on purpose: this module is routed by signature, not by class name. */
    @Override
    public Set<String> targetClassNames() {
        return Set.of();
    }

    @Override
    public Set<MethodCutPoint> targetMethods() {
        return cutPoints;
    }

    @Override
    public void onInstall(Runtime runtime) {
        LangChain4jProbe.install(runtime.sink());
        TomographLog.info("module " + id() + " installed, cut points=" + cutPoints.size()
                + ", options=" + runtime.options());
    }

    @Override
    public byte[] instrument(ClassLoader loader, String internalClassName, byte[] classfileBuffer) {
        // The engine routes by signature, so the method is expected to be here - but a module is
        // handed bytes, not a promise. `rewrite` reports whether it changed anything, and a class it
        // does not recognise comes back untouched.
        byte[] rewritten = rewrite(classfileBuffer);
        if (rewritten == null) {
            return null;
        }
        TomographLog.debug("module " + id() + " instrumented " + internalClassName
                + " (" + classfileBuffer.length + " -> " + rewritten.length + " bytes)");
        return rewritten;
    }

    /** Returns rewritten bytes, or null if none of this module's cut points is declared. */
    private byte[] rewrite(byte[] original) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] changed = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!matches(name, descriptor)) {
                    return next;
                }
                changed[0] = true;
                String methodId = name + descriptor;

                return new MethodVisitor(Opcodes.ASM9, next) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitLdcInsn(methodId);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE_INTERNAL_NAME, "entered",
                                "(Ljava/lang/String;)V", false);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            // See the class comment: DUP keeps a copy for ARETURN, and the response
                            // is declared first because it sits at the bottom of the pushed group.
                            super.visitInsn(Opcodes.DUP);
                            super.visitLdcInsn(methodId);
                            super.visitVarInsn(Opcodes.ALOAD, 0);   // the model instance
                            super.visitVarInsn(Opcodes.ALOAD, 1);   // the ChatRequest
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE_INTERNAL_NAME, "exited",
                                    "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
                                    false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);

        return changed[0] ? writer.toByteArray() : null;
    }

    private boolean matches(String name, String descriptor) {
        for (MethodCutPoint cutPoint : cutPoints) {
            if (cutPoint.name().equals(name) && cutPoint.descriptor().equals(descriptor)) {
                return true;
            }
        }
        return false;
    }
}
