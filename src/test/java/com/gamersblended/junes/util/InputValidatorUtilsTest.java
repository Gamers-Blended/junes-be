package com.gamersblended.junes.util;

import org.junit.jupiter.api.Test;

import static com.gamersblended.junes.util.InputValidatorUtils.sanitizeString;
import static org.assertj.core.api.Assertions.assertThat;

class InputValidatorUtilsTest {

    @Test
    void sanitizeString_returnsNullForNullInput() {
        assertThat(sanitizeString(null)).isNull();
    }

    @Test
    void sanitizeString_returnsNullForEmptyString() {
        assertThat(sanitizeString("")).isNull();
    }

    @Test
    void sanitizeString_returnsNullForBlankString() {
        assertThat(sanitizeString("   ")).isNull();
    }

    @Test
    void sanitizeString_returnsNullForStringOfOnlyNullBytes() {
        assertThat(sanitizeString("\0\0\0")).isNull();
    }

    @Test
    void sanitizeString_leavesCleanStringUnchanged() {
        assertThat(sanitizeString("John Doe")).isEqualTo("John Doe");
    }

    @Test
    void sanitizeString_trimsLeadingAndTrailingWhitespace() {
        assertThat(sanitizeString("  John Doe  ")).isEqualTo("John Doe");
    }

    @Test
    void sanitizeString_removesNullBytes() {
        assertThat(sanitizeString("John\0 Doe")).isEqualTo("John Doe");
    }

    @Test
    void sanitizeString_collapsesRepeatedInternalWhitespaceToSingleSpace() {
        assertThat(sanitizeString("John    Doe")).isEqualTo("John Doe");
    }

    @Test
    void sanitizeString_normalizesTabsAndNewlinesToSingleSpace() {
        assertThat(sanitizeString("John\t\nDoe")).isEqualTo("John Doe");
    }

    @Test
    void sanitizeString_preservesPunctuationAndCasing() {
        assertThat(sanitizeString("  O'Brien-Smith, Jr.  ")).isEqualTo("O'Brien-Smith, Jr.");
    }
}
