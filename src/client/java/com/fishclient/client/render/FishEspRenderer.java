package com.fishclient.client.render;

import com.fishclient.client.ui.FishClickScreen;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FishEspRenderer {

    private static final List<BlockPos> STORAGE_CACHE = new ArrayList<>();
    private static BlockPos lastStorageCenter = BlockPos.ZERO;
    private static int lastStorageRange = -1;
    private static boolean lastIncludeEnder;
    private static boolean lastIncludeFurnaces;
    private static long lastStorageScanMs;

    private FishEspRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null || client.options.hideGui) {
            return;
        }

        boolean playerEspEnabled = isEnabled("PlayerESP");
        boolean storageEspEnabled = isEnabled("StorageESP");
        if (!playerEspEnabled && !storageEspEnabled) {
            return;
        }

        LocalPlayer self = client.player;

        if (playerEspEnabled) {
            renderPlayerEsp(client, self);
        }

        if (storageEspEnabled) {
            renderStorageEsp(client, self);
        }
    }

    private static void renderPlayerEsp(Minecraft client, LocalPlayer self) {
        double range = clamp(getSlider("PlayerESP", "Range", 80.0), 8.0, 160.0);
        int mode = getSelectIndex("PlayerESP", "Mode", 0);
        int drawStyle = getSelectIndex("PlayerESP", "Draw Style", 0);
        boolean tracers = getToggle("PlayerESP", "Tracers", true);
        double maxDistanceSq = range * range;
        long now = System.currentTimeMillis();

        for (Player target : client.level.players()) {
            if (target == null || target == self || target.isRemoved() || target.isSpectator()) {
                continue;
            }

            if (self.distanceToSqr(target) > maxDistanceSq) {
                continue;
            }

            int strokeColor = playerStrokeColor(mode, target, now);
            int fillColor = playerFillColor(mode, target, now);
            if (drawStyle == 1) {
                fillColor = withAlpha(fillColor, 0);
            } else if (drawStyle == 2) {
                strokeColor = withAlpha(strokeColor, 0);
            }
            float strokeWidth = mode == 1 ? 0.95F : 1.35F;
            double inflate = mode == 1 ? 0.035D : 0.07D;

            AABB box = target.getBoundingBox().inflate(inflate, 0.09D, inflate);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeColor, strokeWidth, fillColor)).persistForMillis(160);

            if (mode == 0 || mode == 3) {
                Vec3 marker = target.getBoundingBox().getCenter().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
                Gizmos.point(marker, withAlpha(strokeColor, 220), 4.0F).persistForMillis(130);
            }

            if (tracers && drawStyle != 2) {
                Vec3 from = self.getEyePosition();
                Vec3 to = target.getEyePosition();
                float tracerWidth = mode == 1 ? 0.8F : 1.1F;
                Gizmos.line(from, to, withAlpha(strokeColor, 230), tracerWidth).persistForMillis(120);
            }
        }
    }

    private static void renderStorageEsp(Minecraft client, LocalPlayer self) {
        int range = (int) Math.round(clamp(getSlider("StorageESP", "Range", 16.0), 4.0, 32.0));
        int mode = getSelectIndex("StorageESP", "Mode", 0);
        boolean includeEnder = getToggle("StorageESP", "Include Ender", true);
        boolean includeFurnaces = getToggle("StorageESP", "Include Furnaces", true);
        refreshStorageCache(client, self.blockPosition(), range, includeEnder, includeFurnaces);

        double maxDistanceSq = (double) range * (double) range;
        long now = System.currentTimeMillis();

        for (BlockPos pos : STORAGE_CACHE) {
            if (pos == null || self.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > maxDistanceSq) {
                continue;
            }

            BlockState state = client.level.getBlockState(pos);
            if (state == null || state.isAir()) {
                continue;
            }

            int baseColor = storageColor(state.getBlock());
            int strokeColor = storageStrokeColor(mode, baseColor, pos, now);
            int fillColor = storageFillColor(mode, baseColor, pos, now);
            float width = mode == 1 ? 0.85F : 1.2F;
            AABB box = new AABB(pos).inflate(0.002D);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeColor, width, fillColor)).persistForMillis(220);

            if (mode == 0 || mode == 2) {
                Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.02D, pos.getZ() + 0.5D);
                Gizmos.point(center, withAlpha(strokeColor, 220), 2.6F).persistForMillis(130);
            }
        }
    }

    private static void refreshStorageCache(Minecraft client, BlockPos center, int range, boolean includeEnder, boolean includeFurnaces) {
        long now = System.currentTimeMillis();
        boolean movedFar = center.distManhattan(lastStorageCenter) > 2;
        boolean settingsChanged = lastStorageRange != range
            || lastIncludeEnder != includeEnder
            || lastIncludeFurnaces != includeFurnaces;
        if (!settingsChanged && !movedFar && now - lastStorageScanMs < 350L) {
            return;
        }

        STORAGE_CACHE.clear();
        int verticalRange = Math.max(6, range / 2);
        int minX = center.getX() - range;
        int maxX = center.getX() + range;
        int minY = center.getY() - verticalRange;
        int maxY = center.getY() + verticalRange;
        int minZ = center.getZ() - range;
        int maxZ = center.getZ() + range;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = client.level.getBlockState(cursor);
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    if (!isStorageBlock(state.getBlock(), includeEnder, includeFurnaces)) {
                        continue;
                    }
                    STORAGE_CACHE.add(cursor.immutable());
                }
            }
        }

        lastStorageCenter = center.immutable();
        lastStorageRange = range;
        lastIncludeEnder = includeEnder;
        lastIncludeFurnaces = includeFurnaces;
        lastStorageScanMs = now;
    }

    private static boolean isStorageBlock(Block block, boolean includeEnder, boolean includeFurnaces) {
        if (block instanceof ChestBlock
            || block instanceof BarrelBlock
            || block instanceof ShulkerBoxBlock
            || block instanceof HopperBlock
            || block instanceof DispenserBlock
            || block instanceof DropperBlock) {
            return true;
        }
        if (includeEnder && block instanceof EnderChestBlock) {
            return true;
        }
        return includeFurnaces && block instanceof AbstractFurnaceBlock;
    }

    private static int storageColor(Block block) {
        if (block instanceof EnderChestBlock) {
            return 0xFFBE74FF;
        }
        if (block instanceof AbstractFurnaceBlock) {
            return 0xFFFFA240;
        }
        return 0xFF48E6DB;
    }

    private static int playerStrokeColor(int mode, Player target, long now) {
        if (mode == 1) {
            return 0xFF8CB2FF;
        }
        if (mode == 2) {
            float hp = target.getMaxHealth() <= 0.0F ? 0.0F : clamp01(target.getHealth() / target.getMaxHealth());
            return healthColor(hp);
        }
        if (mode == 3) {
            return chromaColor(now, target.getId() * 113L, 0.92F, 1.0F);
        }
        return 0xFFE56BFF;
    }

    private static int playerFillColor(int mode, Player target, long now) {
        if (mode == 1) {
            return 0x224A7FE6;
        }
        if (mode == 2) {
            float hp = target.getMaxHealth() <= 0.0F ? 0.0F : clamp01(target.getHealth() / target.getMaxHealth());
            return withAlpha(healthColor(hp), 58);
        }
        if (mode == 3) {
            return withAlpha(chromaColor(now, target.getId() * 171L, 0.88F, 1.0F), 54);
        }
        return 0x33D96BFF;
    }

    private static int storageStrokeColor(int mode, int baseColor, BlockPos pos, long now) {
        if (mode == 1) {
            return withAlpha(brighten(baseColor, 0.94F), 255);
        }
        if (mode == 2) {
            return chromaColor(now, pos.asLong() & 0x3FFFL, 0.86F, 1.0F);
        }
        return withAlpha(brighten(baseColor, 1.08F), 255);
    }

    private static int storageFillColor(int mode, int baseColor, BlockPos pos, long now) {
        if (mode == 1) {
            return withAlpha(brighten(baseColor, 0.82F), 42);
        }
        if (mode == 2) {
            return withAlpha(chromaColor(now, pos.asLong() & 0x7FFFL, 0.78F, 0.95F), 40);
        }
        return withAlpha(baseColor, 48);
    }

    private static int healthColor(float ratio) {
        float v = clamp01(ratio);
        int red = (int) (255.0F * (1.0F - v));
        int green = (int) (255.0F * v);
        int blue = 72;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int chromaColor(long nowMs, long phase, float saturation, float value) {
        float hue = (float) ((nowMs + phase) % 4500L) / 4500.0F;
        int rgb = Mth.hsvToRgb(hue, clamp01(saturation), clamp01(value));
        return 0xFF000000 | rgb;
    }

    private static int brighten(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) Math.min(255.0F, r * factor);
        g = (int) Math.min(255.0F, g * factor);
        b = (int) Math.min(255.0F, b * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static boolean isEnabled(String moduleName) {
        FishClickScreen.ModuleEntry module = findModule(moduleName);
        return module != null && module.enabled;
    }

    private static double getSlider(String moduleName, String settingName, double fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null) {
            return fallback;
        }
        return setting.sliderValue;
    }

    private static boolean getToggle(String moduleName, String settingName, boolean fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null) {
            return fallback;
        }
        return setting.boolValue;
    }

    private static int getSelectIndex(String moduleName, String settingName, int fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null || setting.selectOptions == null || setting.selectOptions.length == 0) {
            return fallback;
        }
        return Math.max(0, Math.min(setting.selectIndex, setting.selectOptions.length - 1));
    }

    private static FishClickScreen.ModuleEntry findModule(String moduleName) {
        for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
            if (module.name.equalsIgnoreCase(moduleName)) {
                return module;
            }
        }
        return null;
    }

    private static FishClickScreen.SettingEntry findSetting(String moduleName, String settingName) {
        FishClickScreen.ModuleEntry module = findModule(moduleName);
        if (module == null) {
            return null;
        }
        for (FishClickScreen.SettingEntry setting : module.settings) {
            if (setting.label.equalsIgnoreCase(settingName)) {
                return setting;
            }
        }
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
