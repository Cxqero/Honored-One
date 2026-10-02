package com.gojolimitless.client.anim;

import com.gojolimitless.GojoLimitless;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Blocky finger geometry and the named hand shapes (hand signs), from assets/gojolimitless/hands/hands.json — the
 * same file Blender's preview rig reads, so the game matches the previews.
 */
public final class HandShapes implements SimpleSynchronousResourceReloadListener {
    public static final String[] FINGERS = {"index", "middle", "ring", "pinky", "thumb"};

    /** base (x, y, z) px from the hand end; cross-section (sx, sz); two segment lengths; rest (pitch, roll) deg. */
    public record Finger(float bx, float by, float bz, float sx, float sz, float len1, float len2, float restPitch, float restRoll) {}

    /** Per finger: curl1, curl2, splay, opposition (degrees). */
    public record Shape(float[][] f) {}

    private static final Map<String, Finger> FINGER = new HashMap<>();
    private static final Map<String, Shape> SHAPES = new HashMap<>();
    private static float sealCrossDepth = 0.55f;

    public static Finger finger(String name) { return FINGER.get(name); }

    public static Shape shape(String name) {
        Shape s = SHAPES.get(name);
        return s != null ? s : SHAPES.get("relaxed");
    }

    public static float sealCrossDepth() { return sealCrossDepth; }

    /** Blend two shapes; out[finger][0..3]. */
    public static void blend(String a, String b, float k, float[][] out) {
        Shape sa = shape(a), sb = shape(b);
        for (int i = 0; i < FINGERS.length; i++) {
            for (int c = 0; c < 4; c++) {
                float x = sa == null ? 0 : sa.f[i][c], y = sb == null ? 0 : sb.f[i][c];
                out[i][c] = x + (y - x) * k;
            }
        }
    }

    @Override
    public Identifier getFabricId() { return Identifier.of(GojoLimitless.MOD_ID, "hand_shapes"); }

    @Override
    public void reload(ResourceManager manager) {
        FINGER.clear();
        SHAPES.clear();
        var res = manager.getResource(Identifier.of(GojoLimitless.MOD_ID, "hands/hands.json"));
        if (res.isEmpty()) { GojoLimitless.LOG.warn("hands.json missing"); return; }
        try (Reader r = res.get().getReader()) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject fs = o.getAsJsonObject("fingers");
            for (String n : FINGERS) {
                JsonObject f = fs.getAsJsonObject(n);
                JsonArray b = f.getAsJsonArray("base"), s = f.getAsJsonArray("size"), seg = f.getAsJsonArray("segments"), rest = f.getAsJsonArray("rest");
                FINGER.put(n, new Finger(b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat(),
                        s.get(0).getAsFloat(), s.get(1).getAsFloat(), seg.get(0).getAsFloat(), seg.get(1).getAsFloat(),
                        rest.get(0).getAsFloat(), rest.get(1).getAsFloat()));
            }
            JsonObject ss = o.getAsJsonObject("shapes");
            for (String name : ss.keySet()) {
                JsonObject s = ss.getAsJsonObject(name);
                float[][] f = new float[FINGERS.length][4];
                for (int i = 0; i < FINGERS.length; i++) {
                    JsonArray a = s.getAsJsonArray(FINGERS[i]);
                    for (int c = 0; c < a.size() && c < 4; c++) f[i][c] = a.get(c).getAsFloat();
                }
                SHAPES.put(name, new Shape(f));
            }
            if (o.has("seal_cross_depth")) sealCrossDepth = o.get("seal_cross_depth").getAsFloat();
            GojoLimitless.LOG.info("Loaded {} hand shapes", SHAPES.size());
        } catch (Exception e) {
            GojoLimitless.LOG.error("Bad hands.json", e);
        }
    }
}
