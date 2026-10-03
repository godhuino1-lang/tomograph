package io.github.godhuino1.tomograph.examples.fakeagent;

/**
 * The host application. It has no idea Tomograph exists.
 *
 * <pre>
 * # without the agent - runs normally
 * java -cp target/classes io.github.godhuino1.tomograph.examples.fakeagent.Main
 *
 * # with the agent - same output on stdout, plus Tomograph on stderr
 * java -javaagent:tomograph-agent.jar -cp target/classes \
 *      io.github.godhuino1.tomograph.examples.fakeagent.Main
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("[example] fake agent starting, jvm=" + System.getProperty("java.version"));

        FakeChatModel model = new FakeChatModel();
        String reply = model.chat("Tomograph 能看到这次调用吗？");
        System.out.println("[example] reply: " + reply);

        System.out.println("[example] done");
    }
}
