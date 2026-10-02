package com.gojolimitless.client.pose;

import com.gojolimitless.ability.CastStage;
import com.gojolimitless.ability.MoveType;
import com.gojolimitless.client.anim.CastAnimation;
import com.gojolimitless.client.anim.CastAnimator;
import com.gojolimitless.entity.BlueOrbEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides what the caster's body does while casting — which Blender-authored animation plays, at what time, and any
 * procedural touch on top (the arm tracking Maximum Output: Blue around its orbit, the tremor of a chant). The
 * result is played by the playerAnimator layer {@link CastAnimation}, which adds bends, pivots, first-person arms and
 * the blocky fingers.
 */
public final class PoseManager {
    private PoseManager() {}

    /** Procedural adjustment applied after sampling (progress = age + tick delta). */
    public interface Adjust { void apply(CastAnimation a, float progress); }

    /** What to show this frame: an animation at a time (or none, with a plain weight), plus an adjuster. */
    public record Pick(PoseAnimation anim, float time, float weight, Adjust adjust) {}

    private record Cast(MoveType move, int stage, float startAge, int duration) {}

    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static final Map<Integer, Integer> lastSignStage = new HashMap<>();
    private static final Map<Integer, Float> signStart = new HashMap<>();

    public static void onCastState(int entityId, int move, int stage, int duration) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Entity e = mc.world.getEntityById(entityId);
        if (e == null) return;
        MoveType m = MoveType.byId(move);
        if (stage == CastStage.END || m == null) { CASTS.remove(entityId); return; }
        CASTS.put(entityId, new Cast(m, stage, e.age, duration));
    }

    public static void clear() { CASTS.clear(); lastSignStage.clear(); signStart.clear(); }

    private static int purpleStage(LivingEntity caster) {
        for (Entity e : caster.getWorld().getOtherEntities(caster, caster.getBoundingBox().expand(6),
                x -> x instanceof com.gojolimitless.entity.PurpleEntity)) {
            var p = (com.gojolimitless.entity.PurpleEntity) e;
            if (p.getOwnerId() == caster.getId() && p.getPhase() == com.gojolimitless.entity.PurpleEntity.PHASE_CHARGING) return p.getStage();
        }
        return 1;
    }

    // development: play a named animation on an entity (AutoTest), looping
    private static final Map<Integer, String> DEBUG_ANIM = new HashMap<>();
    private static final Map<Integer, Float> DEBUG_START = new HashMap<>();

    public static void debugPlay(Entity e, String name) {
        if (name == null) { DEBUG_ANIM.remove(e.getId()); return; }
        DEBUG_ANIM.put(e.getId(), name);
        DEBUG_START.put(e.getId(), (float) e.age);
    }

    /** Called by the caster's animation layer every rendered frame. */
    public static Pick pick(AbstractClientPlayerEntity e, float progress) {
        String dbg = DEBUG_ANIM.get(e.getId());
        if (dbg != null) {
            PoseAnimation a = PoseLibrary.get(dbg);
            if (a != null) return new Pick(a, Math.min(progress - DEBUG_START.get(e.getId()), a.lengthTicks()), 1f, null);
        }
        Cast c = CASTS.get(e.getId());
        if (c == null) return null;
        float t = progress - c.startAge;
        return switch (c.move) {
            case BLUE -> switch (c.stage) {
                case CastStage.TAP -> once(c, "blue_tap", t);
                case CastStage.RELEASE -> com.gojolimitless.config.ConfigManager.get().blue.maxReleaseMode
                        == com.gojolimitless.config.LimitlessConfig.ReleaseMode.RAISE_AND_DISPERSE
                        ? hold("blue_max", t, (a, p) -> raiseOrb(a, t)) : once(c, "blue_throw", t);
                case CastStage.CHARGING -> hold("blue_max", t, PoseManager::aimAtOrb);
                default -> null;
            };
            case RED -> switch (c.stage) {
                case CastStage.TAP -> once(c, "red_tap", t);
                case CastStage.CHARGING -> hold("red_charge", t, (a, p) -> {
                    // the strain of the chant: a fine tremor in the raised arm
                    a.addRot(CastAnimationParts.RIGHT_ARM, 0.012f * MathHelper.sin(p * 2.9f), 0f, 0.010f * MathHelper.sin(p * 3.7f));
                });
                case CastStage.RELEASE -> once(c, "red_release", t);
                default -> null;
            };
            case PURPLE -> switch (c.stage) {
                case CastStage.TAP -> once(c, "purple_tap", t);
                case CastStage.RELEASE -> once(c, "purple_200_release", t);
                case CastStage.CHARGING -> {
                    int stage = purpleStage(e);
                    if (stage != lastSignStage.getOrDefault(e.getId(), -1)) {
                        lastSignStage.put(e.getId(), stage);
                        signStart.put(e.getId(), progress);
                    }
                    float st = progress - signStart.getOrDefault(e.getId(), progress);
                    yield hold("purple_sign_" + Math.max(1, stage), st, (a, p) -> {
                        a.addRot(CastAnimationParts.RIGHT_ARM, 0.010f * MathHelper.sin(p * 3.1f), 0f, 0f);
                        a.addRot(CastAnimationParts.LEFT_ARM, 0.010f * MathHelper.sin(p * 2.7f + 1f), 0f, 0f);
                    });
                }
                default -> null;
            };
            case DOMAIN -> c.stage == CastStage.TAP ? once(c, "domain_instant", t) : once(c, "domain_seal", sealTime(t));
            case NUKE -> {
                var ref = com.gojolimitless.client.nuke.NukeClient.pose(e);
                if (ref == null) yield null;
                Adjust drift = null;
                if (ref.anim().equals("nuke_air") || ref.anim().equals("nuke_brace")) {
                    // hovering: a slow drift in the limbs so the pose never freezes
                    drift = (a, p) -> {
                        a.addRot(CastAnimationParts.RIGHT_LEG, 0.04f * MathHelper.sin(p * 0.09f), 0f, 0f);
                        a.addRot(CastAnimationParts.LEFT_LEG, 0.04f * MathHelper.sin(p * 0.09f + 1.3f), 0f, 0f);
                        a.addRot(CastAnimationParts.LEFT_ARM, 0f, 0f, 0.03f * MathHelper.sin(p * 0.07f));
                    };
                }
                PoseAnimation a = PoseLibrary.get(ref.anim());
                yield a == null ? null : new Pick(a, ref.hold() ? Math.min(ref.time(), a.lengthTicks()) : ref.time(), 1f, drift);
            }
            default -> null;
        };
    }

    /** Plays once, then the cast is over for the body. */
    private static Pick once(Cast c, String name, float t) {
        PoseAnimation a = PoseLibrary.get(name);
        if (a == null) return null;
        if (t > a.lengthTicks()) { CASTS.values().remove(c); return null; }
        return new Pick(a, t, 1f, null);
    }

    /** Plays and holds the last frame for as long as the stage lasts, breathing so the hold never goes dead. */
    private static Pick hold(String name, float t, Adjust adjust) {
        PoseAnimation a = PoseLibrary.get(name);
        if (a == null) return adjust == null ? null : new Pick(null, t, Math.min(1f, t / 5f), adjust);
        float len = a.lengthTicks();
        Adjust breathe = (anim, p) -> {
            float k = MathHelper.clamp((t - len) / 10f, 0f, 1f);
            if (k <= 0f) return;
            float b = MathHelper.sin(p * 0.11f);
            anim.addRot(CastAnimationParts.TORSO, -0.012f * b * k, 0f, 0f);
            anim.addRot(CastAnimationParts.HEAD, 0.018f * b * k, 0f, 0f);
            anim.addRot(CastAnimationParts.RIGHT_ARM, 0.010f * MathHelper.sin(p * 0.11f + 0.6f) * k, 0f, 0f);
            anim.addRot(CastAnimationParts.LEFT_ARM, 0.008f * MathHelper.sin(p * 0.11f + 0.9f) * k, 0f, 0f);
        };
        Adjust both = adjust == null ? breathe : (anim, p) -> { breathe.apply(anim, p); adjust.apply(anim, p); };
        return new Pick(a, Math.min(t, len), 1f, both);
    }

    /** Maximum Output: Blue — the casting arm tracks the orb as it circles. */
    /**
     * Maximum Output: Blue circles the caster and his raised right hand conducts it from overhead, the arm tilted toward
     * where it is on its orbit, so the hand traces a circle above his head and never passes through his body; the
     * torso turns and leans after it. Walking hands the legs back to the normal walk.
     */
    private static void aimAtOrb(CastAnimation anim, float progress) {
        LivingEntity entity = anim.player();
        BlueOrbEntity orb = ownOrb(entity, BlueOrbEntity.MODE_ORBIT);
        if (orb == null) return;
        float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        Vec3d d = orb.getLerpedPos(td).subtract(entity.getLerpedPos(td).add(0, 1.4, 0));
        float bodyYaw = MathHelper.lerp(td, entity.prevBodyYaw, entity.bodyYaw);
        Vec3d h = new Vec3d(d.x, 0, d.z);
        if (h.lengthSquared() < 1e-4) h = new Vec3d(0, 0, 1);
        h = h.normalize();
        aimArm(anim, CastAnimationParts.RIGHT_ARM, h.multiply(0.58).add(0, 0.81, 0), bodyYaw);
        // where it is around him: 0 ahead, + to his left
        Vector3f m = new Vector3f((float) h.x, 0, (float) h.z);
        m.rotateY((bodyYaw - 180f) * MathHelper.RADIANS_PER_DEGREE);
        float rel = (float) Math.atan2(m.x, -m.z);
        anim.setRot(CastAnimationParts.TORSO, 0.07f + 0.05f * MathHelper.cos(rel), 0.34f * MathHelper.sin(rel), -0.06f * MathHelper.sin(rel), 1f);
        anim.setRot(CastAnimationParts.LEFT_ARM, -0.35f, 0.2f, -0.35f - 0.1f * MathHelper.sin(rel), 1f);
        freeLegsWhenWalking(anim, entity);
    }

    /** Let go: the hand lifts Blue up over his head, holds it there, then opens as it disperses. */
    private static void raiseOrb(CastAnimation anim, float t) {
        LivingEntity entity = anim.player();
        float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        float bodyYaw = MathHelper.lerp(td, entity.prevBodyYaw, entity.bodyYaw);
        float lift = MathHelper.clamp(t / BlueOrbEntity.RAISE_TICKS, 0f, 1f);
        lift = lift * lift * (3 - 2 * lift);
        float open = MathHelper.clamp((t - BlueOrbEntity.RAISE_TICKS - BlueOrbEntity.HOLD_TICKS) / 6f, 0f, 1f);
        float settle = MathHelper.clamp((t - BlueOrbEntity.RAISE_TICKS - BlueOrbEntity.HOLD_TICKS - 14) / 12f, 0f, 1f);
        // straight up, a touch forward; as it disperses both hands open out to the sides
        float yr = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        aimArm(anim, CastAnimationParts.RIGHT_ARM, new Vec3d(-MathHelper.sin(yr) * 0.15, 1, MathHelper.cos(yr) * 0.15), bodyYaw);
        anim.addRot(CastAnimationParts.RIGHT_ARM, 0f, 0f, 0.55f * open);
        anim.setRot(CastAnimationParts.LEFT_ARM, -0.3f - 1.6f * open, 0f, 0.5f * open - 0.25f, 1f);
        anim.setRot(CastAnimationParts.TORSO, -0.08f * lift, 0f, 0f, 1f);
        anim.setWeight(1f - settle);
        freeLegsWhenWalking(anim, entity);
    }

    /** Points an arm along a world direction (from the shoulder). */
    private static void aimArm(CastAnimation anim, int part, Vec3d dir, float bodyYaw) {
        // world → model space: model = Scale(-1,-1,1) · RotY(bodyYaw - 180°) · world
        Vector3f v = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        v.rotateY((bodyYaw - 180f) * MathHelper.RADIANS_PER_DEGREE);
        v.set(-v.x, -v.y, v.z);
        float p = -(float) Math.acos(MathHelper.clamp(v.y, -1f, 1f));
        float y = (float) Math.atan2(-v.x, -v.z);
        anim.setRot(part, p, y, 0f, 1f);
    }

    private static void freeLegsWhenWalking(CastAnimation anim, LivingEntity e) {
        if (e.getVelocity().horizontalLengthSquared() > 0.0009 || e.limbAnimator.getSpeed() > 0.08f) {
            anim.free(CastAnimationParts.RIGHT_LEG);
            anim.free(CastAnimationParts.LEFT_LEG);
            anim.free(CastAnimationParts.ROOT);
        }
    }

    private static BlueOrbEntity ownOrb(LivingEntity entity, int mode) {
        BlueOrbEntity orb = null;
        double best = Double.MAX_VALUE;
        for (Entity e : entity.getWorld().getOtherEntities(entity, entity.getBoundingBox().expand(64), x -> x instanceof BlueOrbEntity)) {
            BlueOrbEntity b = (BlueOrbEntity) e;
            if (b.getOwnerId() != entity.getId() || b.getMode() != mode) continue;
            double d = b.squaredDistanceTo(entity);
            if (d < best) { best = d; orb = b; }
        }
        return orb;
    }

    /**
     * World position of the right fingertips as last rendered (extraPx further along the hand), or null when the
     * caster isn't being animated.
     */
    public static Vec3d rightHand(LivingEntity e, float td, float extraPx) {
        CastAnimation a = CastAnimator.of(e);
        if (a == null || !a.applied() || !(e instanceof AbstractClientPlayerEntity p)) return null;
        boolean slim = p.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM;
        float armW = slim ? 3f : 4f;
        float cx = -1f + (4f - armW) / 2f;
        return a.handPoint(true, td, new Vector3f(cx, 10f + extraPx, -1f), armW);
    }

    /** Part indices for adjusters. */
    public static final class CastAnimationParts {
        public static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, ROOT = 6;
    }

    /** domain_seal was animated for the void opening at tick 106; the manifesting now runs to D_OPEN, so its hold stretches. */
    private static float sealTime(float t) {
        final float held = 44f, opened = 106f, open = com.gojolimitless.entity.DomainEntity.D_OPEN;
        if (t <= held) return t;
        if (t <= open) return held + (t - held) * (opened - held) / (open - held);
        return t - (open - opened);
    }
}
