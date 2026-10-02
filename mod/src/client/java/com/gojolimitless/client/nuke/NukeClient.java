package com.gojolimitless.client.nuke;

import com.gojolimitless.client.cutscene.CutsceneDirector;
import com.gojolimitless.client.hud.HudOverlay;
import com.gojolimitless.client.sound.TrackedLoopSound;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.NukeEntity;
import com.gojolimitless.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static com.gojolimitless.entity.NukeEntity.*;

/**
 * Client half of the remote Hollow Purple: everything that follows the timeline on the caster's side —
 * flying the caster up to the hover point, the incantation subtitles and cues, the magenta grade and the
 * white-out, the Six Eyes glow, loop sounds, and which pose the caster holds.
 */
public final class NukeClient {
    private NukeClient() {}

    private static NukeEntity own;
    /** Every nuke in the client world, refreshed each tick (the per-frame queries only walk this). */
    private static final java.util.List<NukeEntity> ACTIVE = new java.util.ArrayList<>();
    private static int lastT = -1;
    private static Vec3d leapStart;
    private static float lookYaw, lookPitch;

    // ------------------------------------------------------------------ discovery
    public static void onLoad(NukeEntity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && e.getOwnerId() == mc.player.getId()) {
            own = e;
            lastT = -1;
            leapStart = null;
            lookYaw = e.getYawDeg();
            lookPitch = mc.player.getPitch();
            CutsceneDirector.setAltAnchor(e.getOrigin(), e.getYawDeg(), altLift(e));
        }
        startSounds(e);
    }

    private static boolean alive(NukeEntity e) { return !e.isRemoved(); }

    private static float t(NukeEntity e) {
        return e.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    /** The active nuke cast by this entity (for poses and eye glow of any caster). */
    public static NukeEntity forCaster(Entity caster) {
        if (own != null && !own.isRemoved() && own.getOwnerId() == caster.getId()) return own;
        for (Entity e : caster.getWorld().getOtherEntities(caster, caster.getBoundingBox().expand(160), x -> x instanceof NukeEntity)) {
            NukeEntity n = (NukeEntity) e;
            if (n.getOwnerId() == caster.getId() && n.age < T_END + 5) return n;
        }
        return null;
    }

    /** Movement keys do nothing while our nuke runs (the caster is rooted, then carried by Blue). */
    public static boolean locked() {
        return own != null && !own.isRemoved() && own.age < T_END;
    }

    // ------------------------------------------------------------------ per tick
    public static void tick(MinecraftClient mc) {
        if (own != null && (own.isRemoved() || mc.world == null || own.getWorld() != mc.world)) own = null;
        ACTIVE.clear();
        if (mc.player == null || mc.world == null) return;
        for (Entity e : mc.world.getEntities()) if (e instanceof NukeEntity n && !n.isRemoved()) ACTIVE.add(n);
        if (own == null) {
            // tracked data (owner) arrives just after the spawn packet, so adopt our nuke here rather than on load
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof NukeEntity n && n.getOwnerId() == mc.player.getId() && n.age < T_END) {
                    own = n; lastT = -1; leapStart = null;
                    lookYaw = n.getYawDeg(); lookPitch = mc.player.getPitch();
                    CutsceneDirector.setAltAnchor(n.getOrigin(), n.getYawDeg(), altLift(n));
                    break;
                }
            }
        }
        if (own == null) return;
        int t = own.age;
        for (int k = lastT + 1; k <= t; k++) cue(k);
        lastT = t;
        if (t < T_END) {
            carry(mc.player, t);
            aim(mc.player, t);
        }
    }

    /** Subtitles and incantation cues (the caster's "voice"), heard wherever the camera is — each technique's
     *  incantation in its own voice: Red's (位相・波羅蜜・光の柱), Blue's after the fact (位相・黄昏・智慧の瞳), then
     *  Purple's (九綱・偏光・烏と声明・表裏の間), the order of chapter 235. */
    private static void cue(int k) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String title = switch (k) {
            case T_RED_1 -> "red_1";
            case T_RED_2 -> "red_2";
            case T_RED_3 -> "red_3";
            case T_BLUE_1 -> "blue_1";
            case T_BLUE_2 -> "blue_2";
            case T_BLUE_3 -> "blue_3";
            case T_PURPLE_1 -> "purple_1";
            case T_PURPLE_2 -> "purple_2";
            case T_PURPLE_3 -> "purple_3";
            case T_PURPLE_4 -> "purple_4";
            default -> null;
        };
        if (title != null) {
            HudOverlay.title(title, k >= T_PURPLE_1 ? 20 : 22);
            int word = title.charAt(title.length() - 1) - '1';
            var sound = title.startsWith("red") ? ModSounds.RED_INCANT : title.startsWith("blue") ? ModSounds.BLUE_INCANT : ModSounds.PURPLE_INCANT;
            mc.getSoundManager().play(PositionedSoundInstance.master(sound, 0.94f + 0.05f * word, 0.9f));
        }
        if (k == T_BLOOM + 10 && CutsceneDirector.active()) HudOverlay.flash(1.0f, 1f, 0.9f, 1f);   // clip 20.5 s
        if (k == T_BLOOM + 8) HudOverlay.title("purple", T_GLINT - T_BLOOM - 10);
    }

    /** Blue's attraction carries the caster up to his hover point behind the target, and holds him there. */
    private static void carry(ClientPlayerEntity p, int t) {
        if (t < T_LEAP) return;
        Vec3d hover = own.getHover();
        if (leapStart == null) leapStart = p.getPos();
        Vec3d target;
        if (t < T_LEAP_END) {
            double k = smooth(T_LEAP, T_LEAP_END, t + 1);
            Vec3d up = new Vec3d(0, 1, 0);
            Vec3d a = leapStart, d = hover;
            Vec3d b = a.add(up.multiply(9)).add(d.subtract(a).multiply(0.25));
            Vec3d c = d.add(up.multiply(4)).subtract(fwd(own.getYawDeg()).multiply(3));
            target = bezier(a, b, c, d, k);
        } else {
            double bob = 0.18 * Math.sin((t - T_LEAP_END) * 0.11);
            // caught in the bloom: shoved back a little by the erupting mass
            double push = 2.2 * smooth(T_BLOOM, T_BLOOM + 20, t);
            target = hover.add(0, bob, 0).subtract(fwd(own.getYawDeg()).multiply(push));
        }
        p.setPosition(target.x, target.y, target.z);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0;
        p.setOnGround(false);
    }

    /** With the cutscene camera detached, turn the caster's head the way the story needs (never forced otherwise). */
    private static void aim(ClientPlayerEntity p, int t) {
        if (!CutsceneDirector.active()) return;
        float yaw = own.getYawDeg();
        float pitch;
        if (t < T_THROW) pitch = 0f;
        else if (t < T_THROW + 28) pitch = -68f;
        else if (t < T_SKY) pitch = -4f;
        else if (t < T_LEAP) pitch = -38f;
        else {
            Vec3d d = own.getPos().subtract(p.getEyePos());
            pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        }
        lookYaw += MathHelper.wrapDegrees(yaw - lookYaw) * 0.35f;
        lookPitch += (pitch - lookPitch) * 0.25f;
        p.prevYaw = p.getYaw(); p.prevPitch = p.getPitch();
        p.setYaw(lookYaw); p.setPitch(lookPitch);
        p.setHeadYaw(lookYaw); p.setBodyYaw(lookYaw);
    }

    // ------------------------------------------------------------------ HUD
    /** Magenta grade strength for the camera (anyone near the erasure sees the sky turn). */
    public static float grade() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 0f;
        float g = 0f;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        for (NukeEntity n : ACTIVE) {
            float t = t(n);
            if (t < T_BLOOM) continue;
            float up = (float) smooth(T_BLOOM, T_BLOOM + 3, t);
            float down = 1f - (float) smooth(T_WHITE, T_WHITE + 6, t);         // the explosion shows its own colours
            double d = cam.distanceTo(n.getPos());
            float near = (float) (1.0 - smooth(n.getBlastRadius() * 1.2, n.getBlastRadius() * 4.5, d));
            g = Math.max(g, up * down * near);
        }
        return g;
    }

    /**
     * Where the bloom's white heart is on screen (for the grade to leave it white): {x, y, radius} in scaled GUI pixels
     * plus its strength, or null when there is none in front of the camera.
     */
    public static float[] heartOnScreen(int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        var camera = mc.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();
        org.joml.Vector3f f = camera.getHorizontalPlane(), u = camera.getVerticalPlane(), l = camera.getDiagonalPlane();
        float fov = CutsceneDirector.active() ? CutsceneDirector.fov() : mc.options.getFov().getValue().floatValue();
        float tanV = (float) Math.tan(Math.toRadians(fov) / 2), tanH = tanV * w / (float) h;
        for (NukeEntity n : ACTIVE) {
            float t = t(n);
            if (t < T_BLOOM || t > T_WHITE + 10) continue;
            float r = n.blastRadiusAt(t);
            float rc = Math.max(0.5f, (2.5f + 0.1f * r) * (1f - 0.4f * (float) smooth(T_BLOOM + n.getExpandTicks(), T_END, t)));
            Vec3d d = n.getPos().subtract(cam);
            double z = d.x * f.x() + d.y * f.y() + d.z * f.z();
            if (z < rc * 0.5) continue;
            double x = -(d.x * l.x() + d.y * l.y() + d.z * l.z()), y = d.x * u.x() + d.y * u.y() + d.z * u.z();
            float sx = (float) (w / 2.0 + x / z / tanH * w / 2.0), sy = (float) (h / 2.0 - y / z / tanV * h / 2.0);
            float sr = (float) (rc / Math.sqrt(Math.max(1e-3, d.lengthSquared() - rc * rc)) / tanV * h / 2.0);
            float k = (float) smooth(T_BLOOM, T_BLOOM + 3, t);
            return new float[]{sx, sy, sr, k};
        }
        return null;
    }

    /** The white-out after the glint (only for the caster: it's his moment). */
    public static float whiteout() {
        if (own == null || own.isRemoved()) return 0f;
        float t = t(own);
        if (t < T_WHITE) return 0f;
        float a = (float) smooth(T_WHITE, T_WHITE + 1, t) * (1f - (float) smooth(T_WHITE + 1, T_WHITE + 5, t));
        return a * (0.45f + 0.55f * (float) ConfigManager.get().client.flashIntensity);
    }

    /** The sky floods red as Red swells at the fingertips and is hurled up (clip 3.5-5.0 s): strength of the red grade. */
    public static float redGrade() {
        if (own == null || own.isRemoved()) return 0f;
        float t = t(own);
        return (float) (0.2 * smooth(T_RED_2, T_SIGN, t) + 0.55 * smooth(T_SIGN, T_THROW + 4, t)) * (1f - (float) smooth(T_THROW + 8, T_THROW + 30, t));
    }

    /**
     * While the heart of the bloom blazes, creatures near it are drawn unlit - dark shapes against the white, like the
     * clip's silhouettes (0 = normal light, 1 = silhouette).
     */
    public static float silhouette(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 0f;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float best = 0f;
        for (NukeEntity n : ACTIVE) {
            if (n.getWorld() != e.getWorld()) continue;
            float t = t(n);
            if (t < T_BLOOM || t > T_WHITE + 10) continue;
            if (e.squaredDistanceTo(n) > n.getBlastRadius() * n.getBlastRadius() * 1.2) continue;
            // only when it stands against the heart as seen from the camera
            Vec3d toHeart = n.getPos().subtract(cam), toE = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(cam);
            double dh = toHeart.length(), de = toE.length();
            if (de > dh || dh < 1e-3 || de < 1e-3) continue;
            float against = (float) smooth(0.86, 0.975, toHeart.dotProduct(toE) / (dh * de));
            best = Math.max(best, against * (float) (smooth(T_BLOOM, T_BLOOM + 4, t) * (1 - smooth(T_WHITE - 10, T_WHITE + 5, t))));
        }
        return best;
    }

    /** The Red charge floods the edges of the caster's screen red, like the incantation Red. */
    public static float redVignette() {
        if (own == null || own.isRemoved()) return 0f;
        float t = t(own);
        if (t < T_RED_FORM || t >= T_THROW + 6) return 0f;
        return 0.25f + 0.55f * (float) smooth(T_RED_FORM, T_SIGN, t);
    }

    // ------------------------------------------------------------------ poses / eyes
    public record PoseRef(String anim, float time, boolean hold) {}

    public static PoseRef pose(Entity caster) {
        NukeEntity n = forCaster(caster);
        if (n == null) return null;
        float t = t(n);
        if (t < T_THROW) return new PoseRef("nuke_red", t, true);
        if (t < T_LEAP) return new PoseRef("nuke_throw", t - T_THROW, true);
        if (t < T_BLOOM) return new PoseRef("nuke_air", t - T_LEAP, true);
        if (t < T_END) return new PoseRef("nuke_brace", t - T_BLOOM, true);
        return null;
    }

    /** Six Eyes glow: faint through the charge, blazing in the close-up and while airborne. */
    public static float eyeGlow(LivingEntity caster) {
        NukeEntity n = forCaster(caster);
        if (n == null) return 0f;
        float t = t(n);
        float g = 0.35f * (float) smooth(T_RED_FORM, T_SIGN, t);
        g = Math.max(g, (float) (smooth(T_THROW + 20, T_THROW + 30, t) * (1 - smooth(T_SKY + 20, T_SKY + 40, t))));
        g = Math.max(g, 0.85f * (float) (smooth(T_LEAP, T_LEAP + 10, t) * (1 - smooth(T_BLOOM + 40, T_END, t))));
        g = Math.max(g, (float) (smooth(T_IMPLODE, T_IMPLODE + 6, t) * (1 - smooth(T_BLOOM + 30, T_BLOOM + 50, t))));
        return MathHelper.clamp(g, 0f, 1f);
    }

    /** Where Red sits on the caster's raised fingertip (matches the nuke_red pose). */
    public static Vec3d handPos(NukeEntity n, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity owner = mc.world != null ? mc.world.getEntityById(n.getOwnerId()) : null;
        if (!(owner instanceof LivingEntity le)) return n.getOrigin().add(0, 2, 0);
        Vec3d exact = com.gojolimitless.client.pose.PoseManager.rightHand(le, td, 3.5f);
        if (exact != null) return exact;
        float by = MathHelper.lerp(td, le.prevBodyYaw, le.bodyYaw);
        Vec3d f = fwd(by), r = right(by);
        return le.getLerpedPos(td).add(0, 2.02, 0).add(f.multiply(0.30)).add(r.multiply(0.34));
    }

    // ------------------------------------------------------------------ sound
    private static void startSounds(NukeEntity e) {
        var sm = MinecraftClient.getInstance().getSoundManager();
        // Red charging at the fingertip
        sm.play(new TrackedLoopSound(ModSounds.RED_CHARGE, () -> e.redPos(e.age, handPos(e, 0f)),
                () -> e.age < T_RED_FORM ? 0.0 : 0.4 + 1.2 * smooth(T_RED_FORM, T_SIGN, e.age),
                () -> 0.85 + 0.25 * smooth(T_RED_FORM, T_THROW, e.age),
                () -> alive(e) && e.age < T_THROW + 2));
        // Red in the sky
        sm.play(new TrackedLoopSound(ModSounds.RED_FLY, () -> e.redPos(e.age, handPos(e, 0f)),
                () -> e.age < T_THROW ? 0.0 : 2.5 + 2.0 * smooth(T_CONVERGE, T_COLLIDE, e.age),
                () -> 0.8 + 0.5 * smooth(T_CONVERGE, T_COLLIDE, e.age),
                () -> alive(e) && e.age < T_COLLIDE));
        // Blue's hum, swelling with every word of its incantation
        sm.play(new TrackedLoopSound(ModSounds.NUKE_SKY_HUM, () -> e.bluePos(e.age),
                () -> e.age < T_SKY ? 0.0 : 3.0 + 1.5 * smooth(T_BLUE_1, T_BLUE_3 + 10, e.age) + 2.0 * smooth(T_CONVERGE, T_COLLIDE, e.age),
                () -> 0.75 + 0.1 * smooth(T_BLUE_1, T_BLUE_3, e.age) + 0.45 * smooth(T_CONVERGE, T_COLLIDE, e.age),
                () -> alive(e) && e.age < T_COLLIDE));
    }

    // ------------------------------------------------------------------ math
    public static double smooth(double e0, double e1, double x) {
        double t = MathHelper.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Vec3d bezier(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double k) {
        double u = 1 - k;
        return a.multiply(u * u * u).add(b.multiply(3 * u * u * k)).add(c.multiply(3 * u * k * k)).add(d.multiply(k * k * k));
    }

    public static void clear() { own = null; }

    /** How far the convergence point was raised above the usual height to clear hills (the alt-space shot tilts up). */
    static float altLift(NukeEntity n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 0f;
        Vec3d c = n.getPos();
        int top = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(c.x), MathHelper.floor(c.z));
        double usual = top + Math.max(ConfigManager.get().nuke.liftHeight, 6);
        return (float) MathHelper.clamp(c.y - usual, 0, 40);          // (the client only has the MOTION_BLOCKING heightmap)
    }
}
