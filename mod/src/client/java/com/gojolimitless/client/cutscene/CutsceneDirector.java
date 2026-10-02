package com.gojolimitless.client.cutscene;

import com.gojolimitless.client.cam.CameraShake;
import com.gojolimitless.client.hud.HudOverlay;
import com.gojolimitless.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Plays a Blender-authored camera track live in the world. The camera is detached from the player (who stays
 * visible, posed and animated), HUD is hidden, letterbox bars slide in, and timeline markers fire events
 * (flash, shake, titles, full-screen inserts). Anything skippable returns control immediately.
 */
public final class CutsceneDirector {
    private CutsceneDirector() {}

    private static CameraTrack track;
    /** Cutscene time runs on the game's tick clock (not the wall clock) so it stays locked to the gameplay it frames,
     *  pauses with the game and follows /tick rate. */
    private static long clientTicks;
    private static float startTime;
    private static Vec3d anchor = Vec3d.ZERO;
    private static float anchorYaw;
    /** Alternate space for tracks that switch ("space:alt"), e.g. the caster's own position during the nuke. */
    private static Vec3d altAnchor;
    private static float altYaw;
    /** How far the camera is currently raised to see over plants (eased, reset on cuts). */
    private static double lift;
    private static int lastFrame = -1;
    private static int nextEvent;
    private static boolean prevHudHidden;
    private static float letterbox, letterboxTarget;
    private static long lastFrameMs;

    // resolved pose for this frame
    private static double camX, camY, camZ;
    private static float camYaw, camPitch, camRoll, camFov;

    public static boolean active() { return track != null; }

    // development: a fixed camera for the test harness (position + look target), null = off
    private static Vec3d debugPos, debugLook;

    public static void debugCam(Vec3d pos, Vec3d look) { debugPos = pos; debugLook = look; }

    public static boolean debugCamActive() { return debugPos != null; }

