package io.github.godhuino1.tomograph.javaagent;

import io.github.godhuino1.tomograph.core.AgentBootstrap;

import java.lang.instrument.Instrumentation;

/**
 * The class named in the jar manifest as {@code Premain-Class} and {@code Agent-Class}.
 *
 * <p>It exists only to satisfy one JVM requirement: the entry point must be a class
 * with a public static {@code premain}/{@code agentmain} method, and it must be
 * resolvable by name from the manifest. Keeping it separate from
 * {@link AgentBootstrap} means the real logic stays testable in a normal module
 * without a JVM agent harness.
 */
public final class TomographAgent {

    private TomographAgent() {
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        AgentBootstrap.premain(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        AgentBootstrap.agentmain(agentArgs, inst);
    }
}
