package io.github.tomograph.api;

/**
 * Minimal logging facade so that neither the API nor any instrumentation module
 * needs to pull in a logging framework.
 *
 * <p>Writes to {@code System.err}, never {@code System.out}: the agent shares
 * stdout with the host application, and corrupting a program's stdout (a CLI's
 * JSON output, a data pipeline's records) is a real way to break users.
 *
 * <p>Enable verbose output with {@code -Dtomograph.debug=true}.
 */
public final class TomographLog {

    private static final String PREFIX = "[tomograph] ";
    private static final boolean DEBUG = Boolean.getBoolean("tomograph.debug");

    private TomographLog() {
    }

    public static void info(String message) {
        System.err.println(PREFIX + message);
    }

    public static void debug(String message) {
        if (DEBUG) {
            System.err.println(PREFIX + "DEBUG " + message);
        }
    }

    public static void warn(String message) {
        System.err.println(PREFIX + "WARN " + message);
    }

    public static void error(String message, Throwable t) {
        System.err.println(PREFIX + "ERROR " + message);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    public static boolean isDebug() {
        return DEBUG;
    }
}
