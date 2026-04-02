package com.fishclient.module.core;

import com.fishclient.module.Category;

public final class ExternalModuleSpec {

    public String id;
    public String name;
    public String description;
    public String category;
    public Boolean enabledByDefault;

    public String normalizedId() {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        String normalized = id.trim().toLowerCase().replace(' ', '_');
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                builder.append(c);
            }
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    public String resolvedName() {
        if (name == null || name.trim().isEmpty()) {
            return normalizedId();
        }
        return name.trim();
    }

    public String resolvedDescription() {
        if (description == null || description.trim().isEmpty()) {
            return "External module";
        }
        return description.trim();
    }

    public Category resolvedCategory() {
        return Category.fromString(category, Category.MISC);
    }

    public boolean enabledByDefault() {
        return enabledByDefault != null && enabledByDefault;
    }
}
