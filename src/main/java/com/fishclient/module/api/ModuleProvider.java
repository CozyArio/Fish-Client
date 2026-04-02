package com.fishclient.module.api;

import com.fishclient.module.core.ModuleRegistry;

public interface ModuleProvider {

    void registerModules(ModuleRegistry registry);
}
