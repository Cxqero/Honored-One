package com.gojolimitless.client.render;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.PurpleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Hollow Purple. Before the collision: Blue at the left hand, Red at the right, drawn toward each other.
 * After: a violet imaginary mass — a void-dark lens, turbulent plasma (Blender flipbook), the ghosts of the
 * red and blue infinities still spinning inside, a white-hot heart and lightning crawling over its surface.
 */
public class PurpleRenderer extends EntityRenderer<PurpleEntity> {
    private static final int PLASMA_FRAMES = 32, PLASMA_GRID = 6;

    public PurpleRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override public boolean shouldRender(PurpleEntity e, Frustum f, double x, double y, double z) { return true; }
    @Override public Identifier getTexture(PurpleEntity e) { return VfxLayers.tex("purple_core"); }

    @Override
    public void render(PurpleEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        vcp = DeferredVfx.route(vcp);
        if (RenderPath.inShadowPass()) return;
        float age = e.age + td, t = age / 20f;
        float pt = age - e.getPhaseStart();
        float G = RenderPath.glowScale();
        double ex = MathHelper.lerp(td, e.prevX, e.getX()), ey = MathHelper.lerp(td, e.prevY, e.getY()), ez = MathHelper.lerp(td, e.prevZ, e.getZ());
        Vec3d here = new Vec3d(ex, ey, ez);
        Vec3d cam = Vfx.cameraPos();
        MatrixStack.Entry en = ms.peek();
        Entity owner = e.getWorld().getEntityById(e.getOwnerId());
        float R = e.getRadius();

        switch (e.getPhase()) {
            case PurpleEntity.PHASE_CHARGING -> renderCharging(e, owner, ms, vcp, here, cam, t, G);
            case PurpleEntity.PHASE_FORMING -> {
                int collide = e.collideTick();
                if (pt < collide) {
                    renderConverging(e, owner, ms, vcp, here, cam, pt / collide, t, G, td);
                } else {
                    float k = MathHelper.clamp((pt - collide) / 8f, 0f, 1f);
                    float grow = easeOutBack(k);
                    renderMass(e, ms, vcp, cam.subtract(here), R * grow, t, G, 1f, age, false);
                }
            }
            case PurpleEntity.PHASE_FLYING -> {
                if (e.trailHead >= 0) trail(e, en, vcp, cam.subtract(here), ex, ey, ez, R, G);
                renderMass(e, ms, vcp, cam.subtract(here), R, t, G, 1f, age, true);
            }
            case PurpleEntity.PHASE_FADING -> {
                float k = MathHelper.clamp(pt / PurpleEntity.FADE_TICKS, 0f, 1f);
                renderMass(e, ms, vcp, cam.subtract(here), R * (1f + 0.4f * k), t, G, (1 - k) * (1 - k), age, false);
            }
            default -> {}
        }
    }

