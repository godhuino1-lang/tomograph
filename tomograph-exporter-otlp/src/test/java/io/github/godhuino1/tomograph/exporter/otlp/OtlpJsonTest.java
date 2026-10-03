package io.github.godhuino1.tomograph.exporter.otlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Escaping is the one part of hand-rolled JSON that actually breaks things, so it gets
 * the edge cases rather than the happy path.
 */
class OtlpJsonTest {

    @Test
    void quotesPlainAscii() {
        assertEquals("\"hello\"", OtlpJson.quote("hello"));
    }

    @Test
    void escapesTheTwoMandatoryCharacters() {
        assertEquals("\"a\\\"b\"", OtlpJson.quote("a\"b"));
        assertEquals("\"a\\\\b\"", OtlpJson.quote("a\\b"));
    }

    @Test
    void usesShortEscapesForTheCommonControlCharacters() {
        assertEquals("\"a\\nb\"", OtlpJson.quote("a\nb"));
        assertEquals("\"a\\rb\"", OtlpJson.quote("a\rb"));
        assertEquals("\"a\\tb\"", OtlpJson.quote("a\tb"));
        assertEquals("\"a\\bb\"", OtlpJson.quote("a\bb"));
        assertEquals("\"a\\fb\"", OtlpJson.quote("a\fb"));
    }

    @Test
    void escapesRemainingControlCharactersAsFourDigitUnicode() {
        assertEquals("\"\\u0000\"", OtlpJson.quote("\u0000"));
        assertEquals("\"\\u001f\"", OtlpJson.quote("\u001f"));
        assertEquals("\"\\u0007\"", OtlpJson.quote("\u0007"));
    }

    @Test
    void leavesNonAsciiReadable() {
        // Escaping CJK into surrogate pairs would triple the payload for no benefit:
        // the body goes out as UTF-8 and JSON is defined over Unicode.
        assertEquals("\"能看到\"", OtlpJson.quote("能看到"));
    }

    @Test
    void keepsValidSurrogatePairsIntact() {
        String emoji = "\uD83D\uDE00"; // U+1F600
        assertEquals("\"" + emoji + "\"", OtlpJson.quote(emoji));
    }

    @Test
    void escapesUnpairedSurrogatesSoThePayloadStaysWellFormed() {
        // A Java String can legally hold half a surrogate pair. Emitted raw it becomes
        // invalid UTF-8 and a strict parser rejects the whole batch.
        assertEquals("\"\\ud83d\"", OtlpJson.quote("\uD83D"));
        assertEquals("\"\\ude00\"", OtlpJson.quote("\uDE00"));
    }

    @Test
    void handlesNull() {
        assertEquals("null", OtlpJson.quote(null));
    }
}
