package io.github.godhuino1.tomograph.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures what signature routing costs per loaded class, and guards against it getting catastrophic.
 *
 * <h2>What this is and is not</h2>
 *
 * <p>It is <b>not</b> a benchmark. A JMH run is on the v0.1 list together with the
 * instrumentation-overhead figure, and JMH is the only honest way to answer "what does this cost"
 * for a change of this size. What this does is refuse to let the claim stay an assumption: the
 * design says the byte scan is cheap, and cheap is a number.
 *
 * <p>So the assertions are deliberately loose - roughly two orders of magnitude above the measured
 * values - because their job is to catch a change that makes the scan quadratic or accidentally
 * proportional to the whole class file, not to police micro-regressions on a busy CI machine. The
 * measured values are printed, so the build log carries them and a trend is visible over time.
 *
 * <h2>Why the number matters</h2>
 *
 * <p>This runs for <b>every class the host JVM loads</b>, on the thread that is defining it, holding
 * a JVM lock. Ten thousand classes at startup is ordinary for a Spring Boot application, so a
 * careless implementation here is not a micro-optimisation detail - it is paid by every application
 * that turns instrumentation on.
 */
class RoutingCostTest {

    private static final byte[] ABSENT_NEEDLE = "doChat".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PRESENT_NEEDLE = "declares".getBytes(StandardCharsets.UTF_8);

    @Test
    void scanningAClassForAMethodNameItDoesNotDeclareIsMicroseconds() throws IOException {
        byte[] classBytes = bytesOf(InstrumentationEngine.class);
        long perScanNanos = bestOf(ByteScan::contains, classBytes, ABSENT_NEEDLE, false);

        report("byte scan, name absent (the common case)", perScanNanos, classBytes.length);

        // Two orders of magnitude above what is measured here. A failure means something became
        // quadratic, or started allocating, not that a machine was busy.
        assertTrue(perScanNanos < 100_000,
                "scanning a " + classBytes.length + " byte class took " + perScanNanos
                        + " ns, which is far outside the expected range");
    }

    @Test
    void scanningForAMethodNameThatIsPresentCostsTheSameOrder() throws IOException {
        byte[] classBytes = bytesOf(DeclaredMethods.class);
        long perScanNanos = bestOf(ByteScan::contains, classBytes, PRESENT_NEEDLE, true);

        report("byte scan, name present (the hit case)", perScanNanos, classBytes.length);

        assertTrue(perScanNanos < 100_000, "hit-case scan took " + perScanNanos + " ns");
    }

    @Test
    void theExactCheckIsTheExpensiveOneWhichIsWhyItRunsSecond() throws IOException {
        byte[] classBytes = bytesOf(InstrumentationEngine.class);

        // The name is present but the descriptor is not, so this pays the full constant pool parse
        // and method walk. The two-stage design exists precisely to keep this off the common path.
        long perParseNanos = bestOf((bytes, needle) -> DeclaredMethods.declares(bytes, "declares", "(I)V"),
                classBytes, ABSENT_NEEDLE, false);

        report("full descriptor check (the second stage)", perParseNanos, classBytes.length);

        assertTrue(perParseNanos < 1_000_000,
                "the descriptor check took " + perParseNanos + " ns, far outside the expected range");
    }

    @Test
    void theScanDoesNotScaleWithTheNeedleBeingLongerThanTheClass() {
        // Degenerate inputs return immediately; nothing here should ever be reached with them, but a
        // filter on the class-loading path is a bad place to be wrong about boundaries.
        assertTrue(!ByteScan.contains(new byte[] {1, 2, 3}, new byte[] {1, 2, 3, 4}));
        assertTrue(!ByteScan.contains(new byte[] {1, 2, 3}, new byte[0]));
        assertTrue(ByteScan.contains(new byte[] {1, 2, 3}, new byte[] {2, 3}));
    }

    // --- helpers -------------------------------------------------------------------------

    @FunctionalInterface
    private interface TwoArgProbe {
        boolean test(byte[] bytes, byte[] needle);
    }

    /**
     * Best of three rounds, each warmed up first. Taking the best rather than the average is the
     * usual way to stop a naive measurement from reporting a scheduling hiccup as the cost.
     *
     * <p>It also checks the result against what the operation is supposed to return, so an
     * implementation that accidentally always answered false could not make the timing look
     * wonderful.
     */
    private static long bestOf(TwoArgProbe probe, byte[] classBytes, byte[] needle, boolean expectedResult) {
        assertTrue(probe.test(classBytes, needle) == expectedResult,
                "the operation returned the wrong answer, so its timing means nothing");

        long best = Long.MAX_VALUE;
        // Kept small on purpose: this runs in every CI cell, and a smoke measurement that doubles
        // the build time is a cost paid by everyone for a number that only needs an order of
        // magnitude. JMH does the precise version.
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 5_000; i++) {
                blackhole(probe.test(classBytes, needle));
            }
            int iterations = 20_000;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                blackhole(probe.test(classBytes, needle));
            }
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed / iterations);
        }
        return best;
    }

    /** Keeps the JIT from removing the call being measured. */
    private static void blackhole(boolean value) {
        Blackhole.VALUE = value;
    }

    private static final class Blackhole {
        private static boolean VALUE;
    }

    private static void report(String what, long nanosPerOperation, int classSize) {
        long millisecondsPerThousandClasses = nanosPerOperation * 1_000L / 1_000_000L;
        System.out.println("[cost] " + what + ": " + nanosPerOperation + " ns per class of "
                + classSize + " bytes; " + millisecondsPerThousandClasses
                + " ms per 1000 classes loaded, per cut point");
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource);
            return in.readAllBytes();
        }
    }
}
