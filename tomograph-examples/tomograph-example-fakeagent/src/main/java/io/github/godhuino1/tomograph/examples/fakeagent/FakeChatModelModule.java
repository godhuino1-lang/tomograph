package io.github.godhuino1.tomograph.examples.fakeagent;

import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographModule;
import io.github.godhuino1.tomograph.api.TomographSpan;
import io.github.godhuino1.tomograph.semconv.TraceContext;

import java.util.Set;

/**
 * A third-party instrumentation module, written entirely from the outside.
 *
 * <p>It is declared in {@code META-INF/services/io.github.godhuino1.tomograph.api.TomographModule},
 * discovered by {@code ServiceLoader} when the agent starts, and asked to instrument exactly one
 * class. Nothing in the agent knows this module exists.
 *
 * <p>Today it <em>observes</em> rather than rewrites: {@link #instrument} returns {@code null}, so
 * the class loads unchanged, and it emits a span to prove the wiring is live. v0.1 replaces the
 * body with real ASM rewriting; the plumbing around it is what this skeleton verifies.
 *
 * <h2>Why the ids come from {@link TraceContext} and not from a UUID</h2>
 *
 * <p>This module used to build both ids with {@code UUID.randomUUID()}. A UUID string without its
 * dashes is <b>32</b> hex characters, which happens to be the correct length for a W3C trace id —
 * and is <b>twice</b> the correct length for a span id, which must be 16. So the trace id looked
 * right and the span id was silently invalid: a backend would reject it or fail to link it, with
 * nothing in this repository's logs to say so.
 *
 * <p>Every unit test passed. The defect surfaced the first time an end-to-end test asserted on a
 * span that had actually crossed a socket — which is the entire argument for having one.
 */
public final class FakeChatModelModule implements TomographModule {

    /** Internal name, slash-separated — class file format, not source format. */
    private static final String TARGET =
            "io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel";

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
        TomographLog.info("module " + id() + " matched " + internalClassName
                + " (" + classfileBuffer.length + " bytes, loader=" + describe(loader) + ")");

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

        // No rewriting yet: returning null explicitly means "leave this class alone", which is the
        // only safe thing a skeleton module may do.
        return null;
    }

    private static String describe(ClassLoader loader) {
        return (loader == null) ? "bootstrap" : loader.getName();
    }
}
