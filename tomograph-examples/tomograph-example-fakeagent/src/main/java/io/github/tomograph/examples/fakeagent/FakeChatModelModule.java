package io.github.tomograph.examples.fakeagent;

import io.github.tomograph.api.SpanSink;
import io.github.tomograph.api.TomographLog;
import io.github.tomograph.api.TomographModule;
import io.github.tomograph.api.TomographSpan;

import java.util.Set;
import java.util.UUID;

/**
 * A third-party instrumentation module, written entirely from the outside.
 *
 * <p>It is declared in {@code META-INF/services/io.github.tomograph.api.TomographModule},
 * discovered by {@code ServiceLoader} when the agent starts, and asked to instrument
 * exactly one class. Nothing in the agent knows this module exists.
 *
 * <p>Today it <em>observes</em> rather than rewrites: {@link #instrument} returns
 * {@code null}, so the class is loaded unchanged, and it emits a span to prove the
 * wiring is live. v0.1 replaces the body with real ASM rewriting; the plumbing around
 * it is what this skeleton is verifying.
 */
public final class FakeChatModelModule implements TomographModule {

    /** Internal name, slash-separated — class file format, not source format. */
    private static final String TARGET =
            "io/github/tomograph/examples/fakeagent/FakeChatModel";

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

        sink.accept(TomographSpan
                .builder(newId(), newId(), TomographSpan.Kind.LLM_CALL, "load " + internalClassName)
                .attribute("tomograph.module", id())
                .attribute("tomograph.classfile.bytes", classfileBuffer.length)
                .attribute("gen_ai.system", "fake")
                .status(TomographSpan.Status.OK, null)
                .build());

        // No rewriting yet: returning null explicitly means "leave this class alone",
        // which is the only safe thing a skeleton module may do.
        return null;
    }

    private static String describe(ClassLoader loader) {
        return (loader == null) ? "bootstrap" : loader.getName();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
