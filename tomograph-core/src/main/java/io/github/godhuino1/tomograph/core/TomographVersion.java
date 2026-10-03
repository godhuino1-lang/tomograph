package io.github.godhuino1.tomograph.core;

/**
 * Resolves the Tomograph version at runtime.
 *
 * <p>Reads {@code Implementation-Version} from the jar manifest, which Maven sets
 * automatically. Falls back to {@code dev} when running from an IDE or from
 * {@code target/classes} — an honest "I don't know" beats a wrong version number,
 * because this string ends up attached to traces people use to debug production.
 */
public final class TomographVersion {

    private static final String UNKNOWN = "dev";
    private static final String VERSION = resolve();

    private TomographVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String resolve() {
        Package pkg = TomographVersion.class.getPackage();
        String version = (pkg == null) ? null : pkg.getImplementationVersion();
        return (version == null || version.isBlank()) ? UNKNOWN : version;
    }
}
