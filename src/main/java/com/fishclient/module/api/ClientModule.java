package com.fishclient.module.api;

import com.fishclient.module.Category;

public interface ClientModule {

    String id();

    String name();

    String description();

    Category category();

    boolean isExternal();

    boolean enabled();

    void setEnabled(boolean enabled);

    default void toggle() {
        setEnabled(!enabled());
    }

    default void onEnable() {
    }

    default void onDisable() {
    }

    default void onClientTick() {
    }
}
