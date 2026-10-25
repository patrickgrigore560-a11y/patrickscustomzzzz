package ro.patrickszcustoms.assist;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ro.patrickszcustoms.config.AimEntities;
import ro.patrickszcustoms.config.ConfigManager;
import ro.patrickszcustoms.config.ModConfig;
import ro.patrickszcustoms.i18n.Tr;

import java.util.HashSet;
import java.util.Set;

/**
 * Soft aim assist for SINGLE-PLAYER worlds only.
 *
 * Every rendered frame it nudges the camera toward the nearest visible entity inside a view cone.
 * Targets are either ALL entities or only the entity types the player ticked in the picker.
 * It uses the same entry point as the mouse (Entity#changeLookDirection), so your own aiming always wins.
 * It is hard-disabled on servers and on LAN-opened worlds (MinecraftClient#isInSingleplayer() is false there).
 */
public final class AimAssist {
    private static long lastMs;
    private static final Set<EntityType<?>> CACHE = new HashSet<>();
    private static int cacheKey = Integer.MIN_VALUE;

    private AimAssist() {}

    /** Key / menu toggle. Refuses outside single-player. */
    public static void toggle(MinecraftClient mc) {
        if (mc.player == null) return;
        if (!mc.isInSingleplayer()) {
            mc.player.sendMessage(Tr.t("patrickszcustoms.aim.single_only"), true);
            return;
        }
        ModConfig c = ConfigManager.get();
        c.aimAssistEnabled = !c.aimAssistEnabled;
        ConfigManager.save();
        mc.player.sendMessage(Tr.t(c.aimAssistEnabled
                ? "patrickszcustoms.aim.toggled_on" : "patrickszcustoms.aim.toggled_off"), true);
    }

    /** Selected entity types, rebuilt only when the selection list changes. */
    private static Set<EntityType<?>> selected(ModConfig c) {
        AimEntities.ensureDefaults(c);
        int key = c.aimEntities.hashCode();
        if (key != cacheKey) {
            CACHE.clear();
            Set<String> ids = new HashSet<>(c.aimEntities);
            for (EntityType<?> t : Registries.ENTITY_TYPE) {
                if (ids.contains(AimEntities.id(t))) CACHE.add(t);
            }
            cacheKey = key;
        }
        return CACHE;
    }

    /** Call once per frame. */
    public static void update(MinecraftClient mc) {
        long now = Util.getMeasuringTimeMs();
        float dt = lastMs == 0L ? 0f : Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;

        ModConfig c = ConfigManager.get();
        if (!c.aimAssistEnabled || dt <= 0f) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;
        if (!mc.isInSingleplayer() || !p.isAlive() || p.isSpectator()) return;
        if (c.aimAssistOnlyAttacking && !mc.options.attackKey.isPressed()) return;

        Set<EntityType<?>> allowed = c.aimAssistAllEntities ? null : selected(c);
        if (allowed != null && allowed.isEmpty()) return;

        Vec3d eye = p.getEyePos();
        float yaw = p.getYaw(), pitch = p.getPitch();

        double bestAngle = c.aimAssistFov;
        float bestYaw = 0f, bestPitch = 0f;
        boolean found = false;

        for (Entity e : mc.world.getEntities()) {
            if (e == p || e == p.getVehicle() || e.isRemoved() || e.isSpectator()) continue;
            if (e instanceof LivingEntity le && !le.isAlive()) continue;
            if (allowed != null && !allowed.contains(e.getType())) continue;
            if (p.distanceTo(e) > c.aimAssistRange) continue;

            Vec3d t = e.getBoundingBox().getCenter();
            double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90f;
            float targetPitch = (float) -(Math.atan2(dy, horiz) * 57.29577951308232);
            float yd = MathHelper.wrapDegrees(targetYaw - yaw);
            float pd = targetPitch - pitch;
            double angle = Math.sqrt((double) yd * yd + (double) pd * pd);
            if (angle >= bestAngle || !p.canSee(e)) continue; // canSee = ray cast, so check last

            bestAngle = angle; bestYaw = yd; bestPitch = pd; found = true;
        }
        if (!found || bestAngle < 0.25) return; // dead zone: no jitter when already on target

        float s = c.aimAssistStrength;
        float factor = 1f - (float) Math.exp(-(3f + 20f * s) * dt);
        float maxStep = (90f + 630f * s) * dt; // degrees this frame
        float dYaw = MathHelper.clamp(bestYaw * factor, -maxStep, maxStep);
        float dPitch = c.aimAssistVertical ? MathHelper.clamp(bestPitch * factor, -maxStep, maxStep) : 0f;
        // changeLookDirection takes "mouse units": 1 unit = 0.15 degrees
        p.changeLookDirection(dYaw / 0.15, dPitch / 0.15);
    }
}
