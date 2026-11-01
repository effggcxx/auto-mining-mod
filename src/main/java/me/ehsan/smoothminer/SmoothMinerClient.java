package me.ehsan.smoothminer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SmoothMinerClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("smoothminer", "main"));

    private static KeyMapping toggleKey;
    private static boolean enabled = false;
    private static BlockPos target = null;
    private static int scanCooldown = 0;

    /** Resolved lazily from Settings so the block registry is guaranteed to be ready. */
    private static Block targetBlock = null;
    private static boolean targetBlockDirty = true;

    /** Current camera turn speed in degrees per tick; eased towards the wanted speed every tick. */
    private static float yawVelocity = 0.0f;
    private static float pitchVelocity = 0.0f;

    @Override
    public void onInitializeClient() {
        Settings.get();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smoothminer.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_H,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(SmoothMinerClient::tick);
    }

    /** Called by the config screen after the settings were changed and saved. */
    public static void applySettings() {
        targetBlockDirty = true;
        target = null;
        scanCooldown = 0;
    }

    private static void tick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            if (!enabled) stop(client);
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.literal("SmoothMiner " + (enabled ? "enabled" : "disabled")));
            }
        }

        if (!enabled || client.player == null || client.level == null
                || client.gameMode == null || client.gui.screen() != null) {
            stopAttack(client);
            resetMotion();
            return;
        }

        if (targetBlockDirty) {
            targetBlock = Settings.resolveBlock(Settings.get().targetBlock);
            targetBlockDirty = false;
        }

        if (scanCooldown-- <= 0 || target == null || !isTargetBlock(client, target)) {
            target = findNearestTarget(client);
            scanCooldown = 5;
        }

        if (target == null) {
            stopAttack(client);
            resetMotion();
            return;
        }

        HitResult hit = client.hitResult;
        boolean lookingAtTarget = hit instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target)
                && isTargetBlock(client, target);

        // Once the crosshair is on the block there is nothing left to correct. Not nudging towards the
        // exact block centre is what removes the constant micro-jitter while mining.
        aimSmoothly(client, target, lookingAtTarget);

        client.options.keyAttack.setDown(lookingAtTarget);
    }

    private static BlockPos findNearestTarget(Minecraft client) {
        if (targetBlock == null) return null;

        BlockPos origin = client.player.blockPosition();
        int radius = Settings.get().searchRadius;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!isTargetBlock(client, pos)) continue;

                    double distance = client.player.distanceToSqr(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isTargetBlock(Minecraft client, BlockPos pos) {
        if (targetBlock == null || client.level == null || !client.level.hasChunkAt(pos)) return false;
        return client.level.getBlockState(pos).getBlock() == targetBlock;
    }

    private static void aimSmoothly(Minecraft client, BlockPos pos, boolean onTarget) {
        var player = client.player;
        Settings settings = Settings.get();

        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - (player.getY() + player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - player.getZ();

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        float yawDelta = wrapDegrees(desiredYaw - player.getYRot());
        float pitchDelta = desiredPitch - player.getXRot();

        // Speed limit per tick, from the "max turn speed" option (degrees per second).
        float maxStep = settings.maxTurnSpeed / 20.0f;

        // smoothness 1..10 -> 0..1. Higher = covers less of the remaining angle each tick and
        // changes speed more slowly, which gives a long, gentle ease-in and ease-out.
        float t = (settings.smoothness - Settings.SMOOTH_MIN) / (float) (Settings.SMOOTH_MAX - Settings.SMOOTH_MIN);
        float gain = lerp(0.30f, 0.06f, t);
        float accel = lerp(0.55f, 0.10f, t);

        float wantedYaw = onTarget ? 0.0f : clamp(yawDelta * gain, -maxStep, maxStep);
        float wantedPitch = onTarget ? 0.0f : clamp(pitchDelta * gain, -maxStep, maxStep);

        yawVelocity += (wantedYaw - yawVelocity) * accel;
        pitchVelocity += (wantedPitch - pitchVelocity) * accel;

        // Kill the tiny tail of the easing curve so the camera comes fully to rest.
        if (Math.abs(yawVelocity) < 0.01f && wantedYaw == 0.0f) yawVelocity = 0.0f;
        if (Math.abs(pitchVelocity) < 0.01f && wantedPitch == 0.0f) pitchVelocity = 0.0f;

        player.setYRot(player.getYRot() + yawVelocity);
        player.setXRot(clamp(player.getXRot() + pitchVelocity, -90.0f, 90.0f));
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static void resetMotion() {
        yawVelocity = 0.0f;
        pitchVelocity = 0.0f;
    }

    private static void stop(Minecraft client) {
        stopAttack(client);
        resetMotion();
        target = null;
        scanCooldown = 0;
    }

    private static void stopAttack(Minecraft client) {
        if (client.options != null) client.options.keyAttack.setDown(false);
    }
}