    /**
     * The imaginary mass itself: a deep violet body with a burning magenta-white rim, turbulent violet light moving
     * inside it, the two infinities still turning in its heart, a white-violet core and lightning crawling over it.
     * Kept dark inside so it reads as mass, not as a lamp.
     */
    private void renderMass(PurpleEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float R, float t, float G, float fade, float age, boolean flying) {
        if (R <= 0.01f) return;
        MatrixStack.Entry en = ms.peek();
        int q = ConfigManager.get().client.quality.ordinal();
        float near = Vfx.smooth(R * 0.8f, R * 2.8f, (float) camRel.length());
        float F = G * fade;
        VertexConsumer vc;

        // the body: deep violet, nearly opaque
        vc = vcp.getBuffer(VfxLayers.alpha("dark_disk"));
        Vfx.billboard(vc, en, 0, 0, 0, R * 2.05f, 0, 0.07f, 0.0f, 0.14f, 0.93f * near * fade);

        vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        float halo = 0.05f * F * near;
        Vfx.billboard(vc, en, 0, 0, 0, R * 3.2f, 0, 0.5f * halo, 0.08f * halo, 0.8f * halo, 1);
        // the rim: hot where the surface turns away from you
        Vfx.fresnelSphere(vc, en, camRel, R * 1.0f, q >= 2 ? 22 : 16, q >= 2 ? 32 : 22, F, 0.45f * F, F, 0.0f, 1.1f, 3.2f);
        Vfx.fresnelSphere(vc, en, camRel, R * 1.06f, q >= 2 ? 22 : 16, q >= 2 ? 32 : 22, 0.5f * F, 0.1f * F, 0.8f * F, 0.0f, 0.5f, 5.0f);

        // turbulent violet light inside (Blender plasma flipbook), tinted and held down
        vc = vcp.getBuffer(VfxLayers.additive("purple_plasma"));
        int frame = (int) (age * 0.9f) % PLASMA_FRAMES;
        float pf = 0.42f * F * (0.45f + 0.55f * near);
        Vfx.billboardFrame(vc, en, 0, 0, 0, R * 1.18f, t * 0.4f, 0.8f * pf, 0.3f * pf, pf, 1, frame, PLASMA_GRID, Vfx.FULL_BRIGHT);
        Vfx.billboardFrame(vc, en, 0, 0, 0, R * 0.95f, -t * 0.7f + 2f, 0.35f * pf, 0.15f * pf, 0.5f * pf, 1, (frame + 13) % PLASMA_FRAMES, PLASMA_GRID, Vfx.FULL_BRIGHT);

        // the two infinities still turning inside it
        float ghost = 0.32f * F * near;
        vc = vcp.getBuffer(VfxLayers.additive("blue_vortex_a"));
        Vfx.billboard(vc, en, 0, 0, 0, R * 1.0f, -t * 2.4f, 0.35f * ghost, 0.5f * ghost, ghost, 1);
        vc = vcp.getBuffer(VfxLayers.additive("red_swirl"));
        Vfx.billboard(vc, en, 0, 0, 0, R * 0.9f, t * 2.4f, ghost, 0.35f * ghost, 0.5f * ghost, 1);

        // lightning crawling over the surface
        int bolts = (flying ? 5 : 4) + q;
        long tick = (long) (age / 2f);
        for (int i = 0; i < bolts; i++) {
            long seed = tick * 31 + i * 977 + e.getId() * 7L;
            float h1 = Vfx.hash((int) seed), h2 = Vfx.hash((int) seed + 1), h3 = Vfx.hash((int) seed + 2);
            Vector3f a = new Vector3f(h1 - 0.5f, h2 - 0.5f, h3 - 0.5f).normalize().mul(R * 0.9f);
            Vector3f b = new Vector3f(h2 - 0.5f, h3 - 0.5f, h1 - 0.5f).normalize().mul(R * (1.3f + 0.8f * Vfx.hash((int) seed + 3)));
            float[] pts = Lightning.bolt(a.x, a.y, a.z, b.x, b.y, b.z, seed, 4, 0.35f);
            Lightning.draw(en, vcp, camRel, pts, 0.04f + R * 0.012f, 0.8f, 0.4f, 1.0f, F * (0.6f + 0.4f * Vfx.hash((int) seed + 5)));
        }

        // the heart: white-violet, small
        vc = vcp.getBuffer(VfxLayers.additive("purple_core"));
        float cf = 0.9f * F * (0.4f + 0.6f * near) * (1f + 0.06f * MathHelper.sin(t * 29f));
        Vfx.billboard(vc, en, 0, 0, 0, R * 0.55f, 0, cf, cf, cf, 1);
    }

