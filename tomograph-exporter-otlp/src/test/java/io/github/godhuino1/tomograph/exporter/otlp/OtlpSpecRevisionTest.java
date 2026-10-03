package io.github.godhuino1.tomograph.exporter.otlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the recorded specification revision from rotting.
 *
 * <p>The previous "to be verified" note on this module pointed at material that had moved
 * before anyone read it. These assertions cannot detect a stale revision, but they do catch
 * the cheaper failure: someone half-editing the constants and leaving a value that could not
 * possibly identify a revision.
 */
class OtlpSpecRevisionTest {

    @Test
    void recordsACommitThatCouldActuallyIdentifyARevision() {
        assertEquals(40, OtlpSpecRevision.VERIFIED_COMMIT.length(),
                "a git commit hash is 40 hex characters: " + OtlpSpecRevision.VERIFIED_COMMIT);
        assertTrue(OtlpSpecRevision.VERIFIED_COMMIT.matches("[0-9a-f]{40}"),
                "not a lowercase hex commit hash: " + OtlpSpecRevision.VERIFIED_COMMIT);
    }

    @Test
    void recordsAReleaseThatLooksLikeAVersion() {
        assertTrue(OtlpSpecRevision.VERIFIED_RELEASE.matches("\\d+\\.\\d+\\.\\d+"),
                "release should look like a semantic version: " + OtlpSpecRevision.VERIFIED_RELEASE);
    }

    @Test
    void pointsAtFilesInTheSpecificationRepository() {
        assertTrue(OtlpSpecRevision.REPOSITORY.contains("opentelemetry-proto"),
                OtlpSpecRevision.REPOSITORY);
        assertTrue(OtlpSpecRevision.SPEC_FILE.endsWith(".md"), OtlpSpecRevision.SPEC_FILE);
        assertTrue(OtlpSpecRevision.JSON_EXAMPLES_FILE.endsWith(".md"), OtlpSpecRevision.JSON_EXAMPLES_FILE);
    }
}
