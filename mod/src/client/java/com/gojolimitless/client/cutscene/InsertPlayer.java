package com.gojolimitless.client.cutscene;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.mixin.client.NativeImageAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-screen pre-rendered inserts (Blender renders: hand-sign close-ups, the collision streaks, the implosion, the
 * star glint). Frames live in assets/gojolimitless/inserts/<name>/NNN.jpg|png with meta.json
 * {fps, frames, ext, blend: "alpha"|"add"}. Frames are decoded ahead on worker threads (the first ones as soon as
 * the cutscene that contains the insert starts) and streamed into one GPU texture, then drawn over the whole screen
 * on the HUD layer — which shaderpacks don't touch. If decoding falls behind, the newest ready frame is shown.
 */
public final class InsertPlayer {
    private InsertPlayer() {}

    private static final int AHEAD = 8;
    private static final Identifier TEX_ID = Identifier.of(GojoLimitless.MOD_ID, "dynamic/insert");
    private static NativeImageBackedTexture texture;

    private record Meta(float fps, int frames, boolean additive, String ext) {}

    private static final Map<String, Meta> METAS = new HashMap<>();
    private static final Map<String, Map<Integer, CompletableFuture<NativeImage>>> PRELOADED = new ConcurrentHashMap<>();

    private static String name;
    private static Meta meta;
    private static int shown = -1;
    private static float startTime;
    private static Map<Integer, CompletableFuture<NativeImage>> pending = new ConcurrentHashMap<>();

    public static boolean active() { return name != null; }

    private static Meta meta(String insert) {
        return METAS.computeIfAbsent(insert, n -> {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of(GojoLimitless.MOD_ID, "inserts/" + n + "/meta.json"));
            if (res.isEmpty()) { GojoLimitless.LOG.warn("Missing insert {}", n); return null; }
            try (Reader r = res.get().getReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                return new Meta(o.get("fps").getAsFloat(), o.get("frames").getAsInt(),
                        o.has("blend") && o.get("blend").getAsString().equals("add"), o.has("ext") ? o.get("ext").getAsString() : "png");
            } catch (Exception e) {
                GojoLimitless.LOG.error("Bad insert meta {}", n, e);
                return null;
            }
        });
    }

    /** Start decoding the first frames of an insert that will play soon. */
    public static void preload(String insert) {
        Meta m = meta(insert);
        if (m == null || PRELOADED.containsKey(insert)) return;
        Map<Integer, CompletableFuture<NativeImage>> map = new ConcurrentHashMap<>();
        for (int i = 0; i < Math.min(AHEAD, m.frames); i++) map.put(i, load(insert, m, i));
        PRELOADED.put(insert, map);
    }

    public static void play(String insert) {
        stop();
        Meta m = meta(insert);
        if (m == null) return;
        name = insert;
        meta = m;
        shown = -1;
        startTime = CutsceneDirector.now();
        Map<Integer, CompletableFuture<NativeImage>> pre = PRELOADED.remove(insert);
        pending = pre != null ? pre : new ConcurrentHashMap<>();
        for (int i = 0; i < Math.min(AHEAD, m.frames); i++) request(i);
    }

    public static void stop() {
        name = null;
        release(pending);
        pending = new ConcurrentHashMap<>();
    }

    /** Drop everything (skip / disconnect). */
    public static void clearAll() {
        stop();
        PRELOADED.values().forEach(InsertPlayer::release);
        PRELOADED.clear();
        METAS.clear();
    }

    private static void release(Map<Integer, CompletableFuture<NativeImage>> map) {
        map.values().forEach(f -> f.thenAccept(img -> { if (img != null) img.close(); }));
        map.clear();
    }

    private static void request(int i) {
        if (i >= meta.frames || pending.containsKey(i)) return;
        pending.put(i, load(name, meta, i));
    }

    private static CompletableFuture<NativeImage> load(String n, Meta m, int i) {
        return CompletableFuture.supplyAsync(() -> {
            Identifier id = Identifier.of(GojoLimitless.MOD_ID, String.format("inserts/%s/%03d.%s", n, i, m.ext));
            try (InputStream in = MinecraftClient.getInstance().getResourceManager().getResource(id).orElseThrow().getInputStream()) {
                return m.ext.equals("png") ? NativeImage.read(in) : decodeImage(in);
            } catch (Exception e) {
                return null;
            }
        }, Util.getMainWorkerExecutor());
    }

    /** JPEG (or anything stb_image reads) → NativeImage. NativeImage.read itself only accepts PNG. */
    public static NativeImage decodeImage(InputStream in) throws java.io.IOException {
        java.nio.ByteBuffer buf = com.mojang.blaze3d.platform.TextureUtil.readResource(in);
        buf.rewind();
        try (org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
            java.nio.ByteBuffer px = org.lwjgl.stb.STBImage.stbi_load_from_memory(buf, w, h, c, 4);
            if (px == null) throw new java.io.IOException(org.lwjgl.stb.STBImage.stbi_failure_reason());
            int W = w.get(0), H = h.get(0);
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, W, H, false);
            // RGBA bytes are exactly NativeImage's RGBA layout: one bulk copy
            org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(px), ((NativeImageAccessor) (Object) img).gojolimitless$pointer(), (long) W * H * 4);
            org.lwjgl.stb.STBImage.stbi_image_free(px);
            return img;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(buf);
        }
    }

    public static void render(DrawContext dc) {
        if (name == null) return;
        int f = (int) ((CutsceneDirector.now() - startTime) / 20f * meta.fps);
        if (f >= meta.frames) { stop(); return; }
        for (int i = f; i < Math.min(meta.frames, f + AHEAD); i++) request(i);
        // newest decoded frame not later than f
        int best = -1;
        for (int i = f; i > shown; i--) {
            CompletableFuture<NativeImage> fut = pending.get(i);
            if (fut != null && fut.isDone()) { best = i; break; }
        }
        if (best >= 0) {
            NativeImage img = pending.remove(best).join();
            final int b = best;
            pending.entrySet().removeIf(e -> {
                if (e.getKey() < b) { e.getValue().thenAccept(x -> { if (x != null) x.close(); }); return true; }
                return false;
            });
            if (img != null) upload(img);
            shown = best;
        }
        if (shown < 0 || texture == null) return;
        int w = dc.getScaledWindowWidth(), h = dc.getScaledWindowHeight();
        NativeImage img = texture.getImage();
        if (img == null) return;
        RenderSystem.enableBlend();
        if (meta.additive) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        // cover the screen, keeping the insert's aspect (crop the overflow)
        float ia = img.getWidth() / (float) img.getHeight(), sa = w / (float) h;
        int dw = w, dh = h, ox = 0, oy = 0;
        if (sa > ia) { dh = Math.round(w / ia); oy = (h - dh) / 2; } else { dw = Math.round(h * ia); ox = (w - dw) / 2; }
        dc.drawTexture(TEX_ID, ox, oy, dw, dh, 0, 0, img.getWidth(), img.getHeight(), img.getWidth(), img.getHeight());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void upload(NativeImage img) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (texture == null || texture.getImage() == null
                || texture.getImage().getWidth() != img.getWidth() || texture.getImage().getHeight() != img.getHeight()) {
            if (texture != null) texture.close();
            texture = new NativeImageBackedTexture(img);
            texture.setFilter(true, false);
            mc.getTextureManager().registerTexture(TEX_ID, texture);
        } else {
            texture.setImage(img);
            texture.upload();
        }
    }
}
