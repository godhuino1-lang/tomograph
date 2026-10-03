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
