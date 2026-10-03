package io.github.godhuino1.tomograph.examples.fakeagent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds the built agent jar from a test's working directory.
 *
 * <p>Surefire runs with the module directory as the working directory, and the agent module sits
 * two levels up - but counting the levels is exactly the mistake that once made
 * {@code DynamicAttachTest} skip itself on every machine while looking green. So the directory is
 * searched upward instead of counted, and callers print the resolved path: a wrong path that is
 * never shown is a test that passes for the wrong reason.
 *
 * <p>{@code -Dtomograph.agent.jar=<path>} overrides the search, which is how you point a test at a
 * jar from somewhere else. That property predates this class; it was kept when three copies of this
 * lookup were consolidated into one, because a refactor that quietly drops a capability is not a
 * refactor.
 *
 * <p>One place rather than three because when the layout changes, three copies are three chances to
 * update only two of them.
 */
final class TestAgentJar {

    private static final String SYSTEM_PROPERTY = "tomograph.agent.jar";

    private static final Path RELATIVE = Path.of("tomograph-javaagent", "target", "tomograph-agent.jar");

    private static final int MAX_LEVELS_UP = 4;

    private TestAgentJar() {
    }

    /** The path to use, which may not exist yet - callers assert on that themselves. */
    static Path resolve() {
        String override = System.getProperty(SYSTEM_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }

        Path directory = Path.of("").toAbsolutePath();
        for (int level = 0; level < MAX_LEVELS_UP && directory != null; level++) {
            Path candidate = directory.resolve(RELATIVE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return Path.of("").toAbsolutePath().resolve(RELATIVE);
    }
}
