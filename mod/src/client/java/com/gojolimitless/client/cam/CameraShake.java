package com.gojolimitless.client.cam;

import com.gojolimitless.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Trauma-style camera shake (intensity², smooth noise), applied to camera yaw/pitch by a mixin. */
public final class CameraShake {
    private CameraShake() {}

    private static float trauma;
    private static float time;

    /** @param amount 0..1 trauma added; distance falloff applied from {@code at} */
    public static void add(float amount, Vec3d at, double radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double d = mc.player.getPos().distanceTo(at);
        float fall = (float) MathHelper.clamp(1.0 - d / radius, 0, 1);
        trauma = Math.min(1f, trauma + amount * fall * fall);
    }

    public static void tick() {
        trauma = Math.max(0f, trauma - 0.045f);
        time += 1f;
    }

    /** Returns {yawOffset, pitchOffset} in degrees. */
    public static float[] offsets(float tickDelta) {
        float s = trauma * trauma * (float) ConfigManager.get().client.cameraShake;
        if (s <= 0.0001f) return null;
        float t = (time + tickDelta) * 0.9f;
        float yaw = 6f * s * noise(t, 1.3f);
        float pitch = 4.5f * s * noise(t, 7.9f);
        return new float[]{yaw, pitch};
    }

    private static float noise(float t, float seed) {
        return (MathHelper.sin(t * 1.7f + seed) * 0.5f + MathHelper.sin(t * 3.1f + seed * 2.3f) * 0.3f
                + MathHelper.sin(t * 5.3f + seed * 4.1f) * 0.2f);
    }
}
