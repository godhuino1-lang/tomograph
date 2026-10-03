package io.github.godhuino1.tomograph.api;

import java.util.Set;

/**
 * An instrumentation module: knows how to rewrite one framework's bytecode.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}, so a third party can add
 * support for a new framework <em>without touching Tomograph's source</em>.
 * That extensibility is the whole reason this SPI exists: it is how the
 * project can still be growing in year three without the author writing every
 * adapter personally.
 *
 * <p>Implementations are called on the class-loading path. Rules:
 * <ul>
 *   <li>Return {@code null} to mean "leave this class untouched".</li>
 *   <li>Never throw: the engine catches {@link Throwable} anyway and discards the
 *       whole transformation, so throwing only loses data.</li>
 *   <li>Be fast. This runs while a class is being defined, holding a JVM lock.</li>
 * </ul>
 */
public interface TomographModule {

    /** Stable identifier used in logs and in {@code tomograph.module} span attributes, e.g. {@code langchain4j}. */
    String id();

    /**
     * Internal names (slash-separated, as in class file format) this module wants to see,
     * e.g. {@code dev/langchain4j/model/chat/ChatModel}. The engine builds an index from
     * this set so that classes nobody cares about cost one hash lookup, not N module calls.
     */
    Set<String> targetClassNames();

    /**
     * Methods this module wants to see <b>regardless of which class declares them</b>.
     *
     * <p>Needed because a framework's extension point is often an <em>interface</em>: instrumenting
     * the interface does nothing, the code lives in implementations, and the set of implementations
     * is open - providers ship new ones, applications write their own, tests pass doubles. A list of
     * known class names drifts with every upstream release and silently misses the user's own class.
     *
     * <p>So a module can name a <b>signature</b> instead. The parameter types inside a descriptor
     * usually contain the framework's package, which makes the descriptor itself a strong
     * discriminator: {@code doChat} alone might be anything, but
     * {@code doChat(Ldev/langchain4j/model/chat/request/ChatRequest;)...} is LangChain4j's.
     *
     * <p>Cost, stated plainly: matching by signature means the engine must look inside classes it
     * would otherwise skip with one hash lookup. It does that cheaply - a method's name is always
     * present in the class's constant pool, so a byte-level search rejects almost everything before
     * any parsing happens - but this is still work on somebody else's class-loading path. Declare
     * signature cut points only where a class-name list genuinely cannot work.
     *
     * <p>See ADR 0006 for the LangChain4j case that motivated this, including the two ways an
     * obvious-looking choice produces a wrong call tree without raising an error.
     */
    default Set<MethodCutPoint> targetMethods() {
        return Set.of();
    }

    /**
     * Rewrite the given class, or return {@code null} to leave it alone.
     *
     * @param loader        the defining loader, may be {@code null} for bootstrap classes
     * @param internalClassName slash-separated class name
     * @param classfileBuffer the current bytes (possibly already rewritten by an earlier module)
     * @return new class bytes, or {@code null} for "no change"
     */
    byte[] instrument(ClassLoader loader, String internalClassName, byte[] classfileBuffer) throws Exception;

    /**
     * Called once at agent startup, before any transformation. Gives the module access to
     * the span sink it should emit to.
     */
    default void onInstall(Runtime runtime) {
        // no-op by default
    }

    /** What a module is handed at install time. */
    interface Runtime {

        SpanSink sink();

        AgentOptions options();
    }
}
