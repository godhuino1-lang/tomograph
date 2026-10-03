package io.github.godhuino1.tomograph.api;

/**
 * One method a module wants to see, identified by <b>name and descriptor</b>.
 *
 * <p>The descriptor is not optional, and that is the entire point of this type. The framework-target
 * that motivated it declares the same method twice:
 *
 * <pre>
 * doChat(ChatRequest) -&gt; ChatResponse                    (blocking)
 * doChat(ChatRequest) -&gt; java.util.concurrent.Flow$Publisher   (reactive)
 * </pre>
 *
 * <p>Same name, same parameters, different return type. Java forbids that in one class, so the
 * framework puts them on two interfaces - but a bytecode-level matcher that looked only at names
 * would see one thing and instrument both, or instrument one with code written for the other.
 * The result is a {@code VerifyError} while the host application is loading a class, which is the
 * worst failure this project can produce. See ADR 0006.
 *
 * <p>Descriptors are the class file format's own syntax, e.g.
 * {@code (Ldev/langchain4j/model/chat/request/ChatRequest;)Ldev/langchain4j/model/chat/response/ChatResponse;}
 * - see JVMS 4.3.
 *
 * @param name       the method name, e.g. {@code doChat}
 * @param descriptor the full descriptor, return type included
 */
public record MethodCutPoint(String name, String descriptor) {

    public MethodCutPoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("method name must not be blank");
        }
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank (see MethodCutPoint docs)");
        }
        if (descriptor.indexOf('(') < 0) {
            throw new IllegalArgumentException("descriptor must be a method descriptor, got: " + descriptor);
        }
    }

    public static MethodCutPoint of(String name, String descriptor) {
        return new MethodCutPoint(name, descriptor);
    }

    /** Short form for logs: {@code doChat(Ldev/...;)Ldev/...;}. */
    public String display() {
        return name + descriptor;
    }
}
