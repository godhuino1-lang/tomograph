package io.github.godhuino1.tomograph.exporter.otlp;

/**
 * The smallest correct JSON string encoder we can get away with.
 *
 * <p>Hand-rolling JSON is usually a bad idea. It is justified here only because the
 * input is entirely under our control (attribute keys and values produced by our own
 * instrumentation) and the output must be byte-exact for a wire protocol. The single
 * genuinely tricky part — escaping — is isolated in one method and tested against the
 * cases that actually break naive implementations.
 *
 * <p>Deliberate choices:
 * <ul>
 *   <li><b>Non-ASCII is emitted as-is</b>, not as {@code \\uXXXX}. JSON is defined over
 *       Unicode and the payload is sent as UTF-8, so escaping Chinese text into
 *       surrogate pairs would only triple its size.</li>
 *   <li><b>Solidus ({@code /}) is not escaped.</b> Escaping it is legal but pointless,
 *       and it makes output harder to eyeball.</li>
 *   <li><b>Unpaired surrogates are escaped.</b> A Java {@code String} can hold a lone
 *       surrogate, which is not valid UTF-8. Encoding it directly would produce a
 *       payload that a strict parser rejects — or worse, silently mangles. Escaping it
 *       to {@code \\uD800} at least keeps the JSON well-formed.</li>
 * </ul>
 */
final class OtlpJson {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private OtlpJson() {
    }

    /** Returns the JSON representation of {@code value}, including the surrounding quotes. */
    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        appendUnicodeEscape(out, c);
                    } else if (Character.isHighSurrogate(c)) {
                        // Valid only when immediately followed by a low surrogate.
                        if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                            out.append(c).append(value.charAt(++i));
                        } else {
                            appendUnicodeEscape(out, c);
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        // Reached only if it was not consumed by a preceding high surrogate.
                        appendUnicodeEscape(out, c);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static void appendUnicodeEscape(StringBuilder out, char c) {
        out.append("\\u")
                .append(HEX[(c >> 12) & 0xF])
                .append(HEX[(c >> 8) & 0xF])
                .append(HEX[(c >> 4) & 0xF])
                .append(HEX[c & 0xF]);
    }
}
