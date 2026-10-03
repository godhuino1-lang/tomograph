package io.github.godhuino1.tomograph.examples.fakeagent;

import com.sun.tools.attach.VirtualMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves the dynamic attach path: Tomograph loaded into a JVM that is <b>already running</b>.
 *
 * <h2>Why this test lives in CI and not on the developer's machine</h2>
 *
 * <p>The development sandbox blocks named pipes, and the attach mechanism on Windows is
 * built on them — even {@code jcmd} cannot reach a local JVM there. So this test skips where
 * attach is unavailable, via {@code assumeTrue}, rather than failing or being deleted. A
 * skipped test that names its reason is documentation; a deleted one is a memory.
 *
 * <p>The consequence is that this is the one part of the project whose correctness rests on
 * CI as the only available evidence. That is a legitimate use of CI, not a workaround.
 *
 * <h2>What it asserts</h2>
 *
 * <p>Not "the API call returned". It asserts the target JVM's own log shows the agent ran:
 * the {@code entry=agentmain} banner and the discovered module. An attach that silently does
 * nothing is the failure mode worth catching.
 *
 * <h2>Why the agent jar is located by searching upwards</h2>
 *
 * <p>The first version of this test used {@code Path.of("..", "tomograph-javaagent", ...)}.
 * Surefire runs with the module directory as the working directory, and the agent module is
 * two levels up, not one — so the path did not exist and the test skipped <em>everywhere</em>,
 * including in CI, while looking like it passed. A test that skips for the wrong reason is
 * worse than no test: it manufactures confidence. The search below cannot be off by one, and
 * the resolved path is printed on every run so a wrong answer is visible in the build log.
 */
class DynamicAttachTest {

    // The agent-jar lookup, and its -Dtomograph.agent.jar override, now live in TestAgentJar.

    @Test
    void loadsTheAgentIntoAnAlreadyRunningJvm(@TempDir Path tempDir) throws Exception {
        Path agentJar = resolveAgentJar();
        System.out.println("[attach-test] agent jar resolved to " + agentJar
                + " (exists=" + Files.isRegularFile(agentJar) + ")");

        assumeTrue(Files.isRegularFile(agentJar), "agent jar not found; searched upwards from "
                + Path.of("").toAbsolutePath() + " and got " + agentJar
                + " - build it with 'mvn verify' from the repository root");

        Path log = tempDir.resolve("target-jvm.log");
        Process target = startTargetJvm(log);
        try {
            awaitLine(log, "[example] fake agent starting", Duration.ofSeconds(30));

            attachAndLoadAgent(target.pid(), agentJar);

            String captured = awaitLine(log, "entry=agentmain", Duration.ofSeconds(30));
            assertTrue(captured.contains("installed 1 module(s)"),
                    "the agent started but discovered no instrumentation module:\n" + captured);
        } finally {
            target.destroy();
            if (!target.waitFor(10, TimeUnit.SECONDS)) {
                target.destroyForcibly();
            }
        }
    }

    /**
     * Finds the agent jar. Delegates to {@link TestAgentJar}, which is now the one place that knows
     * the layout and honours {@code -Dtomograph.agent.jar}; this test and the two jar-based ones had
     * three near-identical copies of that lookup, differing only in the override, which is how one
     * of them ends up stale.
     */
    private static Path resolveAgentJar() {
        return TestAgentJar.resolve();
    }

    private static Process startTargetJvm(Path log) throws Exception {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(
                javaBinary,
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path"),
                Main.class.getName(),
                "--hold-open")
                // No pipes: output goes straight to a file, which also keeps this working in
                // environments that restrict pipe creation.
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    /**
     * Attaches and loads the agent, skipping where the environment forbids attaching — there,
     * the absence of a result is not evidence of a defect.
     */
    private static void attachAndLoadAgent(long pid, Path agentJar) throws Exception {
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(String.valueOf(pid));
        } catch (Exception e) {
            assumeTrue(false, "the attach mechanism is unavailable in this environment ("
                    + e.getClass().getName() + ": " + e.getMessage() + ")");
        }
        if (vm == null) {
            return;
        }
        try {
            vm.loadAgent(agentJar.toString(), "otlp.enabled=false");
        } finally {
            vm.detach();
        }
    }

    /** Polls the log until the marker appears, then returns everything captured so far. */
    private static String awaitLine(Path log, String marker, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        String content = "";
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(log)) {
                content = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
                if (content.contains(marker)) {
                    return content;
                }
            }
            Thread.sleep(200L);
        }
        throw new AssertionError("timed out after " + timeout.toSeconds()
                + "s waiting for \"" + marker + "\" in the target JVM log. Captured:\n" + content);
    }
}