    public static double[] debugCamPose() {
        Vec3d d = debugLook.subtract(debugPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[]{debugPos.x, debugPos.y, debugPos.z, yaw, pitch};
    }

    private static String skippedGroup;

    private static String group(String id) { int i = id.indexOf('_'); return i < 0 ? id : id.substring(0, i); }

    /** The player pressed skip: stop, and don't start the follow-up parts of the same cutscene. */
    public static void skip() {
        if (track != null) skippedGroup = group(track.id);
        stop();
    }

    public static void clientTick() { clientTicks++; }

    /** Game time in ticks, with the partial tick. */
    public static float now() {
        return clientTicks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    /** Set the space that "space:alt" frames are authored in (null = same as the main anchor). */
    private static float altLift;

    public static void setAltAnchor(Vec3d pos, float yaw) { setAltAnchor(pos, yaw, 0f); }

    /** @param lift how much higher the main anchor sits than the alt-space shots were framed for: they tilt up after it */
    public static void setAltAnchor(Vec3d pos, float yaw, float lift) { altAnchor = pos; altYaw = yaw; altLift = lift; }

    public static void play(String id, Vec3d anchorPos, float yaw) {
        if (!ConfigManager.get().client.cutscenes) return;
        CameraTrack t = CutsceneLibrary.get(id);
        if (t == null) return;
        if (t.continues && group(id).equals(skippedGroup)) return;
        if (!t.continues) skippedGroup = null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (track == null) prevHudHidden = mc.options.hudHidden;
        track = t;
        anchor = anchorPos;
        anchorYaw = yaw;
        startTime = now();
        lastFrameMs = Util.getMeasuringTimeMs();
        nextEvent = 0;
        lastFrame = -1;
        lift = 0;
        letterboxTarget = 1f;
        mc.options.hudHidden = true;
    }

    public static void stop() {
        if (track == null) return;
        track = null;
        letterboxTarget = 0f;
        MinecraftClient.getInstance().options.hudHidden = prevHudHidden;
    }

    /** Called once per rendered frame from the camera mixin, before the pose is read. */
    public static void update() {
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        letterbox += (letterboxTarget - letterbox) * Math.min(1f, dt * 6f);
        if (track == null) return;
        float f = (now() - startTime) / 20f * track.fps;
        if (f >= track.frames - 1) {
            fireEvents(track.frames);
            stop();
            return;
        }
        fireEvents((int) f);
        // start decoding inserts shortly before they are due
        for (int e = nextEvent; e < track.events.size(); e++) {
            CameraTrack.Event ev = track.events.get(e);
            if (ev.frame() > f + track.fps * 2.5f) break;
            if (ev.name().startsWith("insert:")) InsertPlayer.preload(ev.name().substring(7));
        }
        if (track == null) return;
        int i = (int) f, j = Math.min(i + 1, track.frames - 1);
        float k = f - i;
        if (track.cut[i]) k = 0f;                       // never blend across a hard cut
        boolean useAlt = track.alt[i] && altAnchor != null;
        Vec3d base = useAlt ? altAnchor : anchor;
        float by = useAlt ? altYaw : anchorYaw;
        float[] p = lerp3(track.pos[i], track.pos[j], k);
        float[] fw = norm(lerp3(track.fwd[i], track.fwd[j], k));
        float[] upv = norm(lerp3(track.up[i], track.up[j], k));
        // caster space → world: x = right, y = forward, z = up, rotated by the caster's yaw at cast time
        double yr = Math.toRadians(by);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);
        camX = base.x + rx * p[0] + fx * p[1];
        camY = base.y + p[2];
        camZ = base.z + rz * p[0] + fz * p[1];
        double wfx = rx * fw[0] + fx * fw[1], wfy = fw[2], wfz = rz * fw[0] + fz * fw[1];
        // roll: angle between the track's up and the no-roll up for this forward
        double wux = rx * upv[0] + fx * upv[1], wuy = upv[2], wuz = rz * upv[0] + fz * upv[1];
        Vec3d fwd = new Vec3d(wfx, wfy, wfz).normalize();
        double tilt = 0;
        if (useAlt && altLift > 0.5f && anchor != null) {
            // the main anchor was raised clear of hills: tilt up after it so what hangs there stays in frame
            tilt = Math.atan2(altLift, Math.max(4.0, Math.hypot(anchor.x - camX, anchor.z - camZ)));
            Vec3d ax = fwd.crossProduct(new Vec3d(0, 1, 0));
            if (ax.lengthSquared() > 1e-6) {
                ax = ax.normalize();
                fwd = fwd.multiply(Math.cos(tilt)).add(ax.crossProduct(fwd).multiply(Math.sin(tilt))).normalize();
            }
        }
        Vec3d right0 = fwd.crossProduct(new Vec3d(0, 1, 0));
        if (right0.lengthSquared() < 1e-6) right0 = new Vec3d(1, 0, 0);
        right0 = right0.normalize();
        Vec3d up0 = right0.crossProduct(fwd).normalize();
        Vec3d upW = new Vec3d(wux, wuy, wuz).normalize();
        camRoll = (float) Math.toDegrees(Math.atan2(upW.dotProduct(right0), upW.dotProduct(up0)));
        camFov = MathHelper.lerp(k, track.fov[i], track.fov[j]);
        Vec3d focus;
        if (track.focus[i] != null) {
            float[] q = lerp3(track.focus[i], track.focus[j], k);
            focus = new Vec3d(base.x + rx * q[0] + fx * q[1], base.y + q[2], base.z + rz * q[0] + fz * q[1]);
        } else focus = anchor.add(0, 1.6, 0);
        if (tilt > 0) focus = focus.add(0, Math.tan(tilt) * Math.hypot(focus.x - camX, focus.z - camZ), 0);
        boolean newShot = lastFrame < 0 || i < lastFrame || anyCut(lastFrame, i);
        lastFrame = i;
        avoidTerrain(fwd, focus, newShot);
    }

    private static boolean anyCut(int from, int to) {
        for (int n = Math.max(0, from); n < to && n < track.frames; n++) if (track.cut[n]) return true;
        return false;
    }

    /**
     * Real terrain is uneven. Plants (tall grass, flowers, crops) between the camera and what it frames: raise the
     * camera over them and re-aim so the subject keeps its place in the frame. A hill or tree: slide the camera in
     * front of it.
     */
    private static void avoidTerrain(Vec3d fwd, Vec3d focus, boolean newShot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) { setAngles(fwd); return; }
        Vec3d cam = new Vec3d(camX, camY, camZ);
        // how much lift does this frame need? (sampled in quarter blocks up to 1.5 — beyond that the shot is lost anyway)
        double need = 0;
        for (double h = 0; h <= 1.5; h += 0.25) {
            need = h;
            if (clearOfPlants(mc, focus, cam.add(0, h, 0))) break;
        }
        if (newShot) lift = need;
        else lift += (need - lift) * (need > lift ? 0.35 : 0.08);
        Vec3d raised = cam.add(0, lift, 0);
        if (lift > 1e-3) {
            // keep the subject where it was in the frame: rotate the view by the same angle as the line to it
            Vec3d d0 = focus.subtract(cam).normalize(), d1 = focus.subtract(raised).normalize();
            double pitchShift = Math.asin(MathHelper.clamp(d1.y, -1, 1)) - Math.asin(MathHelper.clamp(d0.y, -1, 1));
            double h = Math.sqrt(fwd.x * fwd.x + fwd.z * fwd.z);
            double pitch = Math.atan2(fwd.y, h) + pitchShift;
            fwd = new Vec3d(fwd.x / Math.max(h, 1e-6) * Math.cos(pitch), Math.sin(pitch), fwd.z / Math.max(h, 1e-6) * Math.cos(pitch));
        }
        camX = raised.x; camY = raised.y; camZ = raised.z;
        var hit = mc.world.raycast(new net.minecraft.world.RaycastContext(focus, raised,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getPos().distanceTo(focus) > 0.8) {
            Vec3d p = hit.getPos().add(focus.subtract(hit.getPos()).normalize().multiply(0.35));
            camX = p.x; camY = p.y; camZ = p.z;
        }
        setAngles(fwd);
    }

    /** Walk the line from the camera to the subject: does it pass through a plant (grass, flowers - no collision)? */
    private static boolean clearOfPlants(MinecraftClient mc, Vec3d focus, Vec3d cam) {
        Vec3d d = focus.subtract(cam);
        double len = d.length();
        if (len < 1e-3) return true;
        Vec3d step = d.multiply(1.0 / len);
        net.minecraft.util.math.BlockPos.Mutable bp = new net.minecraft.util.math.BlockPos.Mutable();
        for (double s = 0.1; s < len - 0.45; s += 0.2) {
            Vec3d p = cam.add(step.multiply(s));
            bp.set(p.x, p.y, p.z);
            var st = mc.world.getBlockState(bp);
            if (st.isAir() || !st.getCollisionShape(mc.world, bp).isEmpty()) continue;
            var shape = st.getOutlineShape(mc.world, bp);
            if (shape.isEmpty()) continue;
            double lx = p.x - bp.getX(), ly = p.y - bp.getY(), lz = p.z - bp.getZ();
            for (var box : shape.getBoundingBoxes()) {
                if (box.expand(0.05).contains(lx, ly, lz)) return false;
            }
        }
        return true;
    }

    private static void setAngles(Vec3d fwd) {
        camYaw = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
        camPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(fwd.y, -1, 1)));
    }

    private static void fireEvents(int frame) {
        while (track != null && nextEvent < track.events.size() && track.events.get(nextEvent).frame() <= frame) {
            handle(track.events.get(nextEvent).name());
            nextEvent++;
        }
    }

    /** Timeline markers: "flash:0.8", "shake:0.5", "title:purple", "insert:purple_collide", "fade:1.2". */
    private static void handle(String ev) {
        String[] kv = ev.split(":", 2);
        String k = kv[0], v = kv.length > 1 ? kv[1] : "";
        switch (k) {
            case "flash" -> HudOverlay.flash(parse(v, 0.8f), 1f, 1f, 1f);
            case "flashv" -> HudOverlay.flash(parse(v, 0.8f), 0.85f, 0.6f, 1f);
            case "flashr" -> HudOverlay.flash(parse(v, 0.8f), 1f, 0.3f, 0.22f);
            case "flashb" -> HudOverlay.flash(parse(v, 0.8f), 0.55f, 0.85f, 1f);
            case "shake" -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) CameraShake.add(parse(v, 0.4f), mc.player.getPos(), 1000);
            }
            case "title" -> HudOverlay.title(v, 60);
            case "insert" -> InsertPlayer.play(v);
            default -> {}
        }
    }

    private static float parse(String s, float d) {
        try { return Float.parseFloat(s); } catch (Exception e) { return d; }
    }

    private static float[] lerp3(float[] a, float[] b, float k) {
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static float[] norm(float[] v) {
        float l = MathHelper.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) + 1e-6f;
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    public static double x() { return camX; }
    public static double y() { return camY; }
    public static double z() { return camZ; }
    public static float yaw() { return camYaw; }
    public static float pitch() { return camPitch; }
    public static float roll() { return camRoll; }
    public static float fov() { return camFov; }
    public static float letterbox() { return letterbox; }
}
