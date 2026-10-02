package com.gojolimitless.client.render;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.entity.BlueOrbEntity;
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
 * Lapse: Blue. Layers, back to front:
 * dark gravity lens → atmospheric halo + fresnel field shell → two spiral vortex discs → 3D energy ribbons
 * spiralling inward → inflowing attraction streaks and sparks → white-hot core. Plus a motion trail for Maximum Output.
 */
public class BlueOrbRenderer extends EntityRenderer<BlueOrbEntity> {
    private static final Identifier NONE = VfxLayers.tex("blue_core");

    public BlueOrbRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
        this.shadowOpacity = 0;
    }

    @Override
    public boolean shouldRender(BlueOrbEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getVisibilityBoundingBox());
    }

    @Override
    public Identifier getTexture(BlueOrbEntity entity) { return NONE; }

    @Override
    public void render(BlueOrbEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        vcp = DeferredVfx.route(vcp);
        if (RenderPath.inShadowPass()) return;
        LimitlessConfig.Client cc = ConfigManager.get().client;
        float age = e.age + td;
        float t = age / 20f;
        float R = e.renderRadius < 0 ? e.getRadius() : MathHelper.lerp(td, e.prevRenderRadius, e.renderRadius);

        float intro = easeOutBack(MathHelper.clamp(age / 9f, 0f, 1f));
        float scale = intro, bright = 1f;
        if (e.getMode() == BlueOrbEntity.MODE_COLLAPSING) {
            float k = MathHelper.clamp((age - e.getCollapseStart()) / BlueOrbEntity.COLLAPSE_TICKS, 0f, 1f);
            // swell, then everything is dragged into the point
            scale *= k < 0.3f ? 1f + 0.3f * (k / 0.3f) : 1.3f * (1f - Vfx.smooth(0.3f, 1f, k));
            bright = 1f + 2.2f * k;
        }
        float s = Math.max(0.001f, R * scale);
        float G = RenderPath.glowScale() * bright;
        boolean big = e.getMode() != BlueOrbEntity.MODE_TAP;
        int q = cc.quality.ordinal();                      // 0..3

        double ex = MathHelper.lerp(td, e.prevX, e.getX()), ey = MathHelper.lerp(td, e.prevY, e.getY()), ez = MathHelper.lerp(td, e.prevZ, e.getZ());
        Vec3d cam = Vfx.cameraPos();
        Vec3d camRel = cam.subtract(ex, ey, ez);
        MatrixStack.Entry en = ms.peek();
        VertexConsumer vc;

        // fade the big flat layers when the camera is inside / right next to the orb, so it never whites out the screen
        float camDist = (float) camRel.length();
        float near = Vfx.smooth(s * 0.7f, s * 2.6f, camDist);

        // 1. gravity lens: darkens what's behind so the energy reads even against a bright sky
        vc = vcp.getBuffer(VfxLayers.alpha("blue_lens"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.35f, 0, 1, 1, 1, 0.92f * intro * near);

        // 2. halo + field shell
        vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        float halo = 0.055f * G * near;
        Vfx.billboard(vc, en, 0, 0, 0, s * 4.2f, 0, 0.12f * halo, 0.35f * halo, 1.0f * halo, 1);
        int lat = q >= 2 ? 18 : 12, lon = q >= 2 ? 28 : 18;
        Vfx.fresnelSphere(vc, en, camRel, s * 0.92f, lat, lon, 0.25f * G, 0.55f * G, 1.0f * G, 0.0f, 0.75f, 2.2f);
        if (big) Vfx.fresnelSphere(vc, en, camRel, s * 1.55f, lat, lon, 0.08f * G, 0.2f * G, 0.55f * G, 0.0f, 0.4f, 3.5f);

        // 3. motion trail (Maximum Output only)
        if (big && e.trailHead >= 0) renderTrail(e, en, vcp, camRel, ex, ey, ez, s, G);

        // 4. vortex discs
        float vb = 0.34f * G * near, va = 0.62f * G * near, vi = 0.42f * G * near;
        vc = vcp.getBuffer(VfxLayers.additive("blue_vortex_b"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.35f, -t * 0.9f, 0.25f * vb, 0.5f * vb, 1.0f * vb, 1);
        vc = vcp.getBuffer(VfxLayers.additive("blue_vortex_a"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 1.8f, -t * 2.1f + 0.3f * MathHelper.sin(t * 1.3f), 0.45f * va, 0.75f * va, 1.0f * va, 1);
        Vfx.billboard(vc, en, 0, 0, 0, s * 1.25f, -t * 3.7f + 1.7f, 0.5f * vi, 0.8f * vi, 1.0f * vi, 1);

        // 5. energy ribbons spiralling into the singularity
        vc = vcp.getBuffer(VfxLayers.additive("ribbon_energy"));
        int ribbons = big ? (q >= 2 ? 6 : 4) : (q >= 2 ? 4 : 3);
        int n = q >= 2 ? 44 : 28;
        float[] pts = new float[n * 3], w = new float[n], col = new float[n * 4];
        for (int i = 0; i < ribbons; i++) {
            float h1 = Vfx.hash(i * 7 + 1), h2 = Vfx.hash(i * 7 + 2), h3 = Vfx.hash(i * 7 + 3);
            Vector3f N = new Vector3f(h1 - 0.5f, 0.8f + h2, h3 - 0.5f).normalize();
            N.rotateY(t * (0.3f + 0.2f * h2) + i);
            Vector3f U = Math.abs(N.y) < 0.99f ? new Vector3f(0, 1, 0).cross(N).normalize() : new Vector3f(1, 0, 0);
            Vector3f V = new Vector3f(N).cross(U).normalize();
            float a0 = h1 * 6.283f;
            float spin = t * (1.7f + 0.5f * h3) * (big ? 1.3f : 1f);
            float outer = big ? 2.4f : 2.9f;
            for (int k = 0; k < n; k++) {
                float p = k / (float) (n - 1);
                float rad = s * (outer - (outer - 0.75f) * p);
                float ang = a0 + p * 1.7f * (float) Math.PI - spin;
                float cx = MathHelper.cos(ang), sx = MathHelper.sin(ang);
                pts[k * 3] = (U.x * cx + V.x * sx) * rad;
                pts[k * 3 + 1] = (U.y * cx + V.y * sx) * rad;
                pts[k * 3 + 2] = (U.z * cx + V.z * sx) * rad;
                w[k] = s * 0.13f * (1f - 0.5f * p);
                float f = (float) Math.pow(MathHelper.sin(p * (float) Math.PI), 0.8) * G * 0.7f * (0.35f + 0.65f * near);
                col[k * 4] = 0.35f * f; col[k * 4 + 1] = 0.8f * f; col[k * 4 + 2] = 1.0f * f; col[k * 4 + 3] = 1;
            }
            Vfx.ribbon(vc, en, camRel, pts, n, w, col, -t * 1.5f + i * 0.37f, 3.0f / n);
        }

        // 6. attraction streaks + sparks
        float pull = e.getPullRadius() * Math.min(1f, intro);
        int streaks = (big ? 70 : 40) * (q + 1) / 4 + 10;
        vc = vcp.getBuffer(VfxLayers.additive("streak"));
        float[] heads = new float[streaks * 4];
        for (int j = 0; j < streaks; j++) {
            float h1 = Vfx.hash(j * 13 + 101), h2 = Vfx.hash(j * 13 + 102), h3 = Vfx.hash(j * 13 + 103), h4 = Vfx.hash(j * 13 + 104);
            float period = 0.8f + 0.7f * h1;
            float p = frac(t / period + h2);
            float rs = pull * (0.22f + 0.5f * h3);
            float th = h4 * 6.283f, ph = (float) Math.acos(2 * Vfx.hash(j * 13 + 105) - 1);
            float dx = MathHelper.sin(ph) * MathHelper.cos(th), dy = MathHelper.cos(ph) * 0.7f, dz = MathHelper.sin(ph) * MathHelper.sin(th);
            float[] a = streakPos(dx, dy, dz, rs, Math.max(0, p - 0.07f), s);
            float[] b = streakPos(dx, dy, dz, rs, p, s);
            float f = (float) Math.pow(MathHelper.sin(p * (float) Math.PI), 0.6) * (0.2f + 0.8f * p) * G * 0.75f;
            Vfx.streak(vc, en, camRel, a[0], a[1], a[2], b[0], b[1], b[2], 0.05f + 0.035f * s * (0.4f + p),
                    0.45f * f, 0.85f * f, 1.0f * f, 1);
            heads[j * 4] = b[0]; heads[j * 4 + 1] = b[1]; heads[j * 4 + 2] = b[2]; heads[j * 4 + 3] = f;
        }
        vc = vcp.getBuffer(VfxLayers.additive("spark"));
        for (int j = 0; j < streaks; j += 3) {
            float f = heads[j * 4 + 3];
            Vfx.billboard(vc, en, heads[j * 4], heads[j * 4 + 1], heads[j * 4 + 2], 0.12f + 0.05f * s, 0, 0.7f * f, 0.95f * f, 1f * f, 1);
        }

        // 7. white-hot core
        vc = vcp.getBuffer(VfxLayers.additive("blue_core"));
        float flick = 1f + 0.05f * MathHelper.sin(t * 31f) + 0.03f * MathHelper.sin(t * 17f);
        float cg = G * (0.4f + 0.6f * near);
        Vfx.billboard(vc, en, 0, 0, 0, s * 1.55f * flick, 0, 0.85f * cg, 0.95f * cg, cg, 1);
    }

    private void renderTrail(BlueOrbEntity e, MatrixStack.Entry en, VertexConsumerProvider vcp, Vec3d camRel,
                             double ex, double ey, double ez, float s, float G) {
        int len = e.trail.length;
        float[] pts = new float[len * 3], w = new float[len], col = new float[len * 4];
        int n = 0;
        pts[0] = 0; pts[1] = 0; pts[2] = 0; w[0] = s * 0.9f;
        col[0] = col[1] = col[2] = 0; col[3] = 1;
        n = 1;
        for (int i = 0; i < len - 1; i++) {
            Vec3d p = e.trail[(e.trailHead - i + len * 2) % len];
            if (p == null) break;
            float k = (i + 1) / (float) (len - 1);
            pts[n * 3] = (float) (p.x - ex); pts[n * 3 + 1] = (float) (p.y - ey); pts[n * 3 + 2] = (float) (p.z - ez);
            w[n] = s * 0.9f * (1f - k);
            float f = (1f - k) * (1f - k) * 0.55f * G;
            col[n * 4] = 0.35f * f; col[n * 4 + 1] = 0.7f * f; col[n * 4 + 2] = 1f * f; col[n * 4 + 3] = 1;
            n++;
        }
        // fade the head segment in so the trail doesn't overdraw the core
        col[0] = col[4] * 0.3f; col[1] = col[5] * 0.3f; col[2] = col[6] * 0.3f;
        VertexConsumer vc = vcp.getBuffer(VfxLayers.additive("ribbon_energy"));
        Vfx.ribbon(vc, en, camRel, pts, n, w, col, 0f, 0.35f);
    }

    private static float[] streakPos(float dx, float dy, float dz, float rs, float p, float s) {
        float r = Math.max(s * 0.35f, rs * (float) Math.pow(1 - p, 1.5));
        float swirl = p * 2.4f;
        float c = MathHelper.cos(swirl), sn = MathHelper.sin(swirl);
        float x = dx * c - dz * sn, z = dx * sn + dz * c;
        return new float[]{x * r, dy * r, z * r};
    }

    private static float frac(float f) { return f - (float) Math.floor(f); }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }
}
