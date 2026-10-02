package com.gojolimitless.client.render;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.client.cutscene.InsertPlayer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Looping Blender renders used as world textures (the inside of Unlimited Void: the panorama, the animated black
 * hole). Frames live in assets/gojolimitless/loops/<name>/NNN.jpg with meta.json {fps, frames}; they are decoded
 * ahead on worker threads and streamed into one dynamic texture per loop. A loop nobody has asked for in a few
 * seconds is freed.
 */
public final class LoopTextures {
    private LoopTextures() {}

    private static final int AHEAD = 6;

    private static final class Loop {
        final String name;
        final Identifier id;
        float fps = 12;
        int frames = 1;
        boolean ok;
        NativeImageBackedTexture tex;
        int shown = -1;
        final Map<Integer, CompletableFuture<NativeImage>> pending = new HashMap<>();
        long lastUse;

        Loop(String name) {
            this.name = name;
            this.id = Identifier.of(GojoLimitless.MOD_ID, "dynamic/loop_" + name);
            var res = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of(GojoLimitless.MOD_ID, "loops/" + name + "/meta.json"));
            if (res.isEmpty()) { GojoLimitless.LOG.warn("Missing loop {}", name); return; }
            try (Reader r = res.get().getReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                fps = o.get("fps").getAsFloat();
                frames = Math.max(1, o.get("frames").getAsInt());
                ok = true;
            } catch (Exception e) {
                GojoLimitless.LOG.error("Bad loop meta {}", name, e);
            }
        }
    }

    private static final Map<String, Loop> LOOPS = new HashMap<>();

    /**
     * The texture showing loop {@code name} at {@code seconds} (it wraps), or null until its first frame is decoded.
     * Call once per rendered frame while it is on screen.
     */
    public static Identifier frame(String name, float seconds) {
        Loop l = LOOPS.computeIfAbsent(name, Loop::new);
        if (!l.ok) return null;
        l.lastUse = Util.getMeasuringTimeMs();
        int f = Math.floorMod((int) Math.floor(seconds * l.fps), l.frames);
        for (int i = 0; i < Math.min(AHEAD, l.frames); i++) request(l, (f + i) % l.frames);
        CompletableFuture<NativeImage> want = l.pending.get(f);
        if (f != l.shown && want != null && want.isDone()) {
            NativeImage img = want.join();
            l.pending.remove(f);
            if (img != null) upload(l, img);
            l.shown = f;
            // drop decoded frames that are now behind us
            l.pending.entrySet().removeIf(e -> {
                int d = Math.floorMod(e.getKey() - f, l.frames);
                if (d > AHEAD) { e.getValue().thenAccept(x -> { if (x != null) x.close(); }); return true; }
                return false;
            });
        }
        return l.tex != null ? l.id : null;
    }

    /** Start decoding a loop's first frames ahead of time. */
    public static void preload(String name, float seconds) { frame(name, seconds); }

    private static void request(Loop l, int i) {
        if (i == l.shown || l.pending.containsKey(i)) return;
        l.pending.put(i, CompletableFuture.supplyAsync(() -> {
            Identifier id = Identifier.of(GojoLimitless.MOD_ID, String.format("loops/%s/%03d.jpg", l.name, i));
            try (InputStream in = MinecraftClient.getInstance().getResourceManager().getResource(id).orElseThrow().getInputStream()) {
                return InsertPlayer.decodeImage(in);
            } catch (Exception e) {
                return null;
            }
        }, Util.getMainWorkerExecutor()));
    }

    private static void upload(Loop l, NativeImage img) {
        if (l.tex == null || l.tex.getImage() == null || l.tex.getImage().getWidth() != img.getWidth() || l.tex.getImage().getHeight() != img.getHeight()) {
            if (l.tex != null) l.tex.close();
            l.tex = new NativeImageBackedTexture(img);
            l.tex.setFilter(true, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(l.id, l.tex);
        } else {
            l.tex.setImage(img);
            l.tex.upload();
        }
    }

    /** Free loops that haven't been drawn for a while (call each client tick). */
    public static void tick() {
        long now = Util.getMeasuringTimeMs();
        LOOPS.values().removeIf(l -> {
            if (now - l.lastUse < 8000) return false;
            l.pending.values().forEach(fu -> fu.thenAccept(x -> { if (x != null) x.close(); }));
            l.pending.clear();
            if (l.tex != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(l.id);
            return true;
        });
    }

    public static void clear() {
        LOOPS.values().forEach(l -> {
            l.pending.values().forEach(fu -> fu.thenAccept(x -> { if (x != null) x.close(); }));
            if (l.tex != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(l.id);
        });
        LOOPS.clear();
    }
}
