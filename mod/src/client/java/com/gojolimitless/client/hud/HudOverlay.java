package com.gojolimitless.client.hud;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.config.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/** Screen flashes, the charge ring around the crosshair, and technique title cards. */
public final class HudOverlay {
    private HudOverlay() {}

    private static float flash, prevFlash;
    private static float fr = 1, fg = 1, fb = 1;
    private static float charge = -1, prevCharge = -1;
    private static String title;
    private static int titleAge, titleLife;
    private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();
    private static final Identifier SPARK = Identifier.of(GojoLimitless.MOD_ID, "textures/vfx/spark.png");
    private static final Identifier WHITE = Identifier.of(GojoLimitless.MOD_ID, "textures/gui/white.png");
    private static String vignette;
    private static float vignetteAlpha, prevVignetteAlpha, vignetteTarget;

    /** Tinted screen-edge vignette (textures/gui/vignette_<name>.png), eased toward {@code alpha} each tick. */
    public static void vignette(String name, float alpha) {
        if (name != null) vignette = name;
        vignetteTarget = alpha;
    }

    public static void flash(float intensity, float r, float g, float b) {
        if (intensity > flash) { flash = intensity; fr = r; fg = g; fb = b; }
    }

    public static void setCharge(float c) { charge = c; }

    // ---------------------------------------------------------------- anime impact frames and speed lines
    // Both are drawn on the HUD layer, after shaderpacks, so they look the same on every pack.
    private static long impactStart = -1L;
    private static float impactStrength;
    private static long speedStart = -1L, speedEnd;
    private static float speedStrength;

    /**
     * Impact frames: the screen flips to its negative for a frame or two, snaps back, flips again (the black/white
     * strobe anime uses on its heaviest hits). Strength 0..1, scaled by the flash setting; off below 0.25 of it
     * (photosensitivity) or with "Impact frames" off.
     */
    public static void impactFrames(float strength) {
        var cc = ConfigManager.get().client;
        if (!cc.impactFrames || cc.flashIntensity < 0.25) return;
        impactStart = net.minecraft.util.Util.getMeasuringTimeMs();
        impactStrength = MathHelper.clamp(strength * (float) Math.min(1.0, cc.flashIntensity / 0.85), 0f, 1f);
    }

    /** Speed lines rushing in from the screen edges for {@code seconds} (strength 0..1). */
    public static void speedLines(float seconds, float strength) {
        if (!ConfigManager.get().client.speedLines) return;
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        if (speedStart < 0 || now > speedEnd + 250) { speedStart = now; speedEnd = now; speedStrength = 0f; }
        speedEnd = Math.max(speedEnd, now + (long) (seconds * 1000));
        speedStrength = Math.max(speedStrength, strength);
    }

    /** 0 = normal, 1 = fully inverted, at this moment of the impact strobe. */
    private static float impactInvert() {
        if (impactStart < 0) return 0f;
        long t = net.minecraft.util.Util.getMeasuringTimeMs() - impactStart;
        float k;
        if (t < 55) k = 1f;                  // negative
        else if (t < 95) k = 0f;             // snap back
        else if (t < 150) k = 1f;            // negative again
        else if (t < 330) k = 1f - (t - 150) / 180f;
        else { impactStart = -1L; return 0f; }
        return k * impactStrength;
    }

