package com.gamersblended.junes.constant;

public enum PlatformEnums {
    PLAYSTATION_4("ps4"),
    PLAYSTATION_5("ps5"),
    XBOX_ONE("xbo"),
    XBOX_SERIES_X("xsx"),
    NINTENDO_SWITCH("nsw"),
    NINTENDO_SWITCH_2("nsw2"),
    PC("pc");

    private final String platformValue;

    PlatformEnums(String platformValue) {
        this.platformValue = platformValue;
    }

    public String getPlatformValue() {
        return platformValue;
    }

    // Static method to check if platform value is valid
    public static boolean isValidPlatformValue(String platformValue) {
        for (PlatformEnums platform : PlatformEnums.values()) {
            if (platform.getPlatformValue().equals(platformValue)) {
                return true;
            }
        }
        return false;
    }
}
