package io.github.godhuino1.tomograph.examples.fakeagent;

/**
 * A deliberately naive stand-in for a real chat model.
 *
 * <p>No network, no API key, no framework: this exists so the integration test and the
 * CI smoke test are deterministic and free. In v0.1 the real targets (LangChain4j,
 * Spring AI) join, and this class stays as the fast regression harness.
 *
 * <p>Note what is <em>not</em> here: any reference to Tomograph. That is the product
 * promise in miniature — a host application that knows nothing about observability.
 */
public class FakeChatModel {

    private final String modelName;

    public FakeChatModel() {
        this("fake-model-v1");
    }

    public FakeChatModel(String modelName) {
        this.modelName = modelName;
    }

    public String modelName() {
        return modelName;
    }

    /** Pretends to be one model round trip. */
    public String chat(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        simulateLatency();
        return "echo(" + modelName + "): " + prompt;
    }

    private void simulateLatency() {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
