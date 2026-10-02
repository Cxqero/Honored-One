package com.gojolimitless.client.cutscene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A camera move authored in Blender (blender/cutscene/*.py) in "caster space":
 * x = caster's right, y = caster's forward, z = up (metres = blocks), origin at the caster's feet.
 * Per frame: position, forward vector, up vector, vertical FOV (degrees) and the look-at point (what the shot must
 * keep in view). Events come from timeline markers.
 *
 * A track can switch between two spaces: the main anchor and an alternate one (for the nuke, the convergence point
 * and the caster's own position), with "space:alt" / "space:main" markers. Hard cuts are detected from the data so
 * the camera never blends between two shots.
 */
public final class CameraTrack {
    public final String id;
    public final float fps;
    public final int frames;
    public final float[][] pos, fwd, up;
    public final float[] fov;
    /** Look-at point per frame (null in tracks exported before it existed). */
    public final float[][] focus;
    /** Which space each frame is in: false = main anchor, true = alternate anchor. */
    public final boolean[] alt;
    /** cut[i]: frames i and i+1 belong to different shots. */
    public final boolean[] cut;
    public final List<Event> events = new ArrayList<>();
    /** Follow-up part of a multi-part cutscene: not played if the player skipped the part before it. */
    public boolean continues;

    public record Event(int frame, String name) {}

    private CameraTrack(String id, float fps, int frames) {
        this.id = id; this.fps = fps; this.frames = frames;
        pos = new float[frames][]; fwd = new float[frames][]; up = new float[frames][]; fov = new float[frames];
        focus = new float[frames][]; alt = new boolean[frames]; cut = new boolean[frames];
    }

    public static CameraTrack parse(String id, JsonObject o) {
        float fps = o.get("fps").getAsFloat();
        JsonArray fr = o.getAsJsonArray("frames");
        CameraTrack t = new CameraTrack(id, fps, fr.size());
        for (int i = 0; i < fr.size(); i++) {
            JsonArray a = fr.get(i).getAsJsonArray();   // [px,py,pz, fx,fy,fz, ux,uy,uz, fov]
            t.pos[i] = new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
            t.fwd[i] = new float[]{a.get(3).getAsFloat(), a.get(4).getAsFloat(), a.get(5).getAsFloat()};
            t.up[i] = new float[]{a.get(6).getAsFloat(), a.get(7).getAsFloat(), a.get(8).getAsFloat()};
            t.fov[i] = a.get(9).getAsFloat();
            if (a.size() >= 13) t.focus[i] = new float[]{a.get(10).getAsFloat(), a.get(11).getAsFloat(), a.get(12).getAsFloat()};
        }
        if (o.has("events")) {
            for (JsonElement el : o.getAsJsonArray("events")) {
                JsonObject ev = el.getAsJsonObject();
                t.events.add(new Event(ev.get("frame").getAsInt(), ev.get("name").getAsString()));
            }
        }
        t.events.sort((x, y) -> Integer.compare(x.frame, y.frame));
        t.continues = o.has("continues") && o.get("continues").getAsBoolean();
        boolean inAlt = false;
        int e = 0;
        for (int i = 0; i < t.frames; i++) {
            while (e < t.events.size() && t.events.get(e).frame <= i) {
                String n = t.events.get(e++).name;
                if (n.equals("space:alt")) inAlt = true;
                else if (n.equals("space:main")) inAlt = false;
            }
            t.alt[i] = inAlt;
        }
        for (int i = 0; i + 1 < t.frames; i++) {
            float[] a = t.pos[i], b = t.pos[i + 1], fa = t.fwd[i], fb = t.fwd[i + 1];
            float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
            float dot = fa[0] * fb[0] + fa[1] * fb[1] + fa[2] * fb[2];
            t.cut[i] = t.alt[i] != t.alt[i + 1] || dx * dx + dy * dy + dz * dz > 1.0f || dot < 0.97f;
        }
        return t;
    }

    public float seconds() { return frames / fps; }
}