    private static void drawImpact(DrawContext dc, int w, int h) {
        float a = impactInvert();
        if (a <= 0.003f) return;
        // result = a·(1 − dst) + (1 − a)·dst: a blend between the frame and its negative
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        org.joml.Matrix4f m = dc.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        var bb = net.minecraft.client.render.Tessellator.getInstance().begin(
                net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        bb.vertex(m, 0, 0, 0).color(a, a, a, a);
        bb.vertex(m, 0, h, 0).color(a, a, a, a);
        bb.vertex(m, w, h, 0).color(a, a, a, a);
        bb.vertex(m, w, 0, 0).color(a, a, a, a);
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawSpeedLines(DrawContext dc, int w, int h) {
        if (speedStart < 0) return;
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        if (now > speedEnd + 250) { speedStart = -1L; return; }
        float in = MathHelper.clamp((now - speedStart) / 180f, 0f, 1f);
        float out = MathHelper.clamp((speedEnd + 250 - now) / 250f, 0f, 1f);
        float s = speedStrength * in * out;
        if (s <= 0.003f) return;
        int seed = (int) (now / 66);                      // a new set of lines every ~4 frames: the hand-drawn flicker
        float cx = w / 2f, cy = h / 2f, diag = (float) Math.hypot(w, h) / 2f;
        org.joml.Matrix4f m = dc.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        var bb = net.minecraft.client.render.Tessellator.getInstance().begin(
                net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLES, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        int n = 84;
        for (int i = 0; i < n; i++) {
            float h1 = hash(seed * 131 + i * 7 + 1), h2 = hash(seed * 131 + i * 7 + 2), h3 = hash(seed * 131 + i * 7 + 3);
            float ang = (i + h1 * 0.9f) / n * MathHelper.TAU;
            float half = (0.0025f + 0.009f * h2 * h2) * MathHelper.TAU;   // wedge width at the edge
            float tip = diag * (0.42f + 0.38f * h3);                         // how far in it reaches (centre stays clear)
            float base = diag * 1.05f;
            float al = s * (0.25f + 0.45f * h2);
            float c0 = MathHelper.cos(ang - half), s0 = MathHelper.sin(ang - half);
            float c1 = MathHelper.cos(ang + half), s1 = MathHelper.sin(ang + half);
            float ct = MathHelper.cos(ang), st = MathHelper.sin(ang);
            bb.vertex(m, cx + c0 * base, cy + s0 * base, 0).color(1f, 1f, 1f, al);
            bb.vertex(m, cx + ct * tip, cy + st * tip, 0).color(1f, 1f, 1f, 0f);
            bb.vertex(m, cx + c1 * base, cy + s1 * base, 0).color(1f, 1f, 1f, al);
        }
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.disableBlend();
    }

    /** Shows textures/gui/title_<name>.png for {@code ticks} (ignored if the texture doesn't exist). */
    public static void title(String name, int ticks) {
        if (!ConfigManager.get().client.subtitles) return;
        title = name; titleAge = 0; titleLife = ticks;
    }

    public static void tick() {
        prevVignetteAlpha = vignetteAlpha;
        vignetteAlpha += (vignetteTarget - vignetteAlpha) * 0.2f;
        prevFlash = flash;
        flash = Math.max(0, flash - 0.12f);
        prevCharge = charge;
        if (title != null && ++titleAge > titleLife) title = null;
    }

    /**
     * A full-screen multiply by (r, g, b), drawn as a vertex-coloured grid so it can fade to no change inside a circle
     * ({x, y, radius, strength}) — the bloom's white heart.
     */
    private static void gradeGrid(DrawContext dc, int w, int h, float r, float g, float b, float[] hole) {
        int nx = hole == null ? 1 : 48, ny = hole == null ? 1 : 27;
        org.joml.Matrix4f m = dc.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        net.minecraft.client.render.BufferBuilder bb = net.minecraft.client.render.Tessellator.getInstance().begin(
                net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                float x0 = w * i / (float) nx, x1 = w * (i + 1) / (float) nx, y0 = h * j / (float) ny, y1 = h * (j + 1) / (float) ny;
                gridVertex(bb, m, x0, y0, r, g, b, hole);
                gridVertex(bb, m, x0, y1, r, g, b, hole);
                gridVertex(bb, m, x1, y1, r, g, b, hole);
                gridVertex(bb, m, x1, y0, r, g, b, hole);
            }
        }
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static void gridVertex(net.minecraft.client.render.BufferBuilder bb, org.joml.Matrix4f m, float x, float y, float r, float g, float b, float[] hole) {
        float k = 0f;
        if (hole != null) {
            float d = (float) Math.hypot(x - hole[0], y - hole[1]) / Math.max(1f, hole[2]);
            k = hole[3] * (1f - MathHelper.clamp((d - 0.55f) / 0.9f, 0f, 1f));
            k = k * k * (3 - 2 * k);
        }
        bb.vertex(m, x, y, 0).color(MathHelper.lerp(k, r, 1f), MathHelper.lerp(k, g, 1f), MathHelper.lerp(k, b, 1f), 1f);
    }

    private static final Identifier STREAK = Identifier.of(GojoLimitless.MOD_ID, "textures/vfx/streak.png");
    private static final float[][] NEON = {{0.48f, 0.24f, 1f}, {1f, 0.25f, 0.66f}, {1f, 0.19f, 0.31f}, {1f, 0.95f, 1f}};

    /** Streaks of neon light converging on the centre of a paralysed viewer's screen, flickering, never still. */
    private static void victimFlood(DrawContext dc, int w, int h, float k, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float t = (mc.world != null ? mc.world.getTime() : 0) + td;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        var ms = dc.getMatrices();
        float cx = w / 2f, cy = h / 2f, diag = (float) Math.hypot(w, h) / 2f;
        for (int i = 0; i < 70; i++) {
            float h1 = hash(i * 3 + 1), h2 = hash(i * 3 + 2), h3 = hash(i * 3 + 3);
            float a = h1 * MathHelper.TAU;
            float ph = ((h2 + t * (0.03f + 0.05f * h3)) % 1f);
            float r = diag * (1.1f - ph);
            float len = diag * (0.12f + 0.25f * h3);
            float[] c = NEON[i % NEON.length];
            float f = k * 0.35f * (0.4f + 0.6f * h2) * (float) Math.sin(Math.PI * ph);
            ms.push();
            ms.translate(cx + MathHelper.cos(a) * r, cy + MathHelper.sin(a) * r, 0);
            ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(a + MathHelper.PI));
            RenderSystem.setShaderColor(c[0] * f, c[1] * f, c[2] * f, 1f);
            dc.drawTexture(STREAK, 0, -2, (int) len, 4, 0, 0, 256, 32, 256, 32);
            ms.pop();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 2147483648f;
    }

    public static void render(DrawContext dc, RenderTickCounter rtc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float td = rtc.getTickDelta(false);
        int w = dc.getScaledWindowWidth(), h = dc.getScaledWindowHeight();

        float va = MathHelper.lerp(td, prevVignetteAlpha, vignetteAlpha);
        if (vignette != null && va > 0.003f) {
            Identifier vid = Identifier.of(GojoLimitless.MOD_ID, "textures/gui/vignette_" + vignette + ".png");
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, MathHelper.clamp(va, 0, 1));
            dc.drawTexture(vid, 0, 0, w, h, 0, 0, 1024, 576, 1024, 576);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }

        // colour grade. The erasure turns the world magenta: multiply toward magenta (darks stay dark, so silhouettes
        // keep their contrast) — except over the white heart, which stays white-hot, fading to magenta at its rim.
        // Red's flood: the same, toward red.
        float g = com.gojolimitless.client.nuke.NukeClient.grade();
        float rg = com.gojolimitless.client.nuke.NukeClient.redGrade();
        if (g > 0.003f || rg > 0.003f) {
            float[] heart = g > 0.003f ? com.gojolimitless.client.nuke.NukeClient.heartOnScreen(w, h) : null;
            float cr = 1f - 0.05f * g, cg = (1f - 0.62f * g) * (1f - 0.45f * rg), cb = (1f - 0.1f * g) * (1f - 0.5f * rg);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.DST_COLOR, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ZERO);
            gradeGrid(dc, w, h, cr, cg, cb, heart);
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            RenderSystem.setShaderColor(0.04f * g + 0.06f * rg, 0.0f, 0.06f * g, 1f);
            dc.drawTexture(WHITE, 0, 0, w, h, 0, 0, 16, 16, 16, 16);
            if (g > 0.003f) {
                RenderSystem.setShaderColor(0.2f * g, 0.03f * g, 0.28f * g, 1f);
                dc.drawTexture(Identifier.of(GojoLimitless.MOD_ID, "textures/gui/vignette_violet.png"), 0, 0, w, h, 0, 0, 1024, 576, 1024, 576);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        // Unlimited Void: the characters take a pink-violet cast while the world is white (S1E7 3.25 s)
        float wt = com.gojolimitless.client.domain.DomainClient.whiteTint();
        if (wt > 0.003f) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.DST_COLOR, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ZERO);
            RenderSystem.setShaderColor(1f - 0.02f * wt, 1f - 0.1f * wt, 1f - 0.01f * wt, 1f);
            dc.drawTexture(WHITE, 0, 0, w, h, 0, 0, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        // caught in someone's Unlimited Void: infinite information streaming across your eyes
        float vo = com.gojolimitless.client.domain.DomainClient.victimOverlay();
        if (vo > 0.003f) victimFlood(dc, w, h, vo, td);

        com.gojolimitless.client.cutscene.InsertPlayer.render(dc);
        drawSpeedLines(dc, w, h);
        drawImpact(dc, w, h);

        float lb = com.gojolimitless.client.cutscene.CutsceneDirector.letterbox();
        if (lb > 0.002f) {
            int bar = Math.round(h * 0.115f * lb);
            dc.fill(0, 0, w, bar, 0xFF000000);
            dc.fill(0, h - bar, w, h, 0xFF000000);
        }

        float wo = com.gojolimitless.client.nuke.NukeClient.whiteout();
        if (wo > 0.003f) dc.fill(0, 0, w, h, ((int) (MathHelper.clamp(wo, 0, 1) * 255) << 24) | 0xFFFFFF);

        float f = MathHelper.lerp(td, prevFlash, flash) * (float) ConfigManager.get().client.flashIntensity;
        if (f > 0.003f) {
            int a = (int) (MathHelper.clamp(f, 0, 1) * 255);
            dc.fill(0, 0, w, h, (a << 24) | ((int) (fr * 255) << 16) | ((int) (fg * 255) << 8) | (int) (fb * 255));
        }

        if (charge >= 0 && !mc.options.hudHidden) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            int dots = 48;
            float cx = w / 2f, cy = h / 2f, rad = 13f;
            for (int i = 0; i < dots; i++) {
                float k = i / (float) dots;
                boolean lit = k <= charge;
                float ang = -MathHelper.HALF_PI + k * MathHelper.TAU;
                int x = Math.round(cx + MathHelper.cos(ang) * rad) - 2, y = Math.round(cy + MathHelper.sin(ang) * rad) - 2;
                float b = lit ? (charge >= 1f ? 1f : 0.85f) : 0.18f;
                RenderSystem.setShaderColor(0.45f * b, 0.8f * b, 1f * b, 1f);
                dc.drawTexture(SPARK, x, y, 5, 5, 0, 0, 128, 128, 128, 128);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        if (title != null) {
            Identifier id = Identifier.of(GojoLimitless.MOD_ID, "textures/gui/title_" + title + ".png");
            if (EXISTS.computeIfAbsent(id, i -> mc.getResourceManager().getResource(i).isPresent())) {
                float t = titleAge + td;
                float a = Math.min(1f, t / 6f) * Math.min(1f, (titleLife - t) / 10f);
                if (a > 0) {
                    int tw = Math.min(w - 20, 360), th = tw / 4;
                    int x = (w - tw) / 2, y = (int) (h * 0.72f);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1, 1, 1, a);
                    dc.drawTexture(id, x, y, tw, th, 0, 0, 1024, 256, 1024, 256);
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    RenderSystem.disableBlend();
                }
            }
        }
    }
}
