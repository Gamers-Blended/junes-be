package com.gamersblended.junes.constant;

public enum PlatformEnums {
    PLAYSTATION_4("playstation_4"),
    PLAYSTATION_5("playstation_5"),
    XBOX_ONE("xbox_one"),
    XBOX_SERIES_X("xbox_series_x"),
    NINTENDO_SWITCH("nintendo_switch"),
    NINTENDO_SWITCH_2("nintendo_switch_2"),
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
