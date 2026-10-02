package com.gojolimitless.client.anim;

import com.gojolimitless.client.pose.PoseAnimation;
import com.gojolimitless.client.pose.PoseManager;
import com.gojolimitless.config.ConfigManager;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The casting-animation layer on a player (playerAnimator). Each frame it asks {@link PoseManager} what to show
 * (which baked Blender animation and at what time, plus procedural aiming), samples it, and hands playerAnimator
 * rotations, pivot offsets and bends for every part, blended with the vanilla pose by the animation's weight.
 * It remembers the arm transforms it applied so effects and fingers can sit exactly at the hand.
 */
public final class CastAnimation implements IAnimation {
    static final String[] PA_NAMES = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "body"};
    static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root"};
    static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3, ROOT = 6;

    private final AbstractClientPlayerEntity player;
    private PoseManager.Pick pick;
    private float weight;

    // sampled frame
    private final float[][] rot = new float[7][3], pos = new float[7][3], bend = new float[7][2];
    private final boolean[] hasRot = new boolean[7], hasPos = new boolean[7], hasBend = new boolean[7];
    private PoseAnimation.HandSample rightHand = PoseAnimation.HandSample.NONE, leftHand = PoseAnimation.HandSample.NONE;

    // what was actually applied last frame (after blending with vanilla) — arms and root
    final float[][] appliedRot = new float[7][3], appliedPos = new float[7][3], appliedBend = new float[7][2];
    boolean appliedValid;

    public CastAnimation(AbstractClientPlayerEntity player) { this.player = player; }

    public AbstractClientPlayerEntity player() { return player; }

    @Override
    public boolean isActive() {
        ensure();
        return pick != null && weight > 0.001f && ConfigManager.get().client.poses;
    }

    private float lastProgress = -1f;

    /** In first person nobody renders the player model, so nothing calls setupAnim: sample on demand. */
    private void ensure() {
        float td = net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        if (Math.abs(player.age + td - lastProgress) > 1e-4f) setupAnim(td);
    }

    @Override
    public void setupAnim(float tickDelta) {
        float progress = player.age + tickDelta;
        lastProgress = progress;
        pick = ConfigManager.get().client.poses ? PoseManager.pick(player, progress) : null;
        for (int i = 0; i < 7; i++) { hasRot[i] = hasPos[i] = hasBend[i] = false; }
        rightHand = leftHand = PoseAnimation.HandSample.NONE;
        weight = 0f;
        if (pick == null) { appliedValid = false; return; }
        PoseAnimation a = pick.anim();
        float t = pick.time();
        if (a != null) {
            weight = a.weightAt(t);
            float headYaw = MathHelper.wrapDegrees(MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw)
                    - MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw)) * MathHelper.RADIANS_PER_DEGREE;
            float headPitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch()) * MathHelper.RADIANS_PER_DEGREE;
            boolean fp = dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode.isFirstPersonPass();
            for (int i = 0; i < 7; i++) {
                // first-person override tracks ("right_arm@fp"): signs held a little further from the camera
                String p = fp && (a.rot.containsKey(PARTS[i] + "@fp") || a.pos.containsKey(PARTS[i] + "@fp")
                        || a.bend.containsKey(PARTS[i] + "@fp")) ? PARTS[i] + "@fp" : PARTS[i];
                if (a.sample(p, t, rot[i])) {
                    hasRot[i] = true;
                    if (a.aim.contains(PARTS[i])) { rot[i][0] += headPitch; rot[i][1] += headYaw; }
                }
                hasPos[i] = a.samplePos(p, t, pos[i]);
                hasBend[i] = a.sampleBend(p, t, bend[i]);
            }
            rightHand = a.sampleHand(true, t);
            leftHand = a.sampleHand(false, t);
        } else {
            weight = pick.weight();
        }
        if (pick.adjust() != null) pick.adjust().apply(this, progress);
    }

    // ---------------------------------------------------------------- procedural access (PoseManager adjusters)
    public void addRot(int part, float p, float y, float r) {
        if (!hasRot[part]) { rot[part][0] = rot[part][1] = rot[part][2] = 0f; hasRot[part] = true; }
        rot[part][0] += p; rot[part][1] += y; rot[part][2] += r;
    }

    public void setRot(int part, float p, float y, float r, float k) {
        if (!hasRot[part]) { rot[part][0] = p; rot[part][1] = y; rot[part][2] = r; hasRot[part] = true; return; }
        rot[part][0] = MathHelper.lerp(k, rot[part][0], p);
        rot[part][1] = MathHelper.lerp(k, rot[part][1], y);
        rot[part][2] = MathHelper.lerp(k, rot[part][2], r);
    }

    /** Hands a part back to vanilla animation this frame (e.g. the legs walk while the arms cast). */
    public void free(int part) { hasRot[part] = hasPos[part] = hasBend[part] = false; }

    public void setBend(int part, float axis, float angle) { bend[part][0] = axis; bend[part][1] = angle; hasBend[part] = true; }

    public void setWeight(float w) { weight = w; }

    public void setHand(boolean right, String shape, float w) {
        var s = new PoseAnimation.HandSample(shape, shape, 0f, w);
        if (right) rightHand = s; else leftHand = s;
    }

    // ---------------------------------------------------------------- playerAnimator
    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        int i = index(modelName);
        if (i < 0 || weight <= 0f) return value0;
        float w = weight;
        switch (type) {
            case ROTATION -> {
                if (!hasRot[i]) { record(i, type, value0); return value0; }
                Vec3f v = i == HEAD
                        // the head keeps following where the player looks; animations only tilt it
                        ? new Vec3f(value0.getX() + rot[i][0] * w, value0.getY() + rot[i][1] * w, value0.getZ() + rot[i][2] * w)
                        : new Vec3f(MathHelper.lerp(w, value0.getX(), rot[i][0]), MathHelper.lerp(w, value0.getY(), rot[i][1]),
                        MathHelper.lerp(w, value0.getZ(), rot[i][2]));
                record(i, type, v);
                return v;
            }
            case POSITION -> {
                if (!hasPos[i]) { record(i, type, value0); return value0; }
                Vec3f v;
                if (i == ROOT) {
                    // pixels (x = the character's left, y up, z forward) → the renderer's rotated entity space, blocks
                    v = new Vec3f(-pos[i][0] / 16f * w, pos[i][1] / 16f * w, -pos[i][2] / 16f * w);
                } else {
                    v = new Vec3f(value0.getX() + pos[i][0] * w, value0.getY() + pos[i][1] * w, value0.getZ() + pos[i][2] * w);
                }
                record(i, type, v);
                return v;
            }
            case BEND -> {
                if (!hasBend[i]) { record(i, type, value0); return value0; }
                Vec3f v = new Vec3f(bend[i][0], bend[i][1] * w, 0f);
                record(i, type, v);
                return v;
            }
            default -> { return value0; }
        }
    }

    private void record(int i, TransformType type, Vec3f v) {
        switch (type) {
            case ROTATION -> { appliedRot[i][0] = v.getX(); appliedRot[i][1] = v.getY(); appliedRot[i][2] = v.getZ(); }
            case POSITION -> { appliedPos[i][0] = v.getX(); appliedPos[i][1] = v.getY(); appliedPos[i][2] = v.getZ(); }
            case BEND -> { appliedBend[i][0] = v.getX(); appliedBend[i][1] = v.getY(); }
            default -> {}
        }
        appliedValid = true;
    }

    private static int index(String n) {
        for (int i = 0; i < PA_NAMES.length; i++) if (PA_NAMES[i].equals(n)) return i;
        return -1;
    }

    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        ensure();
        if (!isActive() || pick == null || pick.anim() == null) return FirstPersonMode.NONE;
        if (!ConfigManager.get().client.firstPersonArms) return FirstPersonMode.NONE;
        PoseAnimation a = pick.anim();
        return (a.firstPersonRight || a.firstPersonLeft) ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        PoseAnimation a = pick != null ? pick.anim() : null;
        boolean r = a == null || a.firstPersonRight, l = a != null && a.firstPersonLeft;
        return new FirstPersonConfiguration(r, l, false, false);
    }

    // ---------------------------------------------------------------- hands
    public PoseAnimation.HandSample hand(boolean right) {
        if (!isActive()) return PoseAnimation.HandSample.NONE;
        PoseAnimation.HandSample h = right ? rightHand : leftHand;
        return new PoseAnimation.HandSample(h.a(), h.b(), h.k(), h.w() * Math.min(1f, weight * 1.5f));
    }

    public float[] armRot(boolean right) { return appliedRot[right ? RIGHT_ARM : LEFT_ARM]; }

    public float[] armPos(boolean right) { return appliedPos[right ? RIGHT_ARM : LEFT_ARM]; }

    public float[] armBend(boolean right) { return appliedBend[right ? RIGHT_ARM : LEFT_ARM]; }

    public boolean applied() { return appliedValid && isActive(); }

    /**
     * World position of a point on the hand (arm-local model pixels, e.g. the fingertips), as last rendered:
     * arm pivot + rotation + bend, then the living-entity renderer (scale -1,-1,1; lift 1.501; yaw 180° − body yaw)
     * and our whole-body transform.
     */
    public Vec3d handPoint(boolean right, float td, Vector3f armLocalPx, float armW) {
        float[] r = armRot(right), p = armPos(right), b = armBend(right);
        Matrix4f m = new Matrix4f();
        // whole-body transform (applied in the rotated entity space, pivot 0.7 up)
        float by = MathHelper.lerp(td, player.prevBodyYaw, player.bodyYaw);
        m.rotateY((180f - by) * MathHelper.RADIANS_PER_DEGREE);
        if (hasPos[ROOT] || hasRot[ROOT]) {
            float[] rp = appliedPos[ROOT], rr = appliedRot[ROOT];
            m.translate(rp[0], rp[1] + 0.7f, rp[2]);
            m.rotateZ(rr[2]).rotateY(rr[1]).rotateX(rr[0]);
            m.translate(0, -0.7f, 0);
        }
        m.scale(-1f, -1f, 1f);
        m.scale(0.9375f);
        m.translate(0, -1.501f, 0);
        // arm part: translate(pivot/16), rotate ZYX
        m.translate(p[0] / 16f, p[1] / 16f, p[2] / 16f);
        m.rotateZ(r[2]).rotateY(r[1]).rotateX(r[0]);
        m.scale(1f / 16f);
        if (Math.abs(b[1]) > 1e-4f) {
            // bendy-lib: rotate the hand half about (cos a, 0, sin a) through the middle of the arm
            float cx = right ? -1f + (4f - armW) / 2f : 1f - (4f - armW) / 2f;
            Vector3f fix = new Vector3f(cx, 4f, 0f);
            m.translate(fix).rotate(b[1], new Vector3f((float) Math.cos(b[0]), 0f, (float) Math.sin(b[0])).normalize()).translate(-fix.x, -fix.y, -fix.z);
        }
        Vector4f v = new Vector4f(armLocalPx.x, armLocalPx.y, armLocalPx.z, 1f).mul(m);
        return player.getLerpedPos(td).add(v.x, v.y, v.z);
    }
}