    /** Before the collision: Blue at the left hand, Red at the right, drawn together. */
    private void renderConverging(PurpleEntity e, Entity owner, MatrixStack ms, VertexConsumerProvider vcp, Vec3d here, Vec3d cam,
                                  float k, float t, float G, float td) {
        if (owner == null) return;
        Vec3d op = owner.getLerpedPos(td);
        float yaw = owner.getYaw(td) * MathHelper.RADIANS_PER_DEGREE;
        Vec3d fwd = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw));
        Vec3d chest = op.add(0, 1.25, 0).add(fwd.multiply(0.45));
        float spread = e.isFull() ? 3.2f : 1.0f;
        Vec3d leftHand = chest.subtract(right.multiply(spread));
        Vec3d rightHand = chest.add(right.multiply(spread));
        if (e.isFull()) {                       // from the charge orbit, swinging in
            double a = t * 3.2;
            leftHand = op.add(0, 3.2, 0).add(new Vec3d(Math.cos(a), 0, Math.sin(a)).multiply(spread));
            rightHand = op.add(0, 3.2, 0).add(new Vec3d(-Math.cos(a), 0, -Math.sin(a)).multiply(spread));
        }
        float c = k * k * k;                    // accelerate into each other
        Vec3d blue = leftHand.lerp(here, c), red = rightHand.lerp(here, c);
        float size = (e.isFull() ? 1.3f : 0.45f) * (0.6f + 0.6f * MathHelper.clamp(k * 3, 0, 1));
        miniBlue(ms, vcp, blue.subtract(here), cam.subtract(blue), size, t, G);
        miniRed(ms, vcp, red.subtract(here), cam.subtract(red), size, t, G);
        // arc between them as they near
        if (k > 0.45f) {
            Vec3d b = blue.subtract(here), r = red.subtract(here);
            float[] pts = Lightning.bolt((float) b.x, (float) b.y, (float) b.z, (float) r.x, (float) r.y, (float) r.z, (long) (t * 10) + e.getId(), 4, 0.3f);
            Lightning.draw(ms.peek(), vcp, cam.subtract(here), pts, 0.05f, 0.75f, 0.35f, 1f, G * (k - 0.45f) * 1.8f);
        }
    }

    /** 200% charge: Blue and Red circle the caster, joined by arcs, the air around him turning violet. */
    private void renderCharging(PurpleEntity e, Entity owner, MatrixStack ms, VertexConsumerProvider vcp, Vec3d here, Vec3d cam, float t, float G) {
        if (owner == null) return;
        float pw = e.getPower();
        float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        Vec3d op = owner.getLerpedPos(td);
        double a = t * (2.0 + 2.5 * pw);
        double orbit = 2.2 + 1.0 * pw;
        Vec3d c = op.add(0, 2.6 + 0.6 * pw, 0);
        Vec3d blue = c.add(Math.cos(a) * orbit, 0.3 * Math.sin(a * 2), Math.sin(a) * orbit);
        Vec3d red = c.add(-Math.cos(a) * orbit, -0.3 * Math.sin(a * 2), -Math.sin(a) * orbit);
        float size = 0.4f + 0.6f * pw;
        miniBlue(ms, vcp, blue.subtract(here), cam.subtract(blue), size, t, G);
        miniRed(ms, vcp, red.subtract(here), cam.subtract(red), size, t, G);
        Vec3d b = blue.subtract(here), r = red.subtract(here);
        int arcs = 1 + e.getStage();
        for (int i = 0; i < arcs; i++) {
            float[] pts = Lightning.bolt((float) b.x, (float) b.y, (float) b.z, (float) r.x, (float) r.y, (float) r.z, (long) (t * 12) * 13 + i * 101, 5, 0.28f);
            Lightning.draw(ms.peek(), vcp, cam.subtract(here), pts, 0.04f + 0.04f * pw, 0.75f, 0.35f, 1f, G * (0.4f + 0.6f * pw));
        }
        // ground-level violet ring of pressure
        VertexConsumer vc = vcp.getBuffer(VfxLayers.additive("ring"));
        float rr = (float) (orbit * 1.6 + 1.5 * Math.sin(t * 3));
        Vec3d ground = op.subtract(here).add(0, 0.05, 0);
        float f = 0.25f * G * (0.4f + pw);
        Vfx.planeQuad(vc, ms.peek(), (float) ground.x, (float) ground.y, (float) ground.z, new Vector3f(0, 1, 0), rr, t, 0.6f * f, 0.2f * f, f, 1);
    }

    private void miniBlue(MatrixStack ms, VertexConsumerProvider vcp, Vec3d off, Vec3d camRel, float s, float t, float G) {
        MatrixStack.Entry en = ms.peek();
        float x = (float) off.x, y = (float) off.y, z = (float) off.z;
        Vfx.billboard(vcp.getBuffer(VfxLayers.alpha("blue_lens")), en, x, y, z, s * 1.9f, 0, 1, 1, 1, 0.85f);
        var glow = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        ms.push(); ms.translate(x, y, z);
        Vfx.fresnelSphere(glow, ms.peek(), camRel, s * 0.8f, 12, 18, 0.25f * G, 0.6f * G, G, 0f, 0.9f, 2.2f);
        ms.pop();
        Vfx.billboard(vcp.getBuffer(VfxLayers.additive("blue_vortex_a")), en, x, y, z, s * 1.35f, -t * 3f, 0.3f * G, 0.55f * G, 0.8f * G, 1);
        Vfx.billboard(vcp.getBuffer(VfxLayers.additive("blue_core")), en, x, y, z, s * 1.2f, 0, G, G, G, 1);
    }

    private void miniRed(MatrixStack ms, VertexConsumerProvider vcp, Vec3d off, Vec3d camRel, float s, float t, float G) {
        MatrixStack.Entry en = ms.peek();
        float x = (float) off.x, y = (float) off.y, z = (float) off.z;
        Vfx.billboard(vcp.getBuffer(VfxLayers.alpha("dark_disk")), en, x, y, z, s * 2.2f, 0, 0.25f, 0, 0, 0.5f);
        var glow = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        ms.push(); ms.translate(x, y, z);
        Vfx.fresnelSphere(glow, ms.peek(), camRel, s * 0.8f, 12, 18, G, 0.3f * G, 0.12f * G, 0f, 0.9f, 2.2f);
        ms.pop();
        Vfx.billboard(vcp.getBuffer(VfxLayers.additive("red_swirl")), en, x, y, z, s * 1.6f, t * 3f, 0.75f * G, 0.75f * G, 0.75f * G, 1);
        Vfx.billboard(vcp.getBuffer(VfxLayers.additive("red_core")), en, x, y, z, s * 1.3f, 0, G, G, G, 1);
    }

    private void trail(PurpleEntity e, MatrixStack.Entry en, VertexConsumerProvider vcp, Vec3d camRel,
                       double ex, double ey, double ez, float R, float G) {
        int len = e.trail.length;
        float[] pts = new float[len * 3], w = new float[len], col = new float[len * 4];
        int n = 1;
        w[0] = R * 1.6f;
        for (int i = 0; i < len - 1; i++) {
            Vec3d p = e.trail[(e.trailHead - i + len * 2) % len];
            if (p == null) break;
            float k = (i + 1) / (float) (len - 1);
            pts[n * 3] = (float) (p.x - ex); pts[n * 3 + 1] = (float) (p.y - ey); pts[n * 3 + 2] = (float) (p.z - ez);
            w[n] = R * 1.6f * (1f - 0.7f * k);
            float f = (1f - k) * (1f - k) * 0.5f * G;
            col[n * 4] = 0.55f * f; col[n * 4 + 1] = 0.15f * f; col[n * 4 + 2] = f; col[n * 4 + 3] = 1;
            n++;
        }
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, pts, n, w, col, 0.5f, 0f);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }
}
