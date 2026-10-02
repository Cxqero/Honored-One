package com.gojolimitless.client.pose;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player-model animation baked from Blender (see blender/anim/player_rig.py), sampled per frame.
 *
 * Parts: head, body (the torso), right_arm, left_arm, right_leg, left_leg, and root (the whole body). Per part:
 * rot = (pitch, yaw, roll) radians in Minecraft model convention; pos = pixels added to the part's default pivot
 * (root: pixels in entity space, x right, y up, z forward); bend = (axis, angle) radians as bendy-lib applies it.
 * Parts flagged "aim" are offset by where the head looks. Hands: finger-shape keys (see HandShapes) per hand.
 *
 * Format v1 files ("tracks": {part: [[p, y, r], ...]}) still load: rotation only.
 */
public final class PoseAnimation {
    public final String name;
    public final float fps;
    public final int frames;
    public final Map<String, float[][]> rot = new HashMap<>();
    public final Map<String, float[][]> pos = new HashMap<>();
    public final Map<String, float[][]> bend = new HashMap<>();
    public final Set<String> aim = new HashSet<>();
    public final float[] weight;
    public final List<HandKey> rightHand = new ArrayList<>();
    public final List<HandKey> leftHand = new ArrayList<>();
    /** Which arms are drawn in first person while this plays (the arms doing the sign). */
    public boolean firstPersonRight, firstPersonLeft;

    /** A finger-shape key: from this frame on the hand blends toward shape at the given weight (0 = no fingers). */
    public record HandKey(float frame, String shape, float weight) {}

    /** Result of sampling a hand: blend from shape a to shape b by k, fingers shown at weight w. */
    public record HandSample(String a, String b, float k, float w) {
        public static final HandSample NONE = new HandSample("fist", "fist", 0f, 0f);
    }

    private PoseAnimation(String name, float fps, int frames, float[] weight) {
        this.name = name; this.fps = fps; this.frames = frames; this.weight = weight;
    }

    public static PoseAnimation parse(String name, JsonObject o) {
        float fps = o.get("fps").getAsFloat();
        int frames = o.get("frames").getAsInt();
        JsonArray w = o.getAsJsonArray("weight");
        float[] weight = new float[w.size()];
        for (int i = 0; i < weight.length; i++) weight[i] = w.get(i).getAsFloat();
        PoseAnimation a = new PoseAnimation(name, fps, frames, weight);
        JsonObject tr = o.getAsJsonObject("tracks");
        for (String part : tr.keySet()) {
            JsonElement el = tr.get(part);
            if (el.isJsonArray()) {                       // v1: rotation only
                a.rot.put(part, floats(el.getAsJsonArray(), 3));
                continue;
            }
            JsonObject p = el.getAsJsonObject();
            if (p.has("rot")) a.rot.put(part, floats(p.getAsJsonArray("rot"), 3));
            if (p.has("pos")) a.pos.put(part, floats(p.getAsJsonArray("pos"), 3));
            if (p.has("bend")) a.bend.put(part, floats(p.getAsJsonArray("bend"), 2));
        }
        if (o.has("aim")) for (var el : o.getAsJsonArray("aim")) a.aim.add(el.getAsString());
        if (o.has("hands")) {
            JsonObject h = o.getAsJsonObject("hands");
            if (h.has("right")) keys(h.getAsJsonArray("right"), a.rightHand);
            if (h.has("left")) keys(h.getAsJsonArray("left"), a.leftHand);
        }
        if (o.has("first_person")) {
            for (var el : o.getAsJsonArray("first_person")) {
                if (el.getAsString().equals("right_arm")) a.firstPersonRight = true;
                if (el.getAsString().equals("left_arm")) a.firstPersonLeft = true;
            }
        }
        return a;
    }

    private static float[][] floats(JsonArray arr, int n) {
        float[][] f = new float[arr.size()][n];
        for (int i = 0; i < f.length; i++) {
            JsonArray v = arr.get(i).getAsJsonArray();
            for (int c = 0; c < n; c++) f[i][c] = v.get(c).getAsFloat();
        }
        return f;
    }

    private static void keys(JsonArray arr, List<HandKey> out) {
        for (var el : arr) {
            JsonArray k = el.getAsJsonArray();
            out.add(new HandKey(k.get(0).getAsFloat(), k.get(1).getAsString(), k.get(2).getAsFloat()));
        }
        out.sort((x, y) -> Float.compare(x.frame(), y.frame()));
    }

    public float lengthTicks() { return frames / fps * 20f; }

    private float frameAt(float t) { return Math.max(0, t / 20f * fps); }

    private boolean sample(Map<String, float[][]> m, String part, float t, float[] out) {
        float[][] f = m.get(part);
        if (f == null) return false;
        float fr = frameAt(t);
        int i = Math.min((int) fr, f.length - 1), j = Math.min(i + 1, f.length - 1);
        float k = fr - (int) fr;
        for (int c = 0; c < out.length && c < f[i].length; c++) out[c] = f[i][c] + (f[j][c] - f[i][c]) * k;
        return true;
    }

    /** Sample a part's rotation at time t (ticks since start). */
    public boolean sample(String part, float t, float[] out) { return sample(rot, part, t, out); }

    public boolean samplePos(String part, float t, float[] out) { return sample(pos, part, t, out); }

    public boolean sampleBend(String part, float t, float[] out) { return sample(bend, part, t, out); }

    public float weightAt(float t) {
        float fr = frameAt(t);
        int i = Math.min((int) fr, weight.length - 1), j = Math.min(i + 1, weight.length - 1);
        float k = fr - (int) fr;
        return weight[i] + (weight[j] - weight[i]) * k;
    }

    /** The hand's finger shape at time t: blends linearly between keys. */
    public HandSample sampleHand(boolean right, float t) {
        List<HandKey> ks = right ? rightHand : leftHand;
        if (ks.isEmpty()) return HandSample.NONE;
        float fr = frameAt(t);
        HandKey prev = ks.get(0);
        if (fr <= prev.frame()) return new HandSample(prev.shape(), prev.shape(), 0f, prev.weight());
        for (int i = 1; i < ks.size(); i++) {
            HandKey next = ks.get(i);
            if (fr < next.frame()) {
                float k = (fr - prev.frame()) / Math.max(1e-3f, next.frame() - prev.frame());
                k = k * k * (3 - 2 * k);
                return new HandSample(prev.shape(), next.shape(), k, prev.weight() + (next.weight() - prev.weight()) * k);
            }
            prev = next;
        }
        return new HandSample(prev.shape(), prev.shape(), 0f, prev.weight());
    }
}
