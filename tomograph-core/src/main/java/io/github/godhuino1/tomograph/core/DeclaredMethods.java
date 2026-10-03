package io.github.godhuino1.tomograph.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Answers one narrow question about a class's own bytes: <b>does it declare a method with this exact
 * name and descriptor?</b>
 *
 * <h2>Why this is hand-written instead of ASM</h2>
 *
 * <p>ASM would do it in three lines, and ASM is already in this project. But {@code tomograph-core}
 * deliberately has no dependency at all: it is loaded into somebody else's JVM, and every artefact
 * added to its tree is paid for by every application that turns instrumentation on (ADR 0003). The
 * question here is narrow enough that a dependency is not worth its price, and the format is one
 * this project already owns: the class file layout, and the two-slot rule for long/double in the
 * constant pool.
 *
 * <h2>How it behaves on input it does not understand</h2>
 *
 * <p>Every failure path returns {@code false}. This runs while a class is being defined, so throwing
 * would mean "I wanted to observe this" becoming "this application will not start". An unknown
 * constant pool tag, a truncated file, an index pointing at the wrong kind of entry: all of them
 * mean "not a cut point", never an exception.
 *
 * <p>The consequence to be aware of is that a class this scanner fails to read is a class the module
 * never sees, silently. That is the acceptable side of the trade-off stated in
 * {@link io.github.godhuino1.tomograph.api.TomographModule#targetMethods()}: missing data beats a
 * broken host.
 */
final class DeclaredMethods {

    private static final int MAGIC = 0xCAFEBABE;

    private DeclaredMethods() {
    }

    /**
     * True if the class declares a method whose name and descriptor both match.
     *
     * <p>Only methods <em>declared by this class</em> count; an inherited method is declared by its
     * superclass, and that class is transformed separately when it loads.
     */
    static boolean declares(byte[] classBytes, String name, String descriptor) {
        if (classBytes == null || classBytes.length < 10) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (in.readInt() != MAGIC) {
                return false;
            }
            in.readUnsignedShort();                       // minor version
            in.readUnsignedShort();                       // major version
            int poolCount = in.readUnsignedShort();
            String[] utf8 = readConstantPool(in, poolCount);
            if (utf8 == null) {
                return false;
            }

            in.readUnsignedShort();                       // access_flags
            in.readUnsignedShort();                       // this_class
            in.readUnsignedShort();                       // super_class
            in.skipNBytes(in.readUnsignedShort() * 2L);   // interfaces

            int fieldCount = in.readUnsignedShort();
            for (int i = 0; i < fieldCount; i++) {
                skipMember(in);
            }

            int methodCount = in.readUnsignedShort();
            for (int i = 0; i < methodCount; i++) {
                in.readUnsignedShort();                   // access_flags
                String declaredName = utf8At(utf8, in.readUnsignedShort());
                String declaredDescriptor = utf8At(utf8, in.readUnsignedShort());
                skipAttributes(in);
                if (name.equals(declaredName) && descriptor.equals(declaredDescriptor)) {
                    return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            // Includes EOFException from a truncated file and the IllegalArgumentException that
            // inner.skipNBytes throws on a negative count. Observation must never be the reason a
            // class fails to load.
            return false;
        }
    }

    /** Returns the UTF-8 entries by constant pool index, or null if a tag is not understood. */
    private static String[] readConstantPool(DataInputStream in, int poolCount) throws IOException {
        String[] utf8 = new String[poolCount];
        for (int i = 1; i < poolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 7, 8, 16, 19, 20 -> in.readUnsignedShort();
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 5, 6 -> {
                    // Long and Double occupy TWO constant pool slots; the second is unusable. This
                    // is the same rule as the first exercise, and forgetting it misaligns every
                    // entry that follows.
                    in.readLong();
                    i++;
                }
                default -> {
                    return null;
                }
            }
        }
        return utf8;
    }

    /** Skips a field_info or method_info body up to (not including) its own name/descriptor. */
    private static void skipMember(DataInputStream in) throws IOException {
        in.readUnsignedShort();                            // access_flags
        in.readUnsignedShort();                            // name_index
        in.readUnsignedShort();                            // descriptor_index
        skipAttributes(in);
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();                        // attribute_name_index
            in.skipNBytes(in.readInt());                   // attribute_length is u4
        }
    }

    private static String utf8At(String[] utf8, int index) {
        return (index >= 0 && index < utf8.length) ? utf8[index] : null;
    }
}
