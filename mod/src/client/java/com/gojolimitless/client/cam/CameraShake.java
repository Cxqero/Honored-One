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

    private static long punchStart = -1L;
    private static float punchAmount;

    /**
     * Lens punch: the view snaps in (narrower field of view) and springs back out over ~0.35 s, the hit-feel of a
     * shot leaving your hands. {@code amount} is the fraction of the field of view taken away at the peak (0.1 = 10 %).
     */
    public static void punch(float amount) {
        punchStart = net.minecraft.util.Util.getMeasuringTimeMs();
        punchAmount = Math.max(punchAmount * 0.5f, amount) * (float) Math.min(1.0, ConfigManager.get().client.cameraShake);
    }

    /** Multiplier for the field of view right now (1 = none). */
    public static float fovScale() {
        if (punchStart < 0) return 1f;
        float t = (net.minecraft.util.Util.getMeasuringTimeMs() - punchStart) / 1000f;
        if (t > 0.45f) { punchStart = -1L; return 1f; }
        // in over 40 ms, then a damped spring back that overshoots slightly wide
        float k = t < 0.04f ? t / 0.04f : (float) (Math.exp(-(t - 0.04f) * 11.0) * Math.cos((t - 0.04f) * 16.0));
        return 1f - punchAmount * k;
    }

    private static float noise(float t, float seed) {
        return (MathHelper.sin(t * 1.7f + seed) * 0.5f + MathHelper.sin(t * 3.1f + seed * 2.3f) * 0.3f
                + MathHelper.sin(t * 5.3f + seed * 4.1f) * 0.2f);
    }
}
