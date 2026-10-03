package io.github.godhuino1.tomograph.examples.fakeagent;

/**
 * The host application. It has no idea Tomograph exists.
 *
 * <pre>
 * # without the agent - runs normally
 * java -cp target/classes io.github.godhuino1.tomograph.examples.fakeagent.Main
 *
 * # with the agent at startup - same output on stdout, plus Tomograph on stderr
 * java -javaagent:tomograph-agent.jar -cp target/classes \
 *      io.github.godhuino1.tomograph.examples.fakeagent.Main
 *
 * # keep the JVM alive so an agent can be attached to it later
 * java -cp target/classes io.github.godhuino1.tomograph.examples.fakeagent.Main --hold-open
 * </pre>
 *
 * <p>The {@code --hold-open} flag exists for the dynamic-attach integration test, and is
 * documented here because it is also the honest way for a person to try the attach path by
 * hand: start this, read the printed pid, then attach with {@code jcmd} or the attach API.
 */
public final class Main {

    /** Long enough to attach by hand, short enough that a forgotten process does not linger. */
    private static final int HOLD_OPEN_SECONDS = 30;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        long pid = ProcessHandle.current().pid();
        System.out.println("[example] fake agent starting, pid=" + pid
                + ", jvm=" + System.getProperty("java.version"));

        FakeChatModel model = new FakeChatModel();
        String reply = model.chat("Tomograph 能看到这次调用吗？");
        System.out.println("[example] reply: " + reply);

        if (hasFlag(args, "--hold-open")) {
            System.out.println("[example] holding this JVM open for " + HOLD_OPEN_SECONDS
                    + "s so an agent can be attached (pid " + pid + ")");
            System.out.flush();
            Thread.sleep(HOLD_OPEN_SECONDS * 1000L);
        }

        System.out.println("[example] done");
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
