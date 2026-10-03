package io.github.godhuino1.tomograph.core;

/**
 * A literal byte search over a class file — the cheap first stage of signature routing.
 *
 * <h2>Why searching for a string is a sound filter, not just a fast one</h2>
 *
 * <p>Routing by method signature means the engine is handed a class it has never heard of and must
 * decide whether that class declares a particular method. Parsing every loaded class would be
 * unacceptable on somebody else's class-loading path, so the question is answered in two stages:
 * this scan first, and {@link DeclaredMethods} only if the scan hits.
 *
 * <p>The order is only valid because the first stage <b>cannot miss</b>. A method that a class
 * declares has its name in that class's constant pool by construction (JVMS 4.6: {@code method_info}
 * refers to a {@code CONSTANT_Utf8}), so a literal search for the name will find it. False positives
 * are possible and harmless — they cost one parse. False negatives are impossible, which is what
 * makes this a filter rather than a guess.
 *
 * <h2>Deliberately naive, and measured rather than assumed</h2>
 *
 * <p>The implementation is a plain nested loop, no allocation, no exceptions: the needle is a method
 * name (a handful of bytes) and the haystack is one class file. {@code RoutingCostTest} measures it
 * and asserts a generous ceiling, so a change that accidentally makes it quadratic fails the build
 * instead of quietly slowing every application that uses the agent.
 *
 * <p>Measured on the development machine (JDK 26, Windows): an 8.9 KB class where the name is
 * <b>absent</b> takes about <b>8 µs</b> — that is the worst case, because the scan must traverse the
 * whole file; a 3.1 KB class where the name is <b>present</b> takes about <b>0.75 µs</b>, exiting at
 * the hit. For an application loading ten thousand classes that is roughly 80 ms spread across
 * startup — small next to loading and verifying those classes at all, but <b>linear in the number of
 * signature cut points</b>, which is exactly why the SPI asks modules to declare them only where a
 * class-name list genuinely cannot work.
 *
 * <p>The same measurement puts the second stage at about 16 µs on that class, only twice the scan.
 * So the filter's value is not that parsing is catastrophically slow; it is that most classes never
 * reach it. If several cut points ever ship, the obvious optimisation is to test all names in one
 * pass rather than one pass per name, and the numbers above are what that decision should be made
 * against.
 *
 * <p>This is a smoke measurement, not a benchmark — a real JMH run is on the v0.1 list next to the
 * instrumentation-overhead figure, and it is the honest way to answer "what does this cost".
 */
final class ByteScan {

    private ByteScan() {
    }

    /** True if {@code needle} occurs anywhere in {@code haystack}. */
    static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        int last = haystack.length - needle.length;
        outer:
        for (int start = 0; start <= last; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
