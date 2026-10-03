package io.github.godhuino1.tomograph.instrumentation.langchain4j;

import io.github.godhuino1.tomograph.api.SpanSink;
import io.github.godhuino1.tomograph.api.TomographLog;
import io.github.godhuino1.tomograph.api.TomographSpan;
import io.github.godhuino1.tomograph.semconv.GenAiAttributes;
import io.github.godhuino1.tomograph.semconv.SpanName;
import io.github.godhuino1.tomograph.semconv.TomographAttributes;
import io.github.godhuino1.tomograph.semconv.TraceContext;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The method that injected bytecode calls. Everything the agent knows about a model call is
 * assembled here.
 *
 * <h2>Why this class has no LangChain4j imports</h2>
 *
 * <p>Deliberate, and it is the reason the module can be written at all in an environment where the
 * framework's jar cannot be downloaded (ADR 0008). The injected call sites name only this class and
 * only {@code Object} parameters, so the framework's types never appear in the host's bytecode:
 * a framework upgrade can cost us an attribute, but it cannot produce a linkage error inside
 * somebody's application.
 *
 * <p>The cost is that the field extraction below is reflective and the accessor names are strings.
 * Each one is annotated with where it was verified from. If an accessor disappears, the attribute is
 * simply missing - the span is still emitted - which is the right way round, and also the reason the
 * integration test against a real jar is not optional.
 *
 * <h2>Two things it must never do</h2>
 *
 * <ol>
 *   <li><b>Throw.</b> These methods run on the host's call path, between an application and its
 *       model. An exception here would surface as a failure of the <em>model call</em>, which would
 *       be an instrumentation bug reported as a framework bug. Every entry point catches
 *       {@link Throwable}.</li>
 *   <li><b>Block.</b> Reporting goes through the sink, which is bounded and non-blocking by design;
 *       nothing here waits on anything.</li>
 * </ol>
 *
 * <h2>Known simplification</h2>
 *
 * <p>All calls in a JVM are children of one root trace, because propagating the observed
 * application's own context is the hard problem this project has not solved yet (hard problem 2, and
 * v1.0 work). One trace with many model-call spans is less useful than a real call tree, and saying
 * so is better than pretending the tree is real.
 */
public final class LangChain4jProbe {

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /** Replaced once at install time. Volatile because install happens on another thread. */
    private static volatile SpanSink sink = span -> { };

    private static final TraceContext TRACE = TraceContext.newRoot();

    private static final AtomicLong UNBALANCED = new AtomicLong();

    private LangChain4jProbe() {
    }

    /** Called once by the module at install time. */
    static void install(SpanSink installedSink) {
        sink = (installedSink == null) ? (span -> { }) : installedSink;
    }

    /** Entry hook. One argument, so the injected call site stays as small as possible. */
    public static void entered(String methodId) {
        try {
            STACK.get().push(new Frame(methodId, System.nanoTime()));
        } catch (Throwable t) {
            countAndLog("entered", t);
        }
    }

    /**
     * Exit hook.
     *
     * @param response the value being returned — declared FIRST because the injected code pushes it
     *                 at the bottom of the argument group, and the JVM pops the last parameter off
     *                 the top of the stack
     * @param methodId name plus descriptor of the instrumented method
     * @param model    the model instance ({@code this}), for attributes that live on it
     * @param request  the {@code ChatRequest}
     */
    public static void exited(Object response, String methodId, Object model, Object request) {
        try {
            Deque<Frame> stack = STACK.get();
            Frame frame = stack.poll();
            if (frame == null || !frame.methodId.equals(methodId)) {
                // The usual cause is a call that threw: the exit hook sits on the return path, so an
                // exception skips it and leaves the frame behind. Counting it is how that limitation
                // stays visible instead of turning into mysterious timings.
                UNBALANCED.incrementAndGet();
                if (frame != null) {
                    stack.push(frame);
                }
                return;
            }
            long durationNanos = System.nanoTime() - frame.startNanos;
            sink.accept(buildSpan(methodId, durationNanos, model, request, response));
        } catch (Throwable t) {
            countAndLog("exited", t);
        }
    }

    /** How many exit calls arrived without a matching entry. Exposed for tests and diagnostics. */
    public static long unbalancedCount() {
        return UNBALANCED.get();
    }

