package io.github.godhuino1.tomograph.examples.fakeagent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Inspects the built agent jar itself.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>Three rules about the agent jar are written down in design documents and, until now, enforced
 * by nothing. This project's own experience is that a rule kept only in prose gets broken:
 *
 * <ol>
 *   <li><b>The agent has no third-party dependencies</b> (ADR 0003). It is injected into somebody
 *       else's JVM, where a shaded library can collide with the host's own copy of it, and every
 *       application that turns instrumentation on pays its weight. The offline report module is
 *       allowed to use Jackson (ADR 0005); the agent is not. That boundary is easy to cross by
 *       accident - someone adds the report module to the agent's dependency tree for reuse, and
 *       ADR 0003 is repealed without anyone deciding to repeal it.</li>
 *   <li><b>The ServiceLoader descriptor has to be present</b> (the engine discovers every sink
 *       through it). This broke once during a package rename: the build stayed green, and only a
 *       smoke test noticed that no sink was ever installed.</li>
 *   <li><b>The manifest has to be right</b> (the JVM finds the entry point through it, and the
 *       version is what the startup banner prints). A missing {@code Implementation-Version} once
 *       made every agent announce itself as {@code dev}.</li>
 * </ol>
 *
 * <p>Asserting on the artifact rather than on the pom is deliberate: the pom describes an
 * intention, the jar is what ships. A dependency can also arrive transitively, where no pom change
 * is visible at all.
 */
class AgentJarContentsTest {

    private static final Path AGENT_JAR = TestAgentJar.resolve();

    /** Everything the agent ships must live under this package. */
    private static final String OWN_PACKAGE = "io/github/godhuino1/tomograph/";

    @Test
    void containsNoThirdPartyClasses() throws IOException {
        assumeTrue(Files.isRegularFile(AGENT_JAR),
                "agent jar not built yet at " + AGENT_JAR + " - run mvn verify from the repository root");

        List<String> foreign = new ArrayList<>();
        int ownClasses = 0;

        try (ZipFile jar = new ZipFile(AGENT_JAR.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                // META-INF holds the manifest, the services descriptors and Maven metadata, none of
                // which is a class file from a library.
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                if (!name.endsWith(".class")) {
                    continue;
                }
                if (name.startsWith(OWN_PACKAGE)) {
                    ownClasses++;
                } else {
                    foreign.add(name);
                }
            }
        }

        assertTrue(ownClasses > 0, "no Tomograph classes in the agent jar at all: " + AGENT_JAR);
        assertTrue(foreign.isEmpty(),
                "the agent jar contains classes that are not ours, so ADR 0003's zero-dependency "
                        + "rule has been broken (did the report module get added to its dependency "
                        + "tree? it is allowed to use Jackson, the agent is not): " + foreign);
    }

    @Test
    void carriesTheServiceLoaderDescriptorTheEngineDependsOn() throws IOException {
        assumeTrue(Files.isRegularFile(AGENT_JAR), "agent jar not built yet at " + AGENT_JAR);

        String descriptor = "META-INF/services/io.github.godhuino1.tomograph.api.SpanSinkProvider";

        try (ZipFile jar = new ZipFile(AGENT_JAR.toFile())) {
            ZipEntry entry = jar.getEntry(descriptor);
            assertNotNull(entry,
                    "the ServiceLoader descriptor is missing from the agent jar. The engine "
                            + "discovers sinks only through it, so without this file the agent "
                            + "starts and exports nothing, without an error. Contains instead: "
                            + jar.stream().map(ZipEntry::getName).filter(n -> n.contains("services")).toList());

            try (InputStream in = jar.getInputStream(entry)) {
                String contents = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(contents.contains("OtlpSpanSinkProvider"),
                        "the descriptor does not name the OTLP provider: " + contents);
            }
        }
    }

    @Test
    void manifestStartsTheAgentAndReportsARealVersion() throws IOException {
        assumeTrue(Files.isRegularFile(AGENT_JAR), "agent jar not built yet at " + AGENT_JAR);

        try (JarFile jar = new JarFile(AGENT_JAR.toFile())) {
            Manifest manifest = jar.getManifest();
            assertNotNull(manifest, "the agent jar has no manifest, so the JVM cannot find the agent");
            Attributes attributes = manifest.getMainAttributes();

            String premain = attributes.getValue("Premain-Class");
            assertNotNull(premain, "Premain-Class is missing: -javaagent would fail to start");
            assertTrue(premain.endsWith("TomographAgent"),
                    "Premain-Class does not point at the agent class: " + premain);
            assertEquals(premain, attributes.getValue("Agent-Class"),
                    "Agent-Class should be the same class, so that runtime attach works too");
            assertEquals("true", attributes.getValue("Can-Retransform-Classes"),
                    "the agent cannot retransform already-loaded classes without this");

            String version = attributes.getValue("Implementation-Version");
            assertNotNull(version, "Implementation-Version is missing; the startup banner would read 'dev'");
            assertFalse(version.isBlank(), "Implementation-Version is blank");
            assertFalse(version.contains("${"), "an unresolved Maven property leaked into the manifest: " + version);
        }
    }

    @Test
    void doesNotShipSourcesOrTests() throws IOException {
        assumeTrue(Files.isRegularFile(AGENT_JAR), "agent jar not built yet at " + AGENT_JAR);

        List<String> junk = new ArrayList<>();
        try (ZipFile jar = new ZipFile(AGENT_JAR.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".java") || name.endsWith("Test.class")) {
                    junk.add(name);
                }
            }
        }
        assertTrue(junk.isEmpty(), "sources or test classes were shaded into the agent jar: " + junk);
    }
}
