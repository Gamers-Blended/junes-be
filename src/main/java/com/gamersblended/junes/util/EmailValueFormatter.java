package com.gamersblended.junes.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.gamersblended.junes.constant.ProductMetadataConstants.*;


@Component
public class EmailValueFormatter {

    @Value("${imageUrlPrefix:}")
    private String imageUrlPrefix;

    public String appendUrlPrefix(String productImageUrl) {
        if (null != productImageUrl) {
            return imageUrlPrefix + productImageUrl;
        }
        return productImageUrl;
    }

    public String formatPlatformName(String platformValue) {
        return switch (platformValue.toLowerCase()) {
            case PLAYSTATION_4 -> "PlayStation 4";
            case PLAYSTATION_5 -> "PlayStation 5";
            case XBOX_ONE -> "Xbox One";
            case XBOX_SERIES_X -> "Xbox Series X";
            case NINTENDO_SWITCH -> "Nintendo Switch";
            case NINTENDO_SWITCH_2 -> "Nintendo Switch 2";
            case PC -> "PC";
            default -> throw new IllegalStateException("Unexpected platform value: " + platformValue.toLowerCase());
        };
    }

    public String formatRegionName(String regionValue) {
        return switch (regionValue.toLowerCase()) {
            case ASIA -> "Asia";
            case US -> "United States";
            case EUR -> "Europe";
            default -> throw new IllegalStateException("Unexpected region value: " + regionValue.toLowerCase());
        };
    }

    public String formatEditionName(String editionValue) {
        return switch (editionValue.toLowerCase()) {
            case STD -> "Standard";
            case CE -> "Collector's";
            default -> throw new IllegalStateException("Unexpected edition value: " + editionValue.toLowerCase());
        };
    }
}
