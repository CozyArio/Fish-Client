package com.fishclient.module.core;

import com.fishclient.module.Category;
import com.fishclient.module.api.ClientModule;

public class BaseModule implements ClientModule {

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean external;
    private boolean enabled;

    public BaseModule(String id, String name, String description, Category category, boolean enabledByDefault, boolean external) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
        this.external = external;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Category category() {
        return category;
    }

    @Override
    public boolean isExternal() {
        return external;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }
}
