package com.fishclient.client.runtime;

import com.fishclient.client.cloud.CloudBridge;
import com.fishclient.client.ui.FishClickScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GameplayModulesRuntime {

    private long lastNukerMs;
    private long lastPrisonMs;
    private long lastRangeInteractMs;
    private long lastPayAllMs;
    private boolean wingsApplied;

    private BlockPos nukerPacketTarget;
    private Direction nukerPacketFace = Direction.UP;
    private BlockPos rangeMineTarget;
    private Direction rangeMineFace = Direction.UP;

    private BlockPos prisonPacketTarget;
    private Direction prisonPacketFace = Direction.UP;

    private boolean prisonCursorReady;
    private int prisonCursorX;
    private int prisonCursorY;
    private int prisonCursorZ;
    private boolean prisonCursorFinished;
    private String prisonCursorSignature = "";
    private String lastLookedBlockId = "";
    private final List<String> payAllQueue = new ArrayList<>();
    private int payAllIndex;
    private boolean payAllRunning;
    private String payAllAmount = "";
    private final List<String> payAllScannedTargets = new ArrayList<>();
    private final Set<BlockPos> rebreakSelections = new LinkedHashSet<>();
    private BlockPos rebreakPos1;
    private BlockPos rebreakPos2;
    private String rebreakPos1Display = "-";
    private String rebreakPos2Display = "-";
    private boolean rebreakSelectHeld;
    private long lastRebreakMs;
    private long rebreakSelectionGuardUntilMs;
    private BlockPos rebreakPacketTarget;
    private Direction rebreakPacketFace = Direction.UP;

    public void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return;
        }

        applyWings(client);
        applyFishMode(client.player);
        applyRangeExtender(client);
        tickRebreak(client);
        tickNuker(client);
        tickPrisonLayerMiner(client);
        tickPayAll(client);
    }

    private void applyWings(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        boolean enabled = isEnabled("Wings");
        if (!enabled) {
            if (wingsApplied && !player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            wingsApplied = false;
            return;
        }

        wingsApplied = true;
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;

        float speed = (float) clamp(getSlider("Wings", "Speed", 1.0) / 10.0, 0.05, 0.9);
        player.getAbilities().setFlyingSpeed(speed);

        double vertical = clamp(getSlider("Wings", "Vertical", 0.8), 0.1, 3.0) * 0.08;
        if (player.getAbilities().flying) {
            Vec3 velocity = player.getDeltaMovement();
            if (client.options.keyJump.isDown() && !client.options.keyShift.isDown()) {
                player.setDeltaMovement(velocity.x, vertical, velocity.z);
            } else if (client.options.keyShift.isDown() && !client.options.keyJump.isDown()) {
                player.setDeltaMovement(velocity.x, -vertical, velocity.z);
            }
        }

        player.onUpdateAbilities();
    }

    private void applyFishMode(LocalPlayer player) {
        boolean enabled = isEnabled("FISH MODE") || isEnabled("NoFall");
        if (!enabled) {
            return;
        }

        boolean falling = player.fallDistance > 2.0F || (!player.onGround() && player.getDeltaMovement().y < -0.08D);
        player.fallDistance = 0.0F;
        player.setOnGround(true);

        if (falling && player.connection != null) {
            player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision));
        }
    }

    private void applyRangeExtender(Minecraft client) {
        LocalPlayer player = client.player;
        Level level = client.level;
        if (!isEnabled("Range Extender")) {
            clearRangeMineTarget(client);
            return;
        }
        if (player == null || level == null || client.gameMode == null) {
            clearRangeMineTarget(client);
            return;
        }

        double range = clamp(getSlider("Range Extender", "Range", 8.0), 4.0, 30.0);
        int actionMode = getSelectIndex("Range Extender", "Action", 0);
        boolean packetMine = getToggle("Range Extender", "Packet Mine", true);

        boolean allowMine = actionMode == 0 || actionMode == 1;
        boolean allowInteract = actionMode == 0 || actionMode == 2;

        HitResult result = player.pick(range, 0.0F, false);
        if (!(result instanceof BlockHitResult blockHitResult)) {
            clearRangeMineTarget(client);
            return;
        }

        if (allowInteract && client.options.keyUse.isDown()) {
            long now = System.currentTimeMillis();
            if (now - lastRangeInteractMs >= 130L) {
                lastRangeInteractMs = now;
                client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHitResult);
                player.swing(InteractionHand.MAIN_HAND);
            }
        }

        if (!allowMine || !client.options.keyAttack.isDown()) {
            clearRangeMineTarget(client);
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            clearRangeMineTarget(client);
            return;
        }

        Direction face = blockHitResult.getDirection();
        if (face == null) {
            face = getMiningFace(player, pos);
        }

        if (!packetMine) {
            client.gameMode.startDestroyBlock(pos, face);
            client.gameMode.continueDestroyBlock(pos, face);
            player.swing(InteractionHand.MAIN_HAND);
            return;
        }

        if (rangeMineTarget == null || !rangeMineTarget.equals(pos)) {
            clearRangeMineTarget(client);
            rangeMineTarget = pos.immutable();
            rangeMineFace = face;
            client.gameMode.startDestroyBlock(rangeMineTarget, rangeMineFace);
        }
        client.gameMode.continueDestroyBlock(rangeMineTarget, rangeMineFace);
        player.swing(InteractionHand.MAIN_HAND);
        if (level.getBlockState(rangeMineTarget).isAir()) {
            clearRangeMineTarget(client);
        }
    }

    private void tickRebreak(Minecraft client) {
        FishClickScreen.ModuleEntry module = findModule("Rebreak");
        if (module == null || client == null || client.player == null || client.level == null || client.gameMode == null) {
            return;
        }

        boolean selectionUsed = handleRebreakSelectionInput(client, module);
        long now = System.currentTimeMillis();
        if (selectionUsed) {
            clearRebreakPacketTarget(client);
            lastRebreakMs = now;
            rebreakSelectionGuardUntilMs = now + 250L;
        }

        if (!module.enabled) {
            clearRebreakPacketTarget(client);
            return;
        }

        double range = clamp(getSlider("Rebreak", "Range", 8.0), 1.0, 30.0);
        long delay = (long) clamp(getSlider("Rebreak", "Delay ms", 25.0), 0.0, 2000.0);
        int blocksPerTick = (int) Math.round(clamp(getSlider("Rebreak", "Blocks Per Tick", 8.0), 1.0, 96.0));
        int mineMode = getSelectIndex("Rebreak", "Mine Mode", 0);
        if (now < rebreakSelectionGuardUntilMs) {
            return;
        }

        if (mineMode == 0) {
            tickPacketRebreak(client, range, delay);
            return;
        }

        clearRebreakPacketTarget(client);
        if (now - lastRebreakMs < delay) {
            return;
        }
        lastRebreakMs = now;

        List<BlockPos> targets = getRebreakMineableTargets(client, range, blocksPerTick);
        for (BlockPos pos : targets) {
            aggressiveMine(client, pos);
        }
    }

    private void tickPacketRebreak(Minecraft client, double range, long delay) {
        LocalPlayer player = client.player;
        Level level = client.level;
        long now = System.currentTimeMillis();

        if (rebreakPacketTarget != null) {
            if (!isValidRebreakTarget(player, level, rebreakPacketTarget, range)) {
                clearRebreakPacketTarget(client);
            } else {
                client.gameMode.continueDestroyBlock(rebreakPacketTarget, rebreakPacketFace);
                player.swing(InteractionHand.MAIN_HAND);
                if (level.getBlockState(rebreakPacketTarget).isAir()) {
                    clearRebreakPacketTarget(client);
                    lastRebreakMs = now;
                }
                return;
            }
        }

        if (now - lastRebreakMs < delay) {
            return;
        }
        lastRebreakMs = now;

        List<BlockPos> targets = getRebreakMineableTargets(client, range, 1);
        if (targets.isEmpty()) {
            return;
        }

        rebreakPacketTarget = targets.get(0).immutable();
        rebreakPacketFace = getMiningFace(player, rebreakPacketTarget);
        client.gameMode.startDestroyBlock(rebreakPacketTarget, rebreakPacketFace);
        client.gameMode.continueDestroyBlock(rebreakPacketTarget, rebreakPacketFace);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean handleRebreakSelectionInput(Minecraft client, FishClickScreen.ModuleEntry module) {
        boolean changed = false;
        boolean selectionUsed = false;

        FishClickScreen.SettingEntry clearAction = findSetting("Rebreak", "Clear Selected");
        if (isActionRequested(clearAction)) {
            rebreakSelections.clear();
            rebreakPos1 = null;
            rebreakPos2 = null;
            rebreakPos1Display = "-";
            rebreakPos2Display = "-";
            clearRebreakPacketTarget(client);
            resetAction(clearAction);
            setText("Rebreak", "Status", "Selection cleared.");
            changed = true;
        }

        boolean down = isRebreakSelectButtonDown(client);
        if (down && !rebreakSelectHeld) {
            HitResult hitResult = getRebreakSelectionHitResult(client);
            if (hitResult instanceof BlockHitResult blockHitResult) {
                BlockPos pos = blockHitResult.getBlockPos().immutable();
                int selectionMode = getSelectIndex("Rebreak", "Selection Mode", 0);
                if (selectionMode == 0) {
                    if (rebreakSelections.contains(pos)) {
                        rebreakSelections.remove(pos);
                        setText("Rebreak", "Status", "Removed: " + formatPos(pos));
                    } else {
                        rebreakSelections.add(pos);
                        setText("Rebreak", "Status", "Added: " + formatPos(pos));
                    }
                    rebreakPos1Display = "-";
                    rebreakPos2Display = "-";
                    changed = true;
                    selectionUsed = true;
                } else {
                    if (rebreakPos1 == null) {
                        rebreakPos1 = pos;
                        rebreakPos2 = null;
                        rebreakPos1Display = formatPos(pos);
                        rebreakPos2Display = "-";
                        setText("Rebreak", "Status", "Pos1 set: " + formatPos(pos));
                        changed = true;
                        selectionUsed = true;
                    } else {
                        String pos1Text = formatPos(rebreakPos1);
                        String pos2Text = formatPos(pos);
                        rebreakPos2 = pos;
                        int added = addCuboidSelection(rebreakPos1, rebreakPos2, 8192);
                        rebreakPos1Display = pos1Text;
                        rebreakPos2Display = pos2Text;
                        setText("Rebreak", "Status", "Cuboid set: " + pos1Text + " -> " + pos2Text + " (+" + added + ")");
                        rebreakPos1 = null;
                        rebreakPos2 = null;
                        changed = true;
                        selectionUsed = true;
                    }
                }
            } else {
                setText("Rebreak", "Status", "No block targeted for selection.");
                changed = true;
            }
        }
        rebreakSelectHeld = down;

        if (changed) {
            setText("Rebreak", "Selected Blocks", Integer.toString(rebreakSelections.size()));
            setText("Rebreak", "Pos1", rebreakPos1Display);
            setText("Rebreak", "Pos2", rebreakPos2Display);
            CloudBridge.broadcastState();
        }
        return selectionUsed;
    }

    private HitResult getRebreakSelectionHitResult(Minecraft client) {
        if (client == null || client.player == null) {
            return null;
        }
        double range = clamp(getSlider("Rebreak", "Range", 8.0), 1.0, 30.0);
        HitResult picked = client.player.pick(range, 0.0F, false);
        if (picked instanceof BlockHitResult) {
            return picked;
        }
        return client.hitResult;
    }

    private boolean isRebreakSelectButtonDown(Minecraft client) {
        if (client == null || client.getWindow() == null) {
            return false;
        }
        int selectButton = getSelectIndex("Rebreak", "Select Button", 0);
        if (selectButton == 0) {
            return client.options.keyPickItem != null && client.options.keyPickItem.isDown();
        }

        int keyCode = switch (selectButton) {
            case 1 -> GLFW.GLFW_KEY_R;
            case 2 -> GLFW.GLFW_KEY_V;
            case 3 -> GLFW.GLFW_KEY_RIGHT_ALT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return false;
        }
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(), keyCode);
    }

    private int addCuboidSelection(BlockPos a, BlockPos b, int maxBlocks) {
        if (a == null || b == null) {
            return 0;
        }
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int added = 0;
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (count++ >= maxBlocks) {
                        return added;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (rebreakSelections.add(pos)) {
                        added++;
                    }
                }
            }
        }
        return added;
    }

    private List<BlockPos> getRebreakMineableTargets(Minecraft client, double range, int limit) {
        LocalPlayer player = client.player;
        Level level = client.level;
        List<TargetCandidate> candidates = new ArrayList<>();
        for (BlockPos pos : rebreakSelections) {
            if (pos == null) {
                continue;
            }
            if (!isValidRebreakTarget(player, level, pos, range)) {
                continue;
            }
            double distance = pos.distToCenterSqr(player.position());
            candidates.add(new TargetCandidate(pos, distance));
        }
        candidates.sort(Comparator.comparingDouble(value -> value.distance));
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < candidates.size() && out.size() < limit; i++) {
            out.add(candidates.get(i).pos);
        }
        return out;
    }

    private boolean isValidRebreakTarget(LocalPlayer player, Level level, BlockPos pos, double range) {
        if (player == null || level == null || pos == null) {
            return false;
        }
        if (!rebreakSelections.contains(pos)) {
            return false;
        }
        if (pos.distToCenterSqr(player.position()) > range * range) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private void tickNuker(Minecraft client) {
        LocalPlayer player = client.player;
        Level level = client.level;
        if (!isEnabled("Nuker")) {
            clearNukerPacketTarget(client);
            return;
        }

        double range = clamp(getSlider("Nuker", "Range", 6.0), 1.0, 8.0);
        long delay = (long) clamp(getSlider("Nuker", "Delay ms", 40.0), 0.0, 2000.0);
        int blocksPerTick = (int) Math.round(clamp(getSlider("Nuker", "Blocks Per Tick", 12.0), 1.0, 96.0));
        int mode = getSelectIndex("Nuker", "Mine Mode", 0);
        int pattern = getSelectIndex("Nuker", "Pattern", 0);
        boolean layerOnly = pattern == 1;

        if (mode == 0) {
            tickPacketNuker(client, player, level, range, delay, layerOnly);
            return;
        }

        clearNukerPacketTarget(client);
        long now = System.currentTimeMillis();
        if (now - lastNukerMs < Math.max(0L, delay)) {
            return;
        }
        lastNukerMs = now;

        List<BlockPos> targets = findNearbyTargets(player, level, range, blocksPerTick, layerOnly);
        for (BlockPos pos : targets) {
            aggressiveMine(client, pos);
        }
    }

    private void tickPacketNuker(Minecraft client, LocalPlayer player, Level level, double range, long delay, boolean layerOnly) {
        long now = System.currentTimeMillis();

        if (nukerPacketTarget != null) {
            if (!isValidTarget(player, level, nukerPacketTarget, range, layerOnly)) {
                clearNukerPacketTarget(client);
            } else {
                client.gameMode.continueDestroyBlock(nukerPacketTarget, nukerPacketFace);
                player.swing(InteractionHand.MAIN_HAND);
                if (level.getBlockState(nukerPacketTarget).isAir()) {
                    clearNukerPacketTarget(client);
                    lastNukerMs = now;
                }
                return;
            }
        }

        if (now - lastNukerMs < Math.max(0L, delay)) {
            return;
        }
        lastNukerMs = now;

        List<BlockPos> next = findNearbyTargets(player, level, range, 1, layerOnly);
        if (next.isEmpty()) {
            return;
        }

        nukerPacketTarget = next.get(0).immutable();
        nukerPacketFace = getMiningFace(player, nukerPacketTarget);
        client.gameMode.startDestroyBlock(nukerPacketTarget, nukerPacketFace);
        client.gameMode.continueDestroyBlock(nukerPacketTarget, nukerPacketFace);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void tickPrisonLayerMiner(Minecraft client) {
        LocalPlayer player = client.player;
        Level level = client.level;
        FishClickScreen.ModuleEntry prisonModule = findModule("Prison Layer Miner");
        if (prisonModule == null) {
            clearPrisonPacketTarget(client);
            prisonCursorReady = false;
            prisonCursorFinished = false;
            return;
        }

        handlePrisonHelpers(client, prisonModule);

        if (!prisonModule.enabled) {
            clearPrisonPacketTarget(client);
            prisonCursorReady = false;
            prisonCursorFinished = false;
            return;
        }

        BlockPos pos1 = parseBlockPos(getText("Prison Layer Miner", "Pos1", ""));
        BlockPos pos2 = parseBlockPos(getText("Prison Layer Miner", "Pos2", ""));
        if (pos1 == null || pos2 == null) {
            clearPrisonPacketTarget(client);
            return;
        }

        String targetBlock = getText("Prison Layer Miner", "Target Block", "").trim().toLowerCase(Locale.ROOT);
        double range = clamp(getSlider("Prison Layer Miner", "Range", 8.0), 1.0, 8.0);
        long delay = (long) clamp(getSlider("Prison Layer Miner", "Delay ms", 25.0), 0.0, 2000.0);
        int blocksPerTick = (int) Math.round(clamp(getSlider("Prison Layer Miner", "Blocks Per Tick", 20.0), 1.0, 128.0));
        int mode = getSelectIndex("Prison Layer Miner", "Mine Mode", 0);
        int yOrder = getSelectIndex("Prison Layer Miner", "Y Order", 0);
        boolean topDown = yOrder == 0;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        String signature = minX + ":" + minY + ":" + minZ + ":" + maxX + ":" + maxY + ":" + maxZ + ":" + targetBlock + ":" + topDown;
        ensurePrisonCursor(signature, minX, minY, minZ, maxY, topDown);

        if (prisonCursorFinished) {
            clearPrisonPacketTarget(client);
            return;
        }

        if (mode == 0) {
            tickPacketPrison(client, player, level, range, delay, targetBlock, minX, minY, minZ, maxX, maxY, maxZ, topDown);
            return;
        }

        clearPrisonPacketTarget(client);
        long now = System.currentTimeMillis();
        if (now - lastPrisonMs < Math.max(0L, delay)) {
            return;
        }
        lastPrisonMs = now;

        for (int i = 0; i < blocksPerTick; i++) {
            BlockPos next = nextPrisonTarget(player, level, range, targetBlock, minX, minY, minZ, maxX, maxY, maxZ, topDown, 50000);
            if (next == null) {
                break;
            }
            aggressiveMine(client, next);
        }
    }

    private void tickPayAll(Minecraft client) {
        FishClickScreen.ModuleEntry module = findModule("PayAll");
        if (module == null || client == null || client.player == null || client.level == null) {
            return;
        }

        boolean includeSelf = getToggle("PayAll", "Include Self", false);
        int playerSource = getSelectIndex("PayAll", "Player Source", 0);
        FishClickScreen.SettingEntry scanAction = findSetting("PayAll", "Scan Players");
        if (isActionRequested(scanAction)) {
            payAllScannedTargets.clear();
            payAllScannedTargets.addAll(collectPayAllTargetsFromTab(client, includeSelf));
            resetAction(scanAction);
            setText("PayAll", "Targets Cached", Integer.toString(payAllScannedTargets.size()));
            setText("PayAll", "Status", payAllScannedTargets.isEmpty() ? "Scan done: no players." : "Scanned tab: " + payAllScannedTargets.size() + " players.");
            CloudBridge.broadcastState();
        }

        if (!module.enabled) {
            if (payAllRunning) {
                payAllRunning = false;
                payAllQueue.clear();
                payAllIndex = 0;
            }
            return;
        }

        if (!payAllRunning) {
            String amountText = normalizeAmount(getText("PayAll", "Amount", "1000"));
            if (amountText.isBlank()) {
                setText("PayAll", "Status", "Invalid amount.");
                setModuleEnabled(module, false);
                CloudBridge.broadcastState();
                return;
            }

            List<String> targets;
            if (playerSource == 0) {
                if (!payAllScannedTargets.isEmpty()) {
                    targets = new ArrayList<>(payAllScannedTargets);
                } else {
                    targets = collectPayAllTargetsFromTab(client, includeSelf);
                    payAllScannedTargets.clear();
                    payAllScannedTargets.addAll(targets);
                }
                setText("PayAll", "Targets Cached", Integer.toString(payAllScannedTargets.size()));
            } else {
                targets = collectPayAllTargetsFromWorld(client, includeSelf);
            }

            payAllQueue.clear();
            payAllQueue.addAll(targets);
            payAllIndex = 0;
            payAllRunning = !payAllQueue.isEmpty();
            payAllAmount = amountText;

            if (!payAllRunning) {
                setText("PayAll", "Status", playerSource == 0 ? "No tab players found." : "No world players found.");
                setModuleEnabled(module, false);
                CloudBridge.broadcastState();
                return;
            }

            setText("PayAll", "Status", "Running 0/" + payAllQueue.size());
            CloudBridge.broadcastState();
        }

        long delayMs = (long) clamp(getSlider("PayAll", "Delay ms", 250.0), 0.0, 5000.0);
        long now = System.currentTimeMillis();
        if (now - lastPayAllMs < delayMs) {
            return;
        }
        lastPayAllMs = now;

        if (payAllIndex >= payAllQueue.size()) {
            setText("PayAll", "Status", "Done: " + payAllQueue.size() + " paid.");
            setModuleEnabled(module, false);
            payAllRunning = false;
            payAllQueue.clear();
            payAllIndex = 0;
            CloudBridge.broadcastState();
            return;
        }

        String target = payAllQueue.get(payAllIndex);
        String command = "pay " + target + " " + payAllAmount;
        sendClientCommand(client, command);
        payAllIndex++;
        setText("PayAll", "Status", "Running " + payAllIndex + "/" + payAllQueue.size());
        if (payAllIndex >= payAllQueue.size()) {
            setText("PayAll", "Status", "Done: " + payAllQueue.size() + " paid.");
            setModuleEnabled(module, false);
            payAllRunning = false;
            payAllQueue.clear();
            payAllIndex = 0;
        }
        CloudBridge.broadcastState();
    }

    private List<String> collectPayAllTargetsFromTab(Minecraft client, boolean includeSelf) {
        Set<String> uniqueNames = new LinkedHashSet<>();
        String selfName = normalizePlayerName(client.player.getName().getString());
        if (client.getConnection() != null) {
            for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                if (info == null || info.getProfile() == null) {
                    continue;
                }
                String name = normalizePlayerName(info.getProfile().name());
                if (name.isBlank()) {
                    continue;
                }
                if (!includeSelf && !selfName.isBlank() && name.equalsIgnoreCase(selfName)) {
                    continue;
                }
                uniqueNames.add(name);
            }
        }
        return new ArrayList<>(uniqueNames);
    }

    private List<String> collectPayAllTargetsFromWorld(Minecraft client, boolean includeSelf) {
        Set<String> uniqueNames = new LinkedHashSet<>();
        String selfName = normalizePlayerName(client.player.getName().getString());
        client.level.players().forEach(player -> {
            if (player == null || player.isSpectator() || player.isRemoved()) {
                return;
            }
            String name = normalizePlayerName(player.getName().getString());
            if (name.isBlank()) {
                return;
            }
            if (!includeSelf && !selfName.isBlank() && name.equalsIgnoreCase(selfName)) {
                return;
            }
            uniqueNames.add(name);
        });
        return new ArrayList<>(uniqueNames);
    }

    private void tickPacketPrison(
        Minecraft client,
        LocalPlayer player,
        Level level,
        double range,
        long delay,
        String targetBlock,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean topDown
    ) {
        long now = System.currentTimeMillis();

        if (prisonPacketTarget != null) {
            if (!isValidAreaTarget(player, level, prisonPacketTarget, range, targetBlock, minX, minY, minZ, maxX, maxY, maxZ)) {
                clearPrisonPacketTarget(client);
            } else {
                client.gameMode.continueDestroyBlock(prisonPacketTarget, prisonPacketFace);
                player.swing(InteractionHand.MAIN_HAND);
                if (level.getBlockState(prisonPacketTarget).isAir()) {
                    clearPrisonPacketTarget(client);
                    lastPrisonMs = now;
                }
                return;
            }
        }

        if (now - lastPrisonMs < Math.max(0L, delay)) {
            return;
        }
        lastPrisonMs = now;

        BlockPos next = nextPrisonTarget(player, level, range, targetBlock, minX, minY, minZ, maxX, maxY, maxZ, topDown, 50000);
        if (next == null) {
            return;
        }

        prisonPacketTarget = next.immutable();
        prisonPacketFace = getMiningFace(player, prisonPacketTarget);
        client.gameMode.startDestroyBlock(prisonPacketTarget, prisonPacketFace);
        client.gameMode.continueDestroyBlock(prisonPacketTarget, prisonPacketFace);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void aggressiveMine(Minecraft client, BlockPos pos) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        BlockState state = client.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(client.level, pos) < 0.0F) {
            return;
        }

        Direction face = getMiningFace(client.player, pos);
        boolean destroyed = client.gameMode.destroyBlock(pos);
        if (!destroyed || !client.level.getBlockState(pos).isAir()) {
            client.gameMode.startDestroyBlock(pos, face);
            client.gameMode.continueDestroyBlock(pos, face);
        }
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private void clearNukerPacketTarget(Minecraft client) {
        nukerPacketTarget = null;
        if (client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private void clearRangeMineTarget(Minecraft client) {
        boolean hadTarget = rangeMineTarget != null;
        rangeMineTarget = null;
        if (hadTarget && client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private void clearRebreakPacketTarget(Minecraft client) {
        boolean hadTarget = rebreakPacketTarget != null;
        rebreakPacketTarget = null;
        if (hadTarget && client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private void clearPrisonPacketTarget(Minecraft client) {
        prisonPacketTarget = null;
        if (client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private boolean isValidTarget(LocalPlayer player, Level level, BlockPos pos, double range, boolean layerOnly) {
        if (player == null || level == null || pos == null) {
            return false;
        }
        if (layerOnly && pos.getY() != player.blockPosition().getY()) {
            return false;
        }
        if (pos.distToCenterSqr(player.position()) > range * range) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private boolean isValidAreaTarget(LocalPlayer player, Level level, BlockPos pos, double range, String targetBlock, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY || pos.getZ() < minZ || pos.getZ() > maxZ) {
            return false;
        }
        if (!isValidTarget(player, level, pos, range, false)) {
            return false;
        }
        return matchesTargetBlock(level.getBlockState(pos), targetBlock);
    }

    private List<BlockPos> findNearbyTargets(LocalPlayer player, Level level, double range, int limit, boolean layerOnly) {
        int r = Math.max(1, (int) Math.ceil(range));
        BlockPos center = player.blockPosition();
        List<TargetCandidate> candidates = new ArrayList<TargetCandidate>();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (layerOnly && pos.getY() != center.getY()) {
                        continue;
                    }

                    double dist = pos.distToCenterSqr(player.position());
                    if (dist > range * range) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }

                    candidates.add(new TargetCandidate(pos, dist));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(c -> c.distance));
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (int i = 0; i < candidates.size() && result.size() < limit; i++) {
            result.add(candidates.get(i).pos);
        }
        return result;
    }

    private void ensurePrisonCursor(String signature, int minX, int minY, int minZ, int maxY, boolean topDown) {
        if (prisonCursorReady && signature.equals(prisonCursorSignature)) {
            return;
        }
        prisonCursorSignature = signature;
        prisonCursorX = minX;
        prisonCursorZ = minZ;
        prisonCursorY = topDown ? maxY : minY;
        prisonCursorReady = true;
        prisonCursorFinished = false;
    }

    private BlockPos nextPrisonTarget(
        LocalPlayer player,
        Level level,
        double range,
        String targetBlock,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean topDown,
        int scanLimit
    ) {
        if (!prisonCursorReady) {
            ensurePrisonCursor("", minX, minY, minZ, maxY, topDown);
        }
        if (prisonCursorFinished) {
            return null;
        }

        int scanned = 0;
        while (scanned++ < scanLimit) {
            if (prisonCursorFinished) {
                return null;
            }
            BlockPos current = new BlockPos(prisonCursorX, prisonCursorY, prisonCursorZ);
            advancePrisonCursor(level, targetBlock, minX, minY, minZ, maxX, maxY, maxZ, topDown);

            if (current.distToCenterSqr(player.position()) > range * range) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            if (state.isAir() || state.getDestroySpeed(level, current) < 0.0F) {
                continue;
            }
            if (!matchesTargetBlock(state, targetBlock)) {
                continue;
            }

            return current;
        }

        return null;
    }

    private void advancePrisonCursor(
        Level level,
        String targetBlock,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean topDown
    ) {
        prisonCursorX++;
        if (prisonCursorX <= maxX) {
            return;
        }
        prisonCursorX = minX;

        prisonCursorZ++;
        if (prisonCursorZ <= maxZ) {
            return;
        }
        prisonCursorZ = minZ;

        if (layerHasMineableBlocks(level, targetBlock, minX, maxX, prisonCursorY, minZ, maxZ)) {
            return;
        }

        prisonCursorY += topDown ? -1 : 1;
        if (topDown) {
            if (prisonCursorY < minY) {
                prisonCursorY = minY;
                prisonCursorFinished = true;
            }
        } else {
            if (prisonCursorY > maxY) {
                prisonCursorY = maxY;
                prisonCursorFinished = true;
            }
        }
    }

    private boolean layerHasMineableBlocks(Level level, String targetBlock, int minX, int maxX, int y, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }
                if (matchesTargetBlock(state, targetBlock)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesTargetBlock(BlockState state, String targetBlock) {
        if (targetBlock == null || targetBlock.isBlank()) {
            return true;
        }
        String normalizedTarget = normalizeTargetId(targetBlock);
        if (normalizedTarget.isBlank()) {
            return true;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
        if (normalizedTarget.startsWith("*")) {
            String containsNeedle = normalizedTarget.substring(1);
            return !containsNeedle.isBlank() && id.contains(containsNeedle);
        }
        if (normalizedTarget.contains(":")) {
            return id.equals(normalizedTarget);
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.equals(normalizedTarget);
    }

    private void handlePrisonHelpers(Minecraft client, FishClickScreen.ModuleEntry prisonModule) {
        if (client == null || client.player == null || client.level == null) {
            return;
        }

        boolean shouldBroadcast = false;

        String lookedBlock = getLookedBlockId(client);
        FishClickScreen.SettingEntry lookingAt = findSetting(prisonModule.name, "Looking At");
        if (lookingAt != null && lookingAt.type == FishClickScreen.SettingEntry.Type.TEXT) {
            if (!lookedBlock.equals(lastLookedBlockId) || !lookedBlock.equals(lookingAt.textValue)) {
                lookingAt.textValue = lookedBlock;
                lastLookedBlockId = lookedBlock;
            }
        }

        FishClickScreen.SettingEntry pos1Action = findSetting(prisonModule.name, "Pos1 Action");
        FishClickScreen.SettingEntry pos2Action = findSetting(prisonModule.name, "Pos2 Action");
        FishClickScreen.SettingEntry targetAction = findSetting(prisonModule.name, "Target Action");

        if (isActionRequested(pos1Action)) {
            setText(prisonModule.name, "Pos1", formatPos(client.player.blockPosition()));
            resetAction(pos1Action);
            shouldBroadcast = true;
        }

        if (isActionRequested(pos2Action)) {
            setText(prisonModule.name, "Pos2", formatPos(client.player.blockPosition()));
            resetAction(pos2Action);
            shouldBroadcast = true;
        }

        if (isActionRequested(targetAction) && !lookedBlock.isBlank()) {
            setText(prisonModule.name, "Target Block", lookedBlock);
            resetAction(targetAction);
            shouldBroadcast = true;
        }

        if (shouldBroadcast) {
            CloudBridge.broadcastState();
        }
    }

    private boolean isActionRequested(FishClickScreen.SettingEntry setting) {
        return setting != null
            && setting.type == FishClickScreen.SettingEntry.Type.SELECT
            && setting.selectOptions != null
            && setting.selectOptions.length > 1
            && setting.selectIndex == 1;
    }

    private void resetAction(FishClickScreen.SettingEntry setting) {
        if (setting != null && setting.type == FishClickScreen.SettingEntry.Type.SELECT) {
            setting.selectIndex = 0;
        }
    }

    private void setText(String moduleName, String settingName, String value) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.TEXT) {
            return;
        }
        setting.textValue = value == null ? "" : value;
    }

    private String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private String getLookedBlockId(Minecraft client) {
        HitResult hitResult = client.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return "";
        }
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState state = client.level.getBlockState(blockPos);
        if (state.isAir()) {
            return "";
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
    }

    private String normalizeTargetId(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (text.startsWith("*")) {
            String contains = text.substring(1).trim();
            return contains.isEmpty() ? "" : "*" + contains;
        }
        if (text.contains(":")) {
            return text;
        }
        return text;
    }

    private BlockPos parseBlockPos(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split("[,;\\s]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Direction getMiningFace(LocalPlayer player, BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        double dx = player.getX() - center.x;
        double dy = player.getEyeY() - center.y;
        double dz = player.getZ() - center.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= ax && ay >= az) {
            return dy >= 0.0 ? Direction.UP : Direction.DOWN;
        }
        return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean isEnabled(String moduleName) {
        FishClickScreen.ModuleEntry module = findModule(moduleName);
        return module != null && module.enabled;
    }

    private double getSlider(String moduleName, String settingName, double fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.SLIDER) {
            return fallback;
        }
        return setting.sliderValue;
    }

    private boolean getToggle(String moduleName, String settingName, boolean fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.TOGGLE) {
            return fallback;
        }
        return setting.boolValue;
    }

    private int getSelectIndex(String moduleName, String settingName, int fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.SELECT) {
            return fallback;
        }
        return setting.selectIndex;
    }

    private String getText(String moduleName, String settingName, String fallback) {
        FishClickScreen.SettingEntry setting = findSetting(moduleName, settingName);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.TEXT) {
            return fallback;
        }
        return setting.textValue == null ? "" : setting.textValue;
    }

    private FishClickScreen.ModuleEntry findModule(String moduleName) {
        for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
            if (module.name.equalsIgnoreCase(moduleName)) {
                return module;
            }
        }
        return null;
    }

    private FishClickScreen.SettingEntry findSetting(String moduleName, String settingName) {
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

    private void setModuleEnabled(FishClickScreen.ModuleEntry module, boolean enabled) {
        if (module == null) {
            return;
        }
        module.enabled = enabled;
        module.toggleAnim = enabled ? 1.0f : 0.0f;
        module.toggleAnimMs = System.currentTimeMillis();
    }

    private String normalizeAmount(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().replace(",", "").replace("_", "");
        if (text.isBlank()) {
            return "";
        }
        try {
            double value = Double.parseDouble(text);
            if (value <= 0.0D) {
                return "";
            }
            if (Math.floor(value) == value) {
                return Long.toString((long) value);
            }
            return String.format(Locale.ROOT, "%.2f", value);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private void sendClientCommand(Minecraft client, String rawCommand) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        String cmd = rawCommand == null ? "" : rawCommand.trim();
        if (cmd.isBlank()) {
            return;
        }
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.isBlank()) {
            return;
        }
        try {
            client.player.connection.sendCommand(cmd);
        } catch (Throwable ignored) {
            try {
                client.player.connection.sendChat("/" + cmd);
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    private String normalizePlayerName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        if (name.isBlank()) {
            return "";
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            return "";
        }
        return name;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class TargetCandidate {
        final BlockPos pos;
        final double distance;

        TargetCandidate(BlockPos pos, double distance) {
            this.pos = pos;
            this.distance = distance;
        }
    }

    public int getRebreakSelectionCount() {
        return rebreakSelections.size();
    }
}

