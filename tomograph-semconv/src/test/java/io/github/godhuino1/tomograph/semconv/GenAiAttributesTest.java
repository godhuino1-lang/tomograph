package io.github.godhuino1.tomograph.semconv;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shape of {@link GenAiAttributes} and the integrity of {@link SemconvRevision}.
 *
 * <p>Two different failures are covered:
 * <ol>
 *   <li><b>Namespace drift.</b> A key such as {@code genai.usage.input_tokens} (missing
 *       underscore) or {@code tomograph.input_tokens} is accepted by every backend and lands
 *       in a dashboard nobody queries. Reflection over the fields catches this for keys that
 *       do not exist yet, which a hand-written list of assertions never can.</li>
 *   <li><b>Accidental rename.</b> These strings are an external contract; changing one breaks
 *       existing dashboards. Pinning them forces the change to be deliberate.</li>
 * </ol>
 */
class GenAiAttributesTest {

    /**
     * General (non-GenAI) conventions that a GenAI span still legitimately carries.
     * Everything else must be namespaced under {@code gen_ai.}.
     */
    private static final List<String> ALLOWED_FOREIGN_KEYS = List.of(
            "error.type",
            "server.address",
            "server.port"
    );

    @Test
    void everyKeyIsNamespacedUnderGenAiOrIsAnAllowedGeneralKey() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String key : allPublicStringConstants()) {
            boolean namespaced = key.startsWith("gen_ai.");
            boolean allowedForeign = ALLOWED_FOREIGN_KEYS.contains(key);
            if (!namespaced && !allowedForeign) {
                offenders.add(key);
            }
        }
        assertTrue(offenders.isEmpty(),
                "these attribute keys are neither gen_ai.* nor on the allowed general list: " + offenders);
    }

    @Test
    void noKeyContainsWhitespaceOrUppercase() throws Exception {
        for (String key : allPublicStringConstants()) {
            assertEquals(key.toLowerCase(), key, "keys must be lower case: " + key);
            assertEquals(-1, key.indexOf(' '), "keys must not contain spaces: " + key);
            assertEquals(-1, key.indexOf('\t'), "keys must not contain tabs: " + key);
        }
    }

    @Test
    void criticalKeysHaveTheDocumentedSpelling() {
        // Covering the acceptance criteria: model, tokens (including the cache split that cost
        // accounting depends on), tool identity and its side effects, agent identity, retrieval.
        assertEquals("gen_ai.request.model", GenAiAttributes.REQUEST_MODEL);
        assertEquals("gen_ai.usage.input_tokens", GenAiAttributes.USAGE_INPUT_TOKENS);
        assertEquals("gen_ai.usage.output_tokens", GenAiAttributes.USAGE_OUTPUT_TOKENS);
        assertEquals("gen_ai.usage.cache_read.input_tokens", GenAiAttributes.USAGE_CACHE_READ_INPUT_TOKENS);
        assertEquals("gen_ai.usage.cache_write.input_tokens", GenAiAttributes.USAGE_CACHE_WRITE_INPUT_TOKENS);
        assertEquals("gen_ai.tool.name", GenAiAttributes.TOOL_NAME);
        assertEquals("gen_ai.tool.call.arguments", GenAiAttributes.TOOL_CALL_ARGUMENTS);
        assertEquals("gen_ai.tool.call.result", GenAiAttributes.TOOL_CALL_RESULT);
        assertEquals("gen_ai.agent.name", GenAiAttributes.AGENT_NAME);
        assertEquals("gen_ai.retrieval.documents", GenAiAttributes.RETRIEVAL_DOCUMENTS);
    }

    @Test
    void legacyKeysAreKeptForReadingOldTracesButNotEmitting() {
        // gen_ai.system was removed from the registry entirely, and gen_ai.token.type was
        // replaced by gen_ai.token.modality. Both are kept only so that a trace produced by an
        // older build can still be interpreted; the replacements are what new code emits.
        assertEquals("gen_ai.system", GenAiAttributes.SYSTEM);
        assertEquals("gen_ai.provider.name", GenAiAttributes.PROVIDER_NAME);
        assertEquals("gen_ai.token.modality", GenAiAttributes.TOKEN_MODALITY);
    }

    @Test
    void theVerifiedSpecRevisionIsRecordedAndPlausible() {
        // The previous "verify this later" note pointed at URLs that died before anyone
        // followed them. Recording the revision as data is what makes the next check possible.
        assertEquals(40, SemconvRevision.VERIFIED_COMMIT.length(),
                "a git commit hash is 40 hex characters: " + SemconvRevision.VERIFIED_COMMIT);
        assertTrue(SemconvRevision.VERIFIED_COMMIT.matches("[0-9a-f]{40}"),
                "not a lowercase hex commit hash: " + SemconvRevision.VERIFIED_COMMIT);
        assertTrue(SemconvRevision.REPOSITORY.startsWith("https://github.com/open-telemetry/"),
                SemconvRevision.REPOSITORY);
        assertTrue(SemconvRevision.ATTRIBUTE_REGISTRY_FILE.endsWith(".yaml"),
                SemconvRevision.ATTRIBUTE_REGISTRY_FILE);
    }

    private static List<String> allPublicStringConstants() throws Exception {
        List<String> keys = new ArrayList<>();
        for (Field field : GenAiAttributes.class.getDeclaredFields()) {
            boolean isConstant = Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == String.class;
            if (isConstant) {
                keys.add((String) field.get(null));
            }
        }
        assertTrue(keys.size() > 40, "reflection found suspiciously few keys: " + keys.size());
        return keys;
    }
}
