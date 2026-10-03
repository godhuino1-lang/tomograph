package io.github.godhuino1.tomograph.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the options handed to the agent on the command line:
 *
 * <pre>java -javaagent:tomograph-agent.jar=dryRun=true,sampleRate=0.1 -jar app.jar</pre>
 *
 * <p>Format: {@code key=value,key=value}. Surrounding whitespace is trimmed; a
 * bare {@code key} with no {@code =} is treated as {@code key=true}.
 *
 * <p>Unknown keys are preserved rather than rejected: an instrumentation module
 * may define its own options, and the core has no business validating them.
 */
public final class AgentOptions {

    private static final AgentOptions EMPTY = new AgentOptions(Map.of());

    private final Map<String, String> values;

    private AgentOptions(Map<String, String> values) {
        this.values = values;
    }

    public static AgentOptions empty() {
        return EMPTY;
    }

    public static AgentOptions parse(String agentArgs) {
        if (agentArgs == null || agentArgs.isBlank()) {
            return EMPTY;
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String token : agentArgs.split(",")) {
            String pair = token.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                parsed.put(pair, "true");
            } else {
                parsed.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return parsed.isEmpty() ? EMPTY : new AgentOptions(Collections.unmodifiableMap(parsed));
    }

    public String get(String key, String defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Map<String, String> asMap() {
        return values;
    }

    /**
     * Prints key names only, never values: agent options commonly carry API keys and
     * endpoint tokens, and agent output frequently ends up in shared logs.
     */
    @Override
    public String toString() {
        return "AgentOptions" + values.keySet();
    }
}
