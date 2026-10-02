package com.gojolimitless.client.render;

import com.gojolimitless.client.nuke.NukeClient;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.NukeEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.gojolimitless.entity.NukeEntity.*;

/**
 * Remote Hollow Purple. Every visual is a pure function of the entity's age:
 * Red at the fingertip (rays, flood) → Red streaking into the sky → Blue forming far across the sky, boosted by a
 * beam and wound in spiral arms as its incantation is chanted → both rushing in on the target, arcing → the flash
 * → the imaginary mass blooming: a white-hot heart, a violet erasure field sweeping outward with its rim burning,
 * lightning, a shock ring — then the haze.
 */
public class NukeRenderer extends EntityRenderer<NukeEntity> {
    private static final int PLASMA_FRAMES = 32, PLASMA_GRID = 6;

    public NukeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override public boolean shouldRender(NukeEntity e, Frustum f, double x, double y, double z) { return true; }
    @Override public Identifier getTexture(NukeEntity e) { return VfxLayers.tex("purple_core"); }

    @Override
    public void render(NukeEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        vcp = DeferredVfx.route(vcp);
        if (RenderPath.inShadowPass()) return;
        float t = e.age + td;
        float G = RenderPath.glowScale();
        int q = ConfigManager.get().client.quality.ordinal();
        Vec3d C = e.getLerpedPos(td);
        Vec3d cam = Vfx.cameraPos();

        // ---------------- Red
        if (t < T_COLLIDE) {
            Vec3d hand = NukeClient.handPos(e, td);
            Vec3d rp = e.redPos(t, hand);
            float s = e.redSize(t);
            // at our own fingertip in first person, draw it smaller so it doesn't swallow the view
            var mc = MinecraftClient.getInstance();
            boolean ownFirst = mc.player != null && e.getOwnerId() == mc.player.getId() && mc.options.getPerspective().isFirstPerson()
                    && !com.gojolimitless.client.cutscene.CutsceneDirector.active() && t < T_THROW;
            if (ownFirst) s *= 0.75f;
            float charge = (float) NukeClient.smooth(T_RED_FORM, T_SIGN, t);
            float intro = (float) NukeClient.smooth(T_RED_FORM - 2, T_RED_FORM + 4, t);
            if (intro > 0) {
                if (t >= T_THROW && t < T_SKY + 10) trail(e, ms, vcp, C, cam, t, td, true, s, G);
                if (t >= T_CONVERGE) trail(e, ms, vcp, C, cam, t, td, true, s, G);
                drawRed(ms, vcp, rp.subtract(C), cam.subtract(rp), s * intro, t, t < T_THROW ? charge : 0.6f, t < T_THROW, G, q);
            }
        }

        // ---------------- Blue, the boost beam and the incantation spirals
        if (t >= T_SKY && t < T_COLLIDE) {
            Vec3d bp = e.bluePos(t);
            float s = e.blueSize(t);
            if (t >= T_CONVERGE) trail(e, ms, vcp, C, cam, t, td, false, s, G);
            drawBlue(ms, vcp, bp.subtract(C), cam.subtract(bp), s, t, G, q);
            boostBeam(e, ms, vcp, C, cam, bp, t, G);
            spirals(ms, vcp, bp.subtract(C), cam.subtract(bp), s, t, G);
        }

        // ---------------- convergence: arcs between the two as they close
        if (t >= T_CONVERGE && t < T_COLLIDE) {
            Vec3d a = e.redPos(t, e.getOrigin()).subtract(C), b = e.bluePos(t).subtract(C);
            float k = (float) NukeClient.smooth(T_CONVERGE + 20, T_COLLIDE, t);
            int arcs = 1 + (int) (k * 4);
            for (int i = 0; i < arcs && k > 0; i++) {
                float[] pts = Lightning.bolt((float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z,
                        (long) (t * 0.5f) * 131 + i * 17, 5, 0.22f);
                Lightning.draw(ms.peek(), vcp, cam.subtract(C), pts, 0.12f + 0.2f * k, 0.8f, 0.35f, 1f, G * k);
            }
        }

        // ---------------- collision flash, then the silent point
        if (t >= T_COLLIDE && t < T_BLOOM + 4) collision(e, ms, vcp, cam.subtract(C), t, G);

        // ---------------- the bloom
        if (t >= T_BLOOM) bloom(e, ms, vcp, cam.subtract(C), t, G, q);
    }

    // =================================================================================================== Red
    /**
     * Red. At the fingertips it is small and white-hot inside a red corona, with a few long laser rays lancing out
     * across the frame, one pair per word of its incantation (the clip's opening); in the sky it is the swirling red
     * star. Near the camera the patterned layers fade out so it never fills the frame.
     */
    private void drawRed(MatrixStack ms, VertexConsumerProvider vcp, Vec3d off, Vec3d camRel, float s, float t, float pw, boolean atHand,
                         float G, int q) {
        ms.push();
        ms.translate(off.x, off.y, off.z);
        MatrixStack.Entry en = ms.peek();
        float tt = t / 20f;
        float camD = (float) camRel.length();
        float flick = 1f + 0.08f * MathHelper.sin(tt * 43f) + 0.05f * MathHelper.sin(tt * 27f);
        VertexConsumer vc;
        if (atHand) {
            // corona and bloom: soft, red, never patterned
            vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
            float bloom = (0.22f + 0.3f * pw) * G * (0.35f + 0.65f * Vfx.smooth(0.4f, 3.0f, camD));
            Vfx.billboard(vc, en, 0, 0, 0, s * (9f + 7f * pw), 0, bloom, 0.05f * bloom, 0.03f * bloom, 1);
            Vfx.billboard(vc, en, 0, 0, 0, s * 3.4f * flick, 0, 0.9f * G, 0.16f * G, 0.08f * G, 1);
            // the churning energy, small and faint
            float sw = 0.3f * G * Vfx.smooth(0.25f, 1.2f, camD);
            vc = vcp.getBuffer(VfxLayers.additive("red_swirl"));
            Vfx.billboard(vc, en, 0, 0, 0, s * 2.6f * flick, tt * 3.4f, sw, sw, sw, 1);
            // the rays: long red lasers from the fingertip, two more with each word
            vc = vcp.getBuffer(VfxLayers.additive("ray"));
            int words = (t >= T_RED_1 ? 1 : 0) + (t >= T_RED_2 ? 1 : 0) + (t >= T_RED_3 ? 1 : 0);
            int n = Math.min(6, 2 * words + (q >= 2 ? 1 : 0));
            for (int i = 0; i < n; i++) {
                int word = i / 2;
                float born = word == 0 ? T_RED_1 : word == 1 ? T_RED_2 : T_RED_3;
                float appear = MathHelper.clamp((t - born) / 5f, 0f, 1f);
                if (appear <= 0) continue;
                float h1 = Vfx.hash(i * 5 + 71), h2 = Vfx.hash(i * 5 + 72), h3 = Vfx.hash(i * 5 + 73);
                // mostly sideways, fanned around the caster; each sweeps slowly
                float az = i * 2.4f + h1 * 0.8f + tt * (0.05f + 0.06f * h2) * (i % 2 == 0 ? 1 : -1);
                float el = (h3 - 0.45f) * 0.9f;
                Vector3f d = new Vector3f(MathHelper.cos(az) * MathHelper.cos(el), MathHelper.sin(el), MathHelper.sin(az) * MathHelper.cos(el));
                float len = (40f + 50f * h2) * (float) Math.pow(appear, 0.35);
                float pulse = 0.8f + 0.2f * MathHelper.sin(tt * (9f + 5f * h3) + i);
                float f = pulse * G * (0.9f + 0.4f * pw);
                float w = (0.045f + 0.035f * pw) * (0.7f + 0.6f * h1);
                Vfx.streak(vc, en, camRel, 0, 0, 0, d.x * len, d.y * len, d.z * len, w, f, 0.14f * f, 0.07f * f, 1);
                Vfx.streak(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, 0, 0, 0, d.x * len * 0.5f, d.y * len * 0.5f,
                        d.z * len * 0.5f, w * 5f, 0.12f * f, 0.012f * f, 0.006f * f, 1);
                vc = vcp.getBuffer(VfxLayers.additive("ray"));
            }
            vc = vcp.getBuffer(VfxLayers.additive("spark"));
            int sparks = 8 + q * 3;
            for (int i = 0; i < sparks; i++) {
                float h1 = Vfx.hash(i * 7 + 501), h2 = Vfx.hash(i * 7 + 502), h3 = Vfx.hash(i * 7 + 503);
                float ph = frac(tt * (1.4f + h1) + h2);
                Vector3f d = new Vector3f(h1 - 0.5f, h2 - 0.5f, h3 - 0.5f).normalize();
                float r = s * (1.2f + 5f * ph);
                float f = (1f - ph) * G;
                Vfx.billboard(vc, en, d.x * r, d.y * r, d.z * r, 0.012f + 0.02f * s, 0, f, 0.3f * f, 0.15f * f, 1);
            }
            // white-hot centre
            vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.billboard(vc, en, 0, 0, 0, s * 1.5f * flick, 0, G, 0.9f * G, 0.85f * G, 1);
            Vfx.billboard(vc, en, 0, 0, 0, s * 0.8f, 0, G, G, G, 1);
            ms.pop();
            return;
        }
        float near = Vfx.smooth(s * 0.8f, s * 3.0f, camD);
        vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        float halo = (0.1f + 0.1f * pw) * G * near;
        Vfx.billboard(vc, en, 0, 0, 0, s * (6f + 4f * pw), 0, halo, 0.1f * halo, 0.05f * halo, 1);
        Vfx.fresnelSphere(vc, en, camRel, s * 0.95f, 14, 22, G, 0.25f * G, 0.1f * G, 0f, 0.8f, 2.2f);
        float sw = (0.6f + 0.4f * pw) * G * near;
        vc = vcp.getBuffer(VfxLayers.additive("red_swirl_b"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 3.1f, tt * 1.3f, 0.55f * sw, 0.55f * sw, 0.55f * sw, 1);
        vc = vcp.getBuffer(VfxLayers.additive("red_swirl"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.4f * flick, tt * 2.8f, 0.95f * sw, 0.95f * sw, 0.95f * sw, 1);
        vc = vcp.getBuffer(VfxLayers.additive("spark"));
        int sparks = 14 + q * 4;
        for (int i = 0; i < sparks; i++) {
            float h1 = Vfx.hash(i * 7 + 501), h2 = Vfx.hash(i * 7 + 502), h3 = Vfx.hash(i * 7 + 503);
            float ph = frac(tt * (1.2f + h1) + h2);
            Vector3f d = new Vector3f(h1 - 0.5f, h2 - 0.5f, h3 - 0.5f).normalize();
            float r = s * (0.6f + 3.5f * ph);
            float f = (1f - ph) * G;
            Vfx.billboard(vc, en, d.x * r, d.y * r, d.z * r, 0.05f + 0.06f * s, 0, f, 0.35f * f, 0.2f * f, 1);
        }
        vc = vcp.getBuffer(VfxLayers.additive("red_core"));
        float cg = G * (0.5f + 0.5f * near) * flick;
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.1f, 0, cg, cg, cg, 1);
        vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 0.9f, 0, G, 0.85f * G, 0.8f * G, 1);
        ms.pop();
    }

    // =================================================================================================== Blue
    private void drawBlue(MatrixStack ms, VertexConsumerProvider vcp, Vec3d off, Vec3d camRel, float s, float t, float G, int q) {
        if (s <= 0.01f) return;
        ms.push();
        ms.translate(off.x, off.y, off.z);
        MatrixStack.Entry en = ms.peek();
        float tt = t / 20f;
        float near = Vfx.smooth(s * 0.7f, s * 2.6f, (float) camRel.length());
        VertexConsumer vc = vcp.getBuffer(VfxLayers.alpha("blue_lens"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.35f, 0, 1, 1, 1, 0.9f * near);
        vc = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        float halo = 0.07f * G * near;
        Vfx.billboard(vc, en, 0, 0, 0, s * 5f, 0, 0.12f * halo, 0.35f * halo, halo, 1);
        int lat = q >= 2 ? 18 : 12, lon = q >= 2 ? 28 : 18;
        Vfx.fresnelSphere(vc, en, camRel, s * 0.92f, lat, lon, 0.25f * G, 0.55f * G, G, 0f, 0.75f, 2.2f);
        Vfx.fresnelSphere(vc, en, camRel, s * 1.55f, lat, lon, 0.08f * G, 0.2f * G, 0.55f * G, 0f, 0.4f, 3.5f);
        float vb = 0.34f * G * near, va = 0.62f * G * near, vi = 0.42f * G * near;
        vc = vcp.getBuffer(VfxLayers.additive("blue_vortex_b"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 2.35f, -tt * 0.9f, 0.25f * vb, 0.5f * vb, vb, 1);
        vc = vcp.getBuffer(VfxLayers.additive("blue_vortex_a"));
        Vfx.billboard(vc, en, 0, 0, 0, s * 1.8f, -tt * 2.1f, 0.45f * va, 0.75f * va, va, 1);
        Vfx.billboard(vc, en, 0, 0, 0, s * 1.25f, -tt * 3.7f + 1.7f, 0.5f * vi, 0.8f * vi, vi, 1);
        // inflowing attraction streaks
        vc = vcp.getBuffer(VfxLayers.additive("streak"));
        int streaks = 30 + q * 10;
        for (int j = 0; j < streaks; j++) {
            float h1 = Vfx.hash(j * 13 + 901), h2 = Vfx.hash(j * 13 + 902), h3 = Vfx.hash(j * 13 + 903), h4 = Vfx.hash(j * 13 + 904);
            float p = frac(tt / (0.8f + 0.7f * h1) + h2);
            float rs = s * (3f + 5f * h3);
            float th = h4 * 6.283f, ph = (float) Math.acos(2 * Vfx.hash(j * 13 + 905) - 1);
            float dx = MathHelper.sin(ph) * MathHelper.cos(th), dy = MathHelper.cos(ph) * 0.7f, dz = MathHelper.sin(ph) * MathHelper.sin(th);
            float r0 = Math.max(s * 0.35f, rs * (float) Math.pow(1 - Math.max(0, p - 0.07f), 1.5));
            float r1 = Math.max(s * 0.35f, rs * (float) Math.pow(1 - p, 1.5));
            float f = (float) Math.pow(MathHelper.sin(p * (float) Math.PI), 0.6) * (0.2f + 0.8f * p) * G * 0.75f;
            Vfx.streak(vc, en, camRel, dx * r0, dy * r0, dz * r0, dx * r1, dy * r1, dz * r1, 0.06f + 0.04f * s, 0.45f * f, 0.85f * f, f, 1);
        }
        vc = vcp.getBuffer(VfxLayers.additive("blue_core"));
        float cg = G * (0.4f + 0.6f * near) * (1f + 0.05f * MathHelper.sin(tt * 31f));
        Vfx.billboard(vc, en, 0, 0, 0, s * 1.55f, 0, 0.85f * cg, 0.95f * cg, cg, 1);
        ms.pop();
    }

    /**
     * The incantation after the fact: a cyan brush-stroke streak rises from the caster into Blue (the clip's 9 s),
     * and where it lands a ring of light flares around Blue.
     */
    private void boostBeam(NukeEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d C, Vec3d cam, Vec3d bp, float t, float G) {
        if (t < T_BOOST || t > T_BLUE_1 + 16) return;
        Vec3d camRel = cam.subtract(C);
        MatrixStack.Entry en = ms.peek();
        if (t < T_BOOST + 14) {
            Vec3d from = e.getOrigin().add(0, 1.6, 0);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null && mc.world.getEntityById(e.getOwnerId()) instanceof net.minecraft.entity.LivingEntity le)
                from = le.getLerpedPos(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false)).add(0, 1.6, 0);
            float head = (float) Math.pow(NukeClient.smooth(T_BOOST, T_BOOST + 9, t), 0.8);
            float tail = (float) NukeClient.smooth(T_BOOST + 3, T_BOOST + 13, t);
            int n = 28;
            float[] pts = new float[n * 3], w = new float[n], col = new float[n * 4];
            // a gentle S-curve, like a brush flick
            Vec3d d = bp.subtract(from);
            Vec3d sideV = d.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(d.length() * 0.08);
            for (int i = 0; i < n; i++) {
                float k = i / (float) (n - 1);                   // 0 = head, 1 = tail
                float u = head - (head - tail) * k;
                Vec3d p = from.add(d.multiply(u)).add(sideV.multiply(Math.sin(u * Math.PI * 2)));
                pts[i * 3] = (float) (p.x - C.x); pts[i * 3 + 1] = (float) (p.y - C.y); pts[i * 3 + 2] = (float) (p.z - C.z);
                w[i] = (1.6f - 1.1f * k) * (0.5f + 0.5f * (float) Math.sin(Math.PI * Math.min(1, k * 1.2 + 0.1)));
                float f = G * (1f - k * 0.7f);
                col[i * 4] = 0.35f * f; col[i * 4 + 1] = 0.9f * f; col[i * 4 + 2] = f; col[i * 4 + 3] = 1;
            }
            Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("ribbon_brush")), en, camRel, pts, n, w, col, -t * 0.08f, 0.05f);
            Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, pts, n, scale(w, 3f), scale(col, 0.22f), 0.5f, 0f);
        }
        // the flare: a ring of light bursting out around Blue as the streak lands and the first word is spoken
        float fk = (t - (T_BOOST + 9)) / 14f;
        if (fk > 0f && fk < 1f) {
            Vec3d o = bp.subtract(C);
            float s = e.blueSize(t);
            float R = s * (1.3f + 2.2f * (float) Math.pow(fk, 0.5));
            float f = (1f - fk) * (1f - fk) * G;
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("ring")), en, (float) o.x, (float) o.y, (float) o.z, R * 2.1f, 0,
                    0.55f * f, 0.85f * f, f, 1);
            Quaternionf qc = Vfx.cameraRotation();
            Vector3f U = qc.transform(new Vector3f(1, 0, 0)), V = qc.transform(new Vector3f(0, 1, 0));
            VertexConsumer sp = vcp.getBuffer(VfxLayers.additive("spark"));
            for (int i = 0; i < 48; i++) {
                float a = i / 48f * MathHelper.TAU + Vfx.hash(i + 4000) * 0.12f;
                float rr = R * (0.92f + 0.16f * Vfx.hash(i + 4100));
                float x = MathHelper.cos(a) * rr, y = MathHelper.sin(a) * rr;
                float ff = f * (0.6f + 0.4f * Vfx.hash(i + 4200));
                Vfx.billboard(sp, en, (float) o.x + U.x * x + V.x * y, (float) o.y + U.y * x + V.y * y, (float) o.z + U.z * x + V.z * y,
                        s * (0.12f + 0.1f * Vfx.hash(i + 4300)), 0, 0.7f * ff, 0.9f * ff, ff, 1);
            }
        }
    }

    /**
     * Blue's incantation, chanted after the fact: bright brush-stroke arms wind in around it with each word (the
     * clip's "6"), tighten, and are swallowed once the incantation is complete.
     */
    private void spirals(MatrixStack ms, VertexConsumerProvider vcp, Vec3d off, Vec3d camRel, float s, float t, float G) {
        float on = (float) (NukeClient.smooth(T_BLUE_1, T_BLUE_1 + 8, t) * (1 - NukeClient.smooth(T_BLUE_3 + 14, T_LIFT + 6, t)));
        if (on <= 0.01f) return;
        float words = (float) (NukeClient.smooth(T_BLUE_1, T_BLUE_1 + 10, t) + NukeClient.smooth(T_BLUE_2, T_BLUE_2 + 10, t)
                + NukeClient.smooth(T_BLUE_3, T_BLUE_3 + 10, t));
        float swallow = (float) NukeClient.smooth(T_BLUE_3 + 6, T_LIFT + 6, t);
        float R = s * (1.7f + 0.55f * words) * (1f - 0.6f * swallow);
        Quaternionf q = Vfx.cameraRotation();
        Vector3f U = q.transform(new Vector3f(1, 0, 0)), V = q.transform(new Vector3f(0, 1, 0));
        float tt = t / 20f;
        ms.push();
        ms.translate(off.x, off.y, off.z);
        MatrixStack.Entry en = ms.peek();
        int arms = words < 1.5f ? 1 : 2;
        int n = 64;
        for (int a = 0; a < arms; a++) {
            float[] pts = new float[n * 3], w = new float[n], col = new float[n * 4];
            float a0 = a * MathHelper.PI + tt * 2.2f;
            float grow = a == 0 ? 1f : (float) NukeClient.smooth(T_BLUE_2, T_BLUE_2 + 10, t);
            float sweep = 3.6f + 1.2f * words;                // how far round it winds
            for (int i = 0; i < n; i++) {
                float k = i / (float) (n - 1);                  // 0 = the outer tip, 1 = into the core
                float ang = a0 - k * sweep;
                float rr = R * (float) Math.pow(1 - k, 0.9) * (0.35f + 0.65f * grow) + s * 0.9f * k;
                float x = MathHelper.cos(ang) * rr, y = MathHelper.sin(ang) * rr;
                pts[i * 3] = U.x * x + V.x * y; pts[i * 3 + 1] = U.y * x + V.y * y; pts[i * 3 + 2] = U.z * x + V.z * y;
                // thin at the tip, thick through the middle, thin where it enters the core
                w[i] = s * (0.05f + 0.55f * (float) Math.pow(Math.sin(Math.PI * Math.min(1, k * 1.15)), 0.8)) * grow;
                float f = on * G * (float) Math.pow(Math.sin(Math.PI * Math.min(1, k * 1.05 + 0.02)), 0.5);
                col[i * 4] = 0.3f * f; col[i * 4 + 1] = 0.85f * f; col[i * 4 + 2] = f; col[i * 4 + 3] = 1;
            }
            Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("ribbon_brush")), en, camRel, pts, n, w, col, -tt * 1.2f + a * 0.5f, 0.05f);
            Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, pts, n, scale(w, 2.6f), scale(col, 0.16f), 0.5f, 0f);
        }
        ms.pop();
    }

    /** Trail behind a flying orb, from its analytic past positions. */
    private void trail(NukeEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d C, Vec3d cam, float t, float td, boolean red, float s, float G) {
        int n = 14;
        float[] pts = new float[n * 3], w = new float[n], col = new float[n * 4];
        Vec3d hand = NukeClient.handPos(e, td);
        for (int i = 0; i < n; i++) {
            float tp = t - i * 0.7f;
            Vec3d p = red ? e.redPos(tp, hand) : e.bluePos(tp);
            pts[i * 3] = (float) (p.x - C.x); pts[i * 3 + 1] = (float) (p.y - C.y); pts[i * 3 + 2] = (float) (p.z - C.z);
            float k = i / (float) (n - 1);
            w[i] = s * 0.9f * (1f - k);
            float f = (1f - k) * (1f - k) * 0.6f * G * (i == 0 ? 0.3f : 1f);
            if (red) { col[i * 4] = f; col[i * 4 + 1] = 0.18f * f; col[i * 4 + 2] = 0.08f * f; }
            else { col[i * 4] = 0.35f * f; col[i * 4 + 1] = 0.7f * f; col[i * 4 + 2] = f; }
            col[i * 4 + 3] = 1;
        }
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("ribbon_brush")), ms.peek(), cam.subtract(C), pts, n, w, col, -t * 0.05f, 0.12f);
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), ms.peek(), cam.subtract(C), pts, n, scale(w, 2.2f), scale(col, 0.3f), 0.5f, 0f);
    }

    // =================================================================================================== collision
    private void collision(NukeEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float t, float G) {
        MatrixStack.Entry en = ms.peek();
        float k = t - T_COLLIDE;
        if (k < 8) {
            float f = (1f - k / 8f) * G;
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, 0, 0, 0, 30f * (0.4f + k / 8f), 0, f, 0.8f * f, f, 1);
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("purple_core")), en, 0, 0, 0, 8f, 0, f, f, f, 1);
        }
        // the implosion: a single violet point in the silence
        float p = (float) (NukeClient.smooth(T_COLLIDE + 4, T_COLLIDE + 10, t) * (1 - NukeClient.smooth(T_BLOOM, T_BLOOM + 4, t)));
        if (p > 0) {
            float pulse = 1f + 0.3f * MathHelper.sin(t * 1.3f);
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("purple_core")), en, 0, 0, 0, 1.8f * pulse, 0, p * G, p * G, p * G, 1);
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, 0, 0, 0, 7f * pulse, 0, 0.5f * p * G, 0.1f * p * G, 0.6f * p * G, 1);
            VertexConsumer sp = vcp.getBuffer(VfxLayers.additive("spark"));
            for (int i = 0; i < 30; i++) {
                float h1 = Vfx.hash(i * 3 + 7001), h2 = Vfx.hash(i * 3 + 7002), h3 = Vfx.hash(i * 3 + 7003);
                Vector3f d = new Vector3f(h1 - 0.5f, h2 - 0.5f, h3 - 0.5f).normalize().mul(3f + 9f * h2);
                float tw = 0.5f + 0.5f * MathHelper.sin(t * (0.6f + h3) + i);
                float f = p * tw * G;
                Vfx.billboard(sp, en, d.x, d.y, d.z, 0.25f + 0.3f * h1, 0, f, 0.35f * f, f, 1);
            }
        }
    }

    // =================================================================================================== bloom
    /**
     * The imaginary mass erupts (clip 19-22.5 s): a white-hot heart with a hard edge fading to magenta, a star and a
     * ring bursting from it, lightning crawling out through the field, the field's boundary burning violet as it
     * sweeps outward. Contrast comes from keeping everything outside the heart dark and saturated (the HUD grade
     * multiplies the world toward magenta, casters are drawn as silhouettes) instead of washing the frame with light.
     */
    private void bloom(NukeEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float t, float G, int q) {
        MatrixStack.Entry en = ms.peek();
        float R = e.getBlastRadius();
        float r = e.blastRadiusAt(t);
        float bt = t - T_BLOOM;
        int expand = e.getExpandTicks();
        float fade = (1f - (float) NukeClient.smooth(T_BLOOM + expand + 10, T_END + 30, t)) * (1f - (float) NukeClient.smooth(T_WHITE, T_WHITE + 24, t));
        if (fade <= 0.001f) return;
        float tt = t / 20f;
        float camD = (float) camRel.length();
        float inside = 1f - Vfx.smooth(r * 0.9f, r * 1.1f, camD);

        // the heart
        float rc = Math.max(0.5f, (2.5f + 0.1f * r) * (1f - 0.4f * (float) NukeClient.smooth(T_BLOOM + expand, T_END, t)));
        float burst = (float) Math.exp(-bt / 5f);
        float heat = G * fade * (1f + 1.2f * burst);
        Vfx.billboard(vcp.getBuffer(VfxLayers.additive("purple_core")), en, 0, 0, 0, rc * 2.3f, 0, heat, heat, heat, 1);
        VertexConsumer glow = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        Vfx.fresnelSphere(glow, en, camRel, rc, 20, 30, heat, 0.55f * heat, heat, 0.9f, 0.9f, 2.0f);
        Vfx.billboard(glow, en, 0, 0, 0, rc * 1.9f, 0, heat, 0.9f * heat, heat, 1);
        Vfx.billboard(glow, en, 0, 0, 0, rc * 5.5f, 0, 0.28f * heat, 0.03f * heat, 0.32f * heat, 1);
        // the first instant: a violet-white star bursts out of it
        if (bt < 14) {
            float sf = (1f - bt / 14f) * G;
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("flash_star")), en, 0, 0, 0, R * (0.3f + 1.0f * bt / 14f), bt * 0.02f,
                    sf, 0.75f * sf, sf, 1);
        }

        // the erasure field: its boundary burns violet as it sweeps outward; faint turbulence inside, never a wash
        if (r > 1f) {
            float rim = G * fade * (0.45f + 0.55f * burst + 0.3f * (1f - (float) NukeClient.smooth(0, expand, bt)));
            Vfx.fresnelSphere(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, r, q >= 2 ? 28 : 20, q >= 2 ? 40 : 28,
                    0.85f * rim, 0.18f * rim, rim, 0.0f, 0.55f, 3.2f);
            VertexConsumer pl = vcp.getBuffer(VfxLayers.additive("purple_plasma"));
            int frame = (int) (t * 0.9f) % PLASMA_FRAMES;
            float pf = 0.16f * G * fade * (1f - 0.85f * inside);
            if (pf > 0.005f) {
                Vfx.billboardFrame(pl, en, 0, 0, 0, r * 1.02f, tt * 0.15f, pf, pf, pf, 1, frame, PLASMA_GRID, Vfx.FULL_BRIGHT);
                Vfx.billboardFrame(pl, en, 0, 0, 0, r * 0.7f, -tt * 0.25f + 2f, 0.6f * pf, 0.4f * pf, 0.8f * pf, 1, (frame + 11) % PLASMA_FRAMES,
                        PLASMA_GRID, Vfx.FULL_BRIGHT);
            }
        }

        // lightning crawling out from the heart through the field
        int bolts = 10 + q * 4;
        long tick = (long) (t / 2f);
        for (int i = 0; i < bolts && r > 2f; i++) {
            long seed = tick * 37 + i * 1009 + e.getId() * 7L;
            float h1 = Vfx.hash((int) seed), h2 = Vfx.hash((int) seed + 1), h3 = Vfx.hash((int) seed + 2), h4 = Vfx.hash((int) seed + 3);
            Vector3f d = new Vector3f(h1 - 0.5f, (h2 - 0.5f) * 0.8f, h3 - 0.5f).normalize();
            Vector3f d2 = new Vector3f(d).add(new Vector3f(h2 - 0.5f, h3 - 0.5f, h1 - 0.5f).mul(0.6f)).normalize();
            Vector3f a = new Vector3f(d).mul(rc * 0.95f), b = new Vector3f(d2).mul(Math.min(r, R * 0.8f) * (0.45f + 0.55f * h4));
            float[] pts = Lightning.bolt(a.x, a.y, a.z, b.x, b.y, b.z, seed, 5, 0.3f);
            Lightning.draw(en, vcp, camRel, pts, 0.12f + r * 0.005f, 0.95f, 0.45f, 1f, fade * G * (0.7f + 0.5f * Vfx.hash((int) seed + 5)));
        }

        // a ring racing out along the ground plane, and a camera-facing ring thrown off the heart (clip 20.0 s)
        float rk = MathHelper.clamp(bt / (expand * 0.8f), 0f, 1f);
        float rr = R * (0.1f + 1.35f * (float) Math.pow(rk, 0.6));
        float rf = (1f - rk) * G * 0.8f;
        // (from inside the ring, at a grazing angle, it would only read as a stray ellipse)
        rf *= Vfx.smooth(rr * 0.8f, rr * 1.15f, (float) Math.hypot(camRel.x, camRel.z));
        if (rf > 0.01f) {
            Vfx.planeQuad(vcp.getBuffer(VfxLayers.additive("ring")), en, 0, -Math.min(r, 6f), 0, new Vector3f(0, 1, 0), rr, tt, 0.9f * rf, 0.3f * rf, rf, 1);
        }
        float sk = MathHelper.clamp(bt / 16f, 0f, 1f);
        if (sk < 1f) {
            float sf = (1f - sk) * (1f - sk) * G;
            Vfx.billboard(vcp.getBuffer(VfxLayers.additive("ring")), en, 0, 0, 0, rc * 2.2f + R * 1.2f * (float) Math.pow(sk, 0.7), 0,
                    sf, 0.35f * sf, sf, 1);
        }

        // violet motes lingering after the field fades
        float haze = (float) (NukeClient.smooth(T_BLOOM + expand * 0.5, T_BLOOM + expand + 20, t) * (1 - NukeClient.smooth(T_END - 10, T_END + 40, t)));
        if (haze > 0.01f) {
            VertexConsumer sp = vcp.getBuffer(VfxLayers.additive("spark"));
            int n = 40 + q * 20;
            for (int i = 0; i < n; i++) {
                float h1 = Vfx.hash(i * 3 + 9001), h2 = Vfx.hash(i * 3 + 9002), h3 = Vfx.hash(i * 3 + 9003);
                Vector3f d = new Vector3f(h1 - 0.5f, (h2 - 0.5f) * 0.6f, h3 - 0.5f).normalize().mul(R * (0.2f + 0.8f * h2));
                float tw = 0.5f + 0.5f * MathHelper.sin(t * (0.2f + 0.3f * h3) + i * 1.7f);
                float f = haze * tw * G * 0.8f;
                Vfx.billboard(sp, en, d.x, d.y + (t - T_BLOOM) * 0.02f, d.z, 0.4f + 0.6f * h1, 0, f, 0.3f * f, f, 1);
            }
        }
    }

    private static float[] scale(float[] a, float k) {
        float[] o = a.clone();
        for (int i = 0; i < o.length; i++) o[i] *= k;
        return o;
    }

    private static float frac(float f) { return f - (float) Math.floor(f); }
}
