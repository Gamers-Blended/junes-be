package com.gamersblended.junes.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailValueFormatterTest {

    private final EmailValueFormatter formatter = new EmailValueFormatter();

    @Test
    void appendUrlPrefix_prependsConfiguredPrefix() {
        ReflectionTestUtils.setField(formatter, "imageUrlPrefix", "https://cdn.junes.com/");

        assertThat(formatter.appendUrlPrefix("games/product1.jpg"))
                .isEqualTo("https://cdn.junes.com/games/product1.jpg");
    }

    @Test
    void appendUrlPrefix_returnsNullForNullInput() {
        ReflectionTestUtils.setField(formatter, "imageUrlPrefix", "https://cdn.junes.com/");

        assertThat(formatter.appendUrlPrefix(null)).isNull();
    }

    @Test
    void appendUrlPrefix_returnsUnprefixedUrlWhenPrefixNotConfigured() {
        ReflectionTestUtils.setField(formatter, "imageUrlPrefix", "");

        assertThat(formatter.appendUrlPrefix("games/product1.jpg")).isEqualTo("games/product1.jpg");
    }

    @Test
    void formatPlatformName_mapsAllKnownPlatformCodes() {
        assertThat(formatter.formatPlatformName("ps4")).isEqualTo("PlayStation 4");
        assertThat(formatter.formatPlatformName("ps5")).isEqualTo("PlayStation 5");
        assertThat(formatter.formatPlatformName("xbo")).isEqualTo("Xbox One");
        assertThat(formatter.formatPlatformName("xsx")).isEqualTo("Xbox Series X");
        assertThat(formatter.formatPlatformName("nsw")).isEqualTo("Nintendo Switch");
        assertThat(formatter.formatPlatformName("nsw2")).isEqualTo("Nintendo Switch 2");
        assertThat(formatter.formatPlatformName("pc")).isEqualTo("PC");
    }

    @Test
    void formatPlatformName_isCaseInsensitive() {
        assertThat(formatter.formatPlatformName("PS5")).isEqualTo("PlayStation 5");
    }

    @Test
    void formatPlatformName_rejectsUnknownPlatformCode() {
        assertThatThrownBy(() -> formatter.formatPlatformName("dreamcast"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected platform value: dreamcast");
    }

    @Test
    void formatRegionName_mapsAllKnownRegionCodes() {
        assertThat(formatter.formatRegionName("asia")).isEqualTo("Asia");
        assertThat(formatter.formatRegionName("us")).isEqualTo("United States");
        assertThat(formatter.formatRegionName("eur")).isEqualTo("Europe");
        assertThat(formatter.formatRegionName("jp")).isEqualTo("Japan");
    }

    @Test
    void formatRegionName_isCaseInsensitive() {
        assertThat(formatter.formatRegionName("US")).isEqualTo("United States");
    }

    @Test
    void formatRegionName_rejectsUnknownRegionCode() {
        assertThatThrownBy(() -> formatter.formatRegionName("oceania"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected region value: oceania");
    }

    @Test
    void formatEditionName_mapsAllKnownEditionCodes() {
        assertThat(formatter.formatEditionName("std")).isEqualTo("Standard");
        assertThat(formatter.formatEditionName("se")).isEqualTo("Special");
        assertThat(formatter.formatEditionName("ce")).isEqualTo("Collector's");
        assertThat(formatter.formatEditionName("dlx_e")).isEqualTo("Deluxe");
        assertThat(formatter.formatEditionName("ge")).isEqualTo("Gold");
    }

    @Test
    void formatEditionName_isCaseInsensitive() {
        assertThat(formatter.formatEditionName("STD")).isEqualTo("Standard");
    }

    @Test
    void formatEditionName_rejectsUnknownEditionCode() {
        assertThatThrownBy(() -> formatter.formatEditionName("goty"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected edition value: goty");
    }
}