    private static TomographSpan buildSpan(String methodId, long durationNanos,
                                           Object model, Object request, Object response) {
        String requestModel = asString(call(request, "modelName"));
        String responseModel = asString(call(response, "modelName"));
        String modelName = (requestModel != null) ? requestModel : responseModel;

        TomographSpan.Builder builder = TomographSpan
                .builder(TRACE.traceId(), TRACE.childSpan().spanId(), TomographSpan.Kind.LLM_CALL,
                        SpanName.chat(modelName))
                .attribute(GenAiAttributes.OPERATION_NAME, SpanName.CHAT)
                .attribute(TomographAttributes.MODULE, "langchain4j")
                .attribute(TomographAttributes.CUT_POINT, methodId);

        // gen_ai.provider.name is required on an inference span, and the model instance is why the
        // injected code bothers loading `this` at all.
        put(builder, GenAiAttributes.PROVIDER_NAME, providerName(call(model, "provider")));

        put(builder, GenAiAttributes.REQUEST_MODEL, requestModel);
        put(builder, GenAiAttributes.REQUEST_MAX_TOKENS, call(request, "maxOutputTokens"));
        put(builder, GenAiAttributes.REQUEST_TEMPERATURE, call(request, "temperature"));
        put(builder, GenAiAttributes.REQUEST_TOP_P, call(request, "topP"));
        put(builder, GenAiAttributes.REQUEST_TOP_K, call(request, "topK"));
        put(builder, GenAiAttributes.REQUEST_FREQUENCY_PENALTY, call(request, "frequencyPenalty"));
        put(builder, GenAiAttributes.REQUEST_PRESENCE_PENALTY, call(request, "presencePenalty"));
        put(builder, GenAiAttributes.REQUEST_STOP_SEQUENCES, joinIfList(call(request, "stopSequences")));

        put(builder, GenAiAttributes.RESPONSE_ID, call(response, "id"));
        put(builder, GenAiAttributes.RESPONSE_MODEL, responseModel);
        put(builder, GenAiAttributes.RESPONSE_FINISH_REASONS, finishReason(call(response, "finishReason")));

        Object tokenUsage = call(response, "tokenUsage");
        put(builder, GenAiAttributes.USAGE_INPUT_TOKENS, call(tokenUsage, "inputTokenCount"));
        put(builder, GenAiAttributes.USAGE_OUTPUT_TOKENS, call(tokenUsage, "outputTokenCount"));

        // The span's own duration field, not a redundant attribute: every OTLP reader derives
        // duration from start and end anyway.
        builder.duration(durationNanos);

        // OK, not ERROR: a call that threw never reaches this method, so it has no span at all. That
        // limitation is stated on the module rather than papered over here by guessing.
        return builder.status(TomographSpan.Status.OK, null).build();
    }

    private static void put(TomographSpan.Builder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Double
                || value instanceof Boolean || value instanceof String) {
            builder.attribute(key, value);
        } else {
            // Never guess a representation for an unexpected type: an unreadable attribute is worse
            // than a missing one, because it looks like data.
            builder.attribute(key, String.valueOf(value));
        }
    }

    /** {@code FinishReason} is an enum; the conventions use the lowercase name, e.g. {@code stop}. */
    private static String finishReason(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase(java.util.Locale.ROOT);
        }
        return String.valueOf(value).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Maps the framework's {@code ModelProvider} enum onto the conventions' lowercase value.
     *
     * <p>{@code OTHER} means "the framework does not know", and it becomes an absent attribute rather
     * than the literal string {@code other}: the conventions want a real provider name or nothing,
     * and a plausible-looking placeholder is the kind of data that ends up in a dashboard.
     *
     * <p><b>Caveat, and it is a real one:</b> compound names are not exact. The conventions use
     * {@code gcp.vertex_ai} while lowercasing the Java constant yields {@code gcp_vertex_ai}. Doing
     * that properly needs the real enum in front of us, which is one of the things the integration
     * test against a real jar will surface.
     */
    private static String providerName(Object modelProvider) {
        if (!(modelProvider instanceof Enum<?> provider)) {
            return null;
        }
        String name = provider.name();
        return "OTHER".equals(name) ? null : name.toLowerCase(java.util.Locale.ROOT);
    }

    private static String joinIfList(Object value) {
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return (value == null) ? null : String.valueOf(value);
    }

    private static String asString(Object value) {
        return (value == null) ? null : String.valueOf(value);
    }

    /**
     * Calls a no-argument accessor by name, trying each candidate in order.
     *
     * <p>Verified against the framework's source (2026-10, {@code @main}); the caller passes the
     * verified name first and any historical alternative after it. Returns null rather than throwing
     * when nothing matches, which is what makes a missing accessor cost an attribute instead of a
     * span.
     */
    static Object call(Object target, String... candidateNames) {
        if (target == null) {
            return null;
        }
        for (String name : candidateNames) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                try {
                    return method.invoke(target);
                } catch (IllegalAccessException e) {
                    // A non-public implementation class with a public accessor: reachable through the
                    // interface, but not callable reflectively without this.
                    method.setAccessible(true);
                    return method.invoke(target);
                }
            } catch (Throwable ignored) {
                // Try the next candidate name.
            }
        }
        return null;
    }

    private static void countAndLog(String where, Throwable t) {
        try {
            TomographLog.debug("langchain4j probe " + where + " failed (contained): " + t);
        } catch (Throwable ignored) {
            // Logging must not be the thing that breaks the host either.
        }
    }

    private record Frame(String methodId, long startNanos) {
    }
}
