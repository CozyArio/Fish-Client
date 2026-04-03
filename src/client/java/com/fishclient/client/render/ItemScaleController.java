package com.fishclient.client.render;

import com.fishclient.client.ui.FishClickScreen;

import java.util.List;

public final class ItemScaleController {

    private ItemScaleController() {
    }

    public static float getItemScale() {
        FishClickScreen.ModuleEntry module = findItemScaleModule();
        if (module == null || !module.enabled || module.settings == null) {
            return 1.0f;
        }

        for (FishClickScreen.SettingEntry setting : module.settings) {
            if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.SLIDER) {
                continue;
            }
            if (!"Scale".equalsIgnoreCase(setting.label)) {
                continue;
            }
            double value = setting.sliderValue;
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 1.0f;
            }
            return (float) clamp(value, 0.1, 3.0);
        }

        return 1.0f;
    }

    private static FishClickScreen.ModuleEntry findItemScaleModule() {
        List<FishClickScreen.ModuleEntry> modules = FishClickScreen.SHARED_MODULES;
        for (FishClickScreen.ModuleEntry module : modules) {
            if (module != null && "ItemScale".equalsIgnoreCase(module.name)) {
                return module;
            }
        }
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

