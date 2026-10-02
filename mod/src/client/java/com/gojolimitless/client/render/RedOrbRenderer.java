package com.gojolimitless.client.render;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.RedOrbEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Reversal: Red. A white-hot point wrapped in outward-bursting crimson filaments. While the incantation is chanted,
 * thin laser rays lance out across the scene, more with every word. In flight it drags a burning trail.
 */
public class RedOrbRenderer extends EntityRenderer<RedOrbEntity> {
    public RedOrbRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override public boolean shouldRender(RedOrbEntity e, Frustum f, double x, double y, double z) { return true; }
    @Override public Identifier getTexture(RedOrbEntity e) { return VfxLayers.tex("red_core"); }

    @Override
    public void render(RedOrbEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        vcp = DeferredVfx.route(vcp);
        if (RenderPath.inShadowPass()) return;
        float age = e.age + td, t = age / 20f;
        float pw = e.isCharged() ? e.getPower() : 0f;
        int stage = e.getStage();
        boolean flying = e.getMode() == RedOrbEntity.MODE_FLYING;
        float intro = MathHelper.clamp(age / 4f, 0f, 1f);
        float s = e.coreRadius() * intro;
        // at the caster's own fingertip in first person the orb is ~1 block from the eye: draw it smaller so it
        // reads the same size it would from a third-person camera, and don't let it swallow the view
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        boolean ownFirstPerson = mc.player != null && e.getOwnerId() == mc.player.getId()
                && mc.options.getPerspective().isFirstPerson() && e.getMode() == RedOrbEntity.MODE_CHARGING;
        if (ownFirstPerson) s *= 0.45f;
        float G = RenderPath.glowScale();
        int q = ConfigManager.get().client.quality.ordinal();
        double ex = MathHelper.lerp(td, e.prevX, e.getX()), ey = MathHelper.lerp(td, e.prevY, e.getY()), ez = MathHelper.lerp(td, e.prevZ, e.getZ());
        Vec3d camRel = Vfx.cameraPos().subtract(ex, ey, ez);
        MatrixStack.Entry en = ms.peek();
        float near = Vfx.smooth(s * 0.8f, s * 3.0f, (float) camRel.length());
        float flick = 1f + 0.08f * MathHelper.sin(t * 43f) + 0.05f * MathHelper.sin(t * 27f);
        VertexConsumer vc;

        // contrast disk + atmospheric glow
        vc = vcp.getBuffer(VfxLayers.alpha("dark_disk"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 3.4f, 0, 0.25f, 0.0f, 0.0f, 0.55f * near * intro);
        vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        float halo = (0.07f + 0.1f * pw) * G * near;
        Vfx.billboard(vc, en, 0, 0, 0, s * (7f + 5f * pw), 0, halo, 0.12f * halo, 0.06f * halo, 1);
        Vfx.fresnelSphere(vc, en, camRel, s * 0.95f, 14, 22, 1.0f * G, 0.3f * G, 0.12f * G, 0f, 0.8f, 2.2f);

        // trail while flying
        if (flying && e.trailHead >= 0) trail(e, en, vcp, camRel, ex, ey, ez, s, G, pw);

        // laser rays: the incantation visibly charging the space around the fingertip
        if (!flying && pw > 0f) {
            vc = vcp.getBuffer(VfxLayers.additive("ray"));
            int rays = 3 + stage * 3 + (q >= 2 ? 2 : 0);
            for (int i = 0; i < rays; i++) {
                float h1 = Vfx.hash(i * 5 + 11), h2 = Vfx.hash(i * 5 + 12), h3 = Vfx.hash(i * 5 + 13);
                Vector3f d = new Vector3f(h1 - 0.5f, (h2 - 0.5f) * 0.9f, h3 - 0.5f).normalize();
                d.rotateY(t * (0.15f + 0.1f * h1) + i * 0.9f);
                float appear = MathHelper.clamp((pw * 3f - i / (float) rays * 2.2f) * 2f, 0f, 1f);
                if (appear <= 0) continue;
                float len = (5f + 75f * (float) Math.pow(pw, 1.4)) * (0.55f + 0.45f * h2) * appear;
                float pulse = 0.65f + 0.35f * MathHelper.sin(t * (8f + 5f * h3) + i);
                float f = pulse * G * (0.6f + 0.6f * pw) * appear;
                Vfx.streak(vc, en, camRel, 0, 0, 0, d.x * len, d.y * len, d.z * len, 0.03f + 0.09f * pw,
                        f, 0.16f * f, 0.08f * f, 1);
            }
        }

        // outward-bursting swirl layers
        float sw = (0.55f + 0.45f * pw) * G * near;
        vc = vcp.getBuffer(VfxLayers.additive("red_swirl_b"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 3.3f, t * 1.3f, 0.6f * sw, 0.6f * sw, 0.6f * sw, 1);
        vc = vcp.getBuffer(VfxLayers.additive("red_swirl"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.5f * flick, t * 2.8f, 0.95f * sw, 0.95f * sw, 0.95f * sw, 1);

        // sparks thrown outward
        vc = vcp.getBuffer(VfxLayers.additive("spark"));
        int sparks = 10 + (int) (24 * pw) + q * 3;
        for (int i = 0; i < sparks; i++) {
            float h1 = Vfx.hash(i * 7 + 301), h2 = Vfx.hash(i * 7 + 302), h3 = Vfx.hash(i * 7 + 303);
            float ph = frac(t * (1.2f + h1) + h2);
            Vector3f d = new Vector3f(h1 - 0.5f, h2 - 0.5f, h3 - 0.5f).normalize();
            float r = s * (0.6f + (2.5f + 3f * pw) * ph);
            float f = (1f - ph) * G;
            Vfx.billboard(vc, en, d.x * r, d.y * r, d.z * r, 0.05f + 0.05f * s, 0, f, 0.35f * f, 0.2f * f, 1);
        }

        // white-hot core
        vc = vcp.getBuffer(VfxLayers.additive("red_core"));
        float cg = G * (0.5f + 0.5f * near) * flick;
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.1f, 0, cg, cg, cg, 1);
    }

    private void trail(RedOrbEntity e, MatrixStack.Entry en, VertexConsumerProvider vcp, Vec3d camRel,
                       double ex, double ey, double ez, float s, float G, float pw) {
        int len = e.trail.length;
        float[] pts = new float[len * 3], w = new float[len], col = new float[len * 4];
        int n = 1;
        w[0] = s * 0.9f;
        for (int i = 0; i < len - 1; i++) {
            Vec3d p = e.trail[(e.trailHead - i + len * 2) % len];
            if (p == null) break;
            float k = (i + 1) / (float) (len - 1);
            pts[n * 3] = (float) (p.x - ex); pts[n * 3 + 1] = (float) (p.y - ey); pts[n * 3 + 2] = (float) (p.z - ez);
            w[n] = s * (0.9f + 0.6f * pw) * (1f - k);
            float f = (1f - k) * (1f - k) * 0.9f * G;
            col[n * 4] = f; col[n * 4 + 1] = 0.18f * f; col[n * 4 + 2] = 0.08f * f; col[n * 4 + 3] = 1;
            n++;
        }
        col[0] = col[4] * 0.5f; col[1] = col[5] * 0.5f; col[2] = col[6] * 0.5f; col[3] = 1;
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("ribbon_energy")), en, camRel, pts, n, w, col, 0f, 0.4f);
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, pts, n, scale(w, 2.2f), scale(col, 0.35f), 0.5f, 0f);
    }

    private static float[] scale(float[] a, float k) {
        float[] o = a.clone();
        for (int i = 0; i < o.length; i++) o[i] *= k;
        return o;
    }

    private static float frac(float f) { return f - (float) Math.floor(f); }
}
