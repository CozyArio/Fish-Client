package com.fishclient.modules;

public enum Category {
    VISUAL("Visual"),
    HUD("HUD"),
    PLAYER("Player"),
    MISC("Misc"),
    RADIO("Radio");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

