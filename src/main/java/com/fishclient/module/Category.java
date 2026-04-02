package com.fishclient.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    MISC("Misc"),
    VISUAL("Visual"),
    OVERLAY("Overlay"),
    THEME("Theme"),
    RADIO("Radio");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Category fromString(String value, Category fallback) {
        if (value == null) {
            return fallback;
        }

        for (Category category : values()) {
            if (category.name().equalsIgnoreCase(value) || category.displayName.equalsIgnoreCase(value)) {
                return category;
            }
        }
        return fallback;
    }
}
