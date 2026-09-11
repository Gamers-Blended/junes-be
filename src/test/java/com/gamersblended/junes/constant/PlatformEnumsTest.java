package com.gamersblended.junes.constant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformEnumsTest {

    @ParameterizedTest
    @EnumSource(PlatformEnums.class)
    void isValidPlatformValue_returnsTrue_forEveryKnownPlatformValue(PlatformEnums platform) {
        assertThat(PlatformEnums.isValidPlatformValue(platform.getPlatformValue())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wii", "PS5", "ps-5", "switch"})
    void isValidPlatformValue_returnsFalse_forUnknownOrMiscasedValue(String platformValue) {
        assertThat(PlatformEnums.isValidPlatformValue(platformValue)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isValidPlatformValue_returnsFalse_forNullOrEmptyValue(String platformValue) {
        assertThat(PlatformEnums.isValidPlatformValue(platformValue)).isFalse();
    }
}
