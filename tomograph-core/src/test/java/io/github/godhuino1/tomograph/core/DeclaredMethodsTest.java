package io.github.godhuino1.tomograph.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the class file scanner against real bytecode - its own, mostly.
 *
 * <p>Reading the class's own bytes is a neat trick worth using: the input is real javac output with
 * real attribute tables, real constant pool ordering and no fixture to keep in sync, and a mistake
 * in the scanner shows up immediately rather than on some future class that happens to look unusual.
 */
class DeclaredMethodsTest {

    /** Has a field, a long, a double and a method: exercises the layouts the scanner has to skip. */
    @SuppressWarnings("unused")
    private static final class Sample {

        int counter;
        long bigNumber;
        double ratio;

        void doChat(String request) {
            // the body is irrelevant; the declaration is what is being read
        }

        int doChat(int request) {
            return request;
        }
    }

    @Test
    void findsAMethodThatIsDeclared() throws IOException {
        byte[] bytes = classBytes(DeclaredMethods.class);

        assertTrue(DeclaredMethods.declares(bytes, "declares",
                        "([BLjava/lang/String;Ljava/lang/String;)Z"),
                "the scanner did not find its own entry point");
    }

    @Test
    void theDescriptorHasToMatchNotJustTheName() throws IOException {
        byte[] bytes = classBytes(Sample.class);

        // Same name twice with different descriptors: exactly the shape that exists in the framework
        // this feature was built for, and the reason MethodCutPoint carries a descriptor at all.
        assertTrue(DeclaredMethods.declares(bytes, "doChat", "(Ljava/lang/String;)V"));
        assertTrue(DeclaredMethods.declares(bytes, "doChat", "(I)I"));

        assertFalse(DeclaredMethods.declares(bytes, "doChat", "(J)V"),
                "a descriptor that is not declared must not match");
        assertFalse(DeclaredMethods.declares(bytes, "doChat", "()Ljava/lang/String;"));
    }

    @Test
    void aFieldIsNotAMethod() throws IOException {
        byte[] bytes = classBytes(Sample.class);

        // "counter" is in the constant pool and has a descriptor, but it is a field. A scanner that
        // walked fields as methods would report it.
        assertFalse(DeclaredMethods.declares(bytes, "counter", "I"));
        assertFalse(DeclaredMethods.declares(bytes, "bigNumber", "J"));
    }

    @Test
    void survivesTheTwoSlotConstantsInItsOwnPool() throws IOException {
        byte[] bytes = classBytes(Sample.class);

        // Sample declares a long and a double, so its pool contains two-slot entries. Getting that
        // rule wrong shifts every later entry, and the symptom is a method that suddenly cannot be
        // found. This asserts the scanner still reads a method declared after them.
        assertTrue(DeclaredMethods.declares(bytes, "doChat", "(I)I"));
    }

    @Test
    void inheritedMethodsDoNotCount() throws IOException {
        // Sample extends Object, whose methods are not declared here.
        assertFalse(DeclaredMethods.declares(classBytes(Sample.class), "toString", "()Ljava/lang/String;"));
    }

    @Test
    void returnsFalseInsteadOfThrowingOnAnythingItDoesNotUnderstand() {
        // Every one of these runs while a class is being defined. Throwing would turn "I wanted to
        // observe this" into "this application will not start", so the answer is always false.
        assertFalse(DeclaredMethods.declares(null, "doChat", "()V"));
        assertFalse(DeclaredMethods.declares(new byte[0], "doChat", "()V"));
        assertFalse(DeclaredMethods.declares(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, "doChat", "()V"));
    }

    @Test
    void aTruncatedClassFileReturnsFalseRatherThanBlowingUp() throws IOException {
        byte[] whole = classBytes(DeclaredMethods.class);
        byte[] truncated = new byte[whole.length / 2];
        System.arraycopy(whole, 0, truncated, 0, truncated.length);

        assertFalse(DeclaredMethods.declares(truncated, "declares",
                "([BLjava/lang/String;Ljava/lang/String;)Z"));
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        // The fully qualified name, not the simple one: a nested class's resource is
        // Outer$Inner.class, so "Sample.class" would silently resolve to nothing.
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "could not read " + resource + " from the test classpath");
            return in.readAllBytes();
        }
    }
}
