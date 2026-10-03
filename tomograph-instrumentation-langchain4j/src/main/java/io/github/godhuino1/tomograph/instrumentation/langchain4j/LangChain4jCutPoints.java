package io.github.godhuino1.tomograph.instrumentation.langchain4j;

import io.github.godhuino1.tomograph.api.MethodCutPoint;

/**
 * The LangChain4j methods Tomograph instruments.
 *
 * <h2>Provenance</h2>
 *
 * <p>Verified against the framework's source on <b>2026-10</b>, read from
 * {@code langchain4j/langchain4j@main}, in
 * {@code langchain4j-core/src/main/java/dev/langchain4j/model/chat/{ChatModel,StreamingChatModel}.java}.
 * The repository's commit feed was unavailable while writing this, so the revision is <b>a branch,
 * not a lock</b> - the signatures below must be re-checked against the pinned version once the jar
 * is in the build. That caveat is the honest state of a decision made without the artefact.
 *
 * <h2>Why this method, and not one of the four obvious ones</h2>
 *
 * <p>{@code ChatModel} has four entry points - {@code chat(String)}, {@code chat(ChatMessage...)},
 * {@code chat(List)} and {@code chat(ChatRequest)} - and they all delegate inward to
 * {@code chat(ChatRequest, ChatRequestOptions)}, which fires the framework's listeners and calls
 * {@code doChat}. {@code doChat} is the innermost funnel and the one method every provider
 * implementation must override.
 *
 * <p>Instrumenting an outer method as well would produce <b>two spans per model call</b> with no
 * error anywhere. See ADR 0006.
 *
 * <h2>The descriptor is not decoration</h2>
 *
 * <p>{@code StreamingChatModel} declares {@code doChat(ChatRequest)} too, returning a reactive
 * {@code Publisher} instead of a {@code ChatResponse}: same name, same parameters, different return
 * type. Matching on the name alone would instrument one of them with code written for the other,
 * which is a {@code VerifyError} while the host loads a class.
 *
 * <h2>What is deliberately not here yet</h2>
 *
 * <p>Streaming ({@code doChat(ChatRequest, StreamingChatResponseHandler)}), the async and reactive
 * paths ({@code doChatAsync}, and {@code doChat(ChatRequest)} returning a {@code Publisher}) are
 * <b>scheduled, not forgotten</b> - all three were introduced in 1.20.0 and marked
 * {@code @Experimental}. Missing them means some applications produce no data at all rather than an
 * error, so the schedule is written in ADR 0006 rather than left to memory.
 */
public final class LangChain4jCutPoints {

    /**
     * The blocking funnel: {@code doChat(ChatRequest) -> ChatResponse}.
     *
     * <p>Package names are spelled out in full because this string <em>is</em> the routing rule: the
     * engine matches classes by this descriptor, which is what lets the module work against
     * implementations it has never heard of, including ones the application wrote itself.
     */
    public static final MethodCutPoint BLOCKING_DO_CHAT = MethodCutPoint.of(
            "doChat",
            "(Ldev/langchain4j/model/chat/request/ChatRequest;)"
                    + "Ldev/langchain4j/model/chat/response/ChatResponse;");

    private LangChain4jCutPoints() {
    }
}
