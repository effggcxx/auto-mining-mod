package me.ehsan.smoothminer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;

public final class SmoothMinerClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("smoothminer", "main"));

    private static KeyMapping toggleKey;
    private static boolean enabled = false;
    private static BlockPos target = null;
    private static int scanCooldown = 0;
    private static Settings settings;

    @Override
    public void onInitializeClient() {
        settings = Settings.load();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smoothminer.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_H,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(SmoothMinerClient::tick);
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
            return;
        }

        if (scanCooldown-- <= 0 || target == null || !isTargetBlock(client, target)) {
            target = findNearestTarget(client);
            scanCooldown = 5;
        }

        if (target == null) {
            stopAttack(client);
            return;
        }

        aimSmoothly(client, target);

        HitResult hit = client.hitResult;
        boolean lookingAtTarget = hit instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target)
                && isTargetBlock(client, target);

        client.options.keyAttack.setDown(lookingAtTarget);
    }

    private static BlockPos findNearestTarget(Minecraft client) {
        BlockPos origin = client.player.blockPosition();
        int radius = Math.max(1, Math.min(settings.searchRadius, 8));
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
        if (client.level == null || !client.level.hasChunkAt(pos)) return false;
        BlockState state = client.level.getBlockState(pos);
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.toString().equals(settings.targetBlock);
    }

    private static void aimSmoothly(Minecraft client, BlockPos pos) {
        var player = client.player;
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - (player.getY() + player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - player.getZ();

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        float maxStep = Math.max(0.5f, Math.min(settings.turnDegreesPerTick, 15.0f));
        float yawDelta = wrapDegrees(desiredYaw - player.getYRot());
        float pitchDelta = desiredPitch - player.getXRot();

        player.setYRot(player.getYRot() + clamp(yawDelta, -maxStep, maxStep));
        player.setXRot(player.getXRot() + clamp(pitchDelta, -maxStep, maxStep));
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

    private static void stop(Minecraft client) {
        stopAttack(client);
        target = null;
        scanCooldown = 0;
    }

    private static void stopAttack(Minecraft client) {
        if (client.options != null) client.options.keyAttack.setDown(false);
    }
}
