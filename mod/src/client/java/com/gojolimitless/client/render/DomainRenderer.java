package com.gojolimitless.client.render;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.client.domain.DomainClient;
import com.gojolimitless.client.domain.DomainClient.Phase;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.DomainEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
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

import static com.gojolimitless.entity.DomainEntity.*;

/**
 * Domain Expansion: Unlimited Void. Everything is drawn around the viewer's camera, phase by phase (S1E7):
 * the world goes white · black ink erupts behind the caster with neon speed lines · a tunnel of information
 * (streaks of violet, magenta, red and white, torn white fragments) · the flash · the void: a Blender-rendered
 * panorama of deep space, the giant black hole (a Cycles loop: the smoke ring turning, the stream pouring off),
 * smoke wisps drifting around you. From outside, the barrier is a black dome.
 */
public class DomainRenderer extends EntityRenderer<DomainEntity> {
    private static final Identifier WHITE = Identifier.of(GojoLimitless.MOD_ID, "textures/gui/white.png");
    private static final float[][] NEON = {{0.48f, 0.24f, 1f}, {1f, 0.25f, 0.66f}, {1f, 0.19f, 0.31f}, {1f, 0.95f, 1f}, {0.62f, 0.3f, 1f}};

    public DomainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override public boolean shouldRender(DomainEntity e, Frustum f, double x, double y, double z) { return true; }
    @Override public Identifier getTexture(DomainEntity e) { return VfxLayers.tex("glow_soft"); }

    @Override
    public void render(DomainEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        vcp = DeferredVfx.route(vcp);
        if (RenderPath.inShadowPass()) return;
        float t = e.age + td;
        Vec3d C = e.getLerpedPos(td);
        Vec3d cam = Vfx.cameraPos();
        Vec3d camRel = cam.subtract(C);
        boolean inside = camRel.length() <= e.getRadius() + 0.5;
        if (!inside) { barrier(e, ms, vcp, camRel, t); return; }
        Phase p = DomainClient.phase(e, t);
        float G = RenderPath.glowScale();
        switch (p) {
            case WHITE -> white(ms, vcp, camRel, 1f);
            case INK -> { white(ms, vcp, camRel, 1f); ink(e, ms, vcp, C, cam, t, G); }
            case TUNNEL -> tunnel(e, ms, vcp, camRel, t, G);
            case VOID -> interior(e, ms, vcp, camRel, t, G);
            default -> {}
        }
    }

    // =================================================================================================== spheres
    /** An inward sphere of radius r centred at c (entity-relative), equirectangular UVs, rotated by yaw about y. */
    private static void sphere(VertexConsumer vc, MatrixStack.Entry en, Vec3d c, float r, int lat, int lon, float yawRad,
                               float cr, float cg, float cb) {
        for (int i = 0; i < lat; i++) {
            float v0 = i / (float) lat, v1 = (i + 1) / (float) lat;
            float th0 = MathHelper.PI * v0, th1 = MathHelper.PI * v1;
            for (int j = 0; j < lon; j++) {
                float u0 = j / (float) lon, u1 = (j + 1) / (float) lon;
                float ph0 = MathHelper.TAU * u0 + yawRad, ph1 = MathHelper.TAU * u1 + yawRad;
                vtx(vc, en, c, r, th0, ph0, cr, cg, cb, u0, v0);
                vtx(vc, en, c, r, th1, ph0, cr, cg, cb, u0, v1);
                vtx(vc, en, c, r, th1, ph1, cr, cg, cb, u1, v1);
                vtx(vc, en, c, r, th0, ph1, cr, cg, cb, u1, v0);
            }
        }
    }

    private static void vtx(VertexConsumer vc, MatrixStack.Entry en, Vec3d c, float r, float th, float ph, float cr, float cg, float cb, float u, float v) {
        float s = MathHelper.sin(th);
        float x = -s * MathHelper.sin(ph), y = MathHelper.cos(th), z = s * MathHelper.cos(ph);
        Vfx.vert(vc, en, (float) c.x + x * r, (float) c.y + y * r, (float) c.z + z * r, cr, cg, cb, 1f, u, v);
    }

    private static void flush(VertexConsumerProvider vcp, RenderLayer layer) {
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    // =================================================================================================== the white
    /** S1E7 3.25 s: the whole world is flat pale white-pink; only the people near stay. */
    private static void white(MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float k) {
        RenderLayer l = VfxLayers.sky(WHITE);
        sphere(vcp.getBuffer(l), ms.peek(), camRel, 9f, 12, 16, 0f, 0.96f * k, 0.93f * k, 0.95f * k);
        flush(vcp, l);
    }

    // =================================================================================================== the ink
    /** S1E7 4.25 s: black ink erupts from behind the caster, torn edges, neon speed lines shooting out. */
    private static void ink(DomainEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d C, Vec3d cam, float t, float G) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity owner = mc.world != null ? mc.world.getEntityById(e.getOwnerId()) : null;
        Vec3d body = owner != null ? owner.getLerpedPos(mc.getRenderTickCounter().getTickDelta(false)).add(0, 1.0, 0) : C.add(0, 1, 0);
        Vec3d away = body.subtract(cam);
        if (away.lengthSquared() < 1e-4) away = new Vec3d(0, 0, 1);
        away = away.normalize();
        Vec3d at = body.add(away.multiply(2.5)).subtract(C);
        float start = e.isInstant() ? I_INK : D_INK, end = e.isInstant() ? I_VOID : D_TUNNEL;
        float k = MathHelper.clamp((t - start) / (end - start), 0f, 1f);
        int frame = Math.min(15, (int) (k * 16f));
        MatrixStack.Entry en = ms.peek();
        float dist = (float) body.add(away.multiply(2.5)).distanceTo(cam);
        float size = dist * 1.6f;
        Vfx.billboardFrame(vcp.getBuffer(VfxLayers.alpha("ink_burst")), en, (float) at.x, (float) at.y, (float) at.z, size, 0.3f,
                1f, 1f, 1f, 1f, frame, 4, Vfx.FULL_BRIGHT);
        // neon speed lines from the heart of the burst
        VertexConsumer vc = vcp.getBuffer(VfxLayers.additive("streak"));
        var q = Vfx.cameraRotation();
        int lines = 24 + (int) (k * 136);
        for (int i = 0; i < lines; i++) {
            float h1 = Vfx.hash(i * 3 + 11), h2 = Vfx.hash(i * 3 + 12), h3 = Vfx.hash(i * 3 + 13);
            float born = Math.max(0f, (float) i / 160f - 0.05f);                 // each line appears, then draws itself out
            float lk = MathHelper.clamp((k - born) / 0.35f, 0f, 1f);
            if (lk <= 0f) continue;
            float a = h1 * MathHelper.TAU + (h2 - 0.5f) * 0.4f * k;
            float r0 = size * (0.05f + 0.5f * k * h2), r1 = r0 + size * (0.2f + 0.6f * h3) * (0.3f + k) * lk;
            Vector3f d = q.transform(new Vector3f(MathHelper.cos(a), MathHelper.sin(a), 0));
            float[] c = NEON[i % NEON.length];
            float pulse = 0.75f + 0.25f * MathHelper.sin(t * (0.6f + 0.5f * h3) + i);
            float f = G * (0.5f + 0.5f * h2) * (1f - 0.2f * k) * pulse * Math.min(1f, lk * 3f);
            Vfx.streak(vc, en, cam.subtract(C), (float) at.x + d.x * r0, (float) at.y + d.y * r0, (float) at.z + d.z * r0,
                    (float) at.x + d.x * r1, (float) at.y + d.y * r1, (float) at.z + d.z * r1, 0.02f + 0.03f * h3 * dist * 0.1f,
                    c[0] * f, c[1] * f, c[2] * f, 1);
        }
        manifest(en, vcp, at, size, k, t, G);
    }

    /** Torn white fragments of the void showing through the ink as space forms (the last part of the ink). */
    private static void manifest(MatrixStack.Entry en, VertexConsumerProvider vcp, Vec3d at, float size, float k, float t, float G) {
        float m = MathHelper.clamp((k - 0.45f) / 0.55f, 0f, 1f);
        if (m <= 0f) return;
        VertexConsumer frag = vcp.getBuffer(VfxLayers.additive("nebula_frags"));
        for (int i = 0; i < 18; i++) {
            float h1 = Vfx.hash(i * 7 + 501), h2 = Vfx.hash(i * 7 + 502), h3 = Vfx.hash(i * 7 + 503);
            float on = MathHelper.clamp((m - h3 * 0.6f) / 0.3f, 0f, 1f);
            if (on <= 0f) continue;
            var q = Vfx.cameraRotation();
            float a = h1 * MathHelper.TAU, rho = size * (0.12f + 0.38f * h2);
            Vector3f d = q.transform(new Vector3f(MathHelper.cos(a) * rho, MathHelper.sin(a) * rho, 0));
            float f = 0.9f * G * on * (0.7f + 0.3f * MathHelper.sin(t * 0.8f + i));
            Vfx.billboardFrame(frag, en, (float) at.x + d.x, (float) at.y + d.y, (float) at.z + d.z, size * (0.08f + 0.1f * h2), h1 * 3f,
                    f, 0.95f * f, f, 1, i % 16, 4, Vfx.FULL_BRIGHT);
        }
    }

    // =================================================================================================== the tunnel
    /** S1E7 4.5–10.25 s: a hyperspace tunnel of information rushing past; torn white fragments; the dark behind it. */
    private static void tunnel(DomainEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float t, float G) {
        MatrixStack.Entry en = ms.peek();
        RenderLayer back = VfxLayers.sky(WHITE);
        sphere(vcp.getBuffer(back), en, camRel, 26f, 12, 16, 0f, 0.07f, 0.02f, 0.11f);
        flush(vcp, back);
        float yr = e.getYawDeg() * MathHelper.RADIANS_PER_DEGREE;
        Vector3f axis = new Vector3f(-MathHelper.sin(yr), 0, MathHelper.cos(yr));
        Vector3f side = new Vector3f(axis).cross(0, 1, 0).normalize(), up = new Vector3f(0, 1, 0);
        // it gathers speed into the flash: distance travelled = tau + tau^3 / (2 T^2)
        float tau = Math.max(0f, t - D_TUNNEL) / 20f, span = (D_FLASH - D_TUNNEL) / 20f;
        float tt = D_TUNNEL / 20f + tau + 0.5f * tau * tau * tau / (span * span);
        float ramp = MathHelper.clamp((t - D_TUNNEL) / 10f, 0f, 1f);
        VertexConsumer vc = vcp.getBuffer(VfxLayers.additive("streak"));
        int n = 560;
        float L = 70f;
        for (int i = 0; i < n; i++) {
            float h1 = Vfx.hash(i * 5 + 101), h2 = Vfx.hash(i * 5 + 102), h3 = Vfx.hash(i * 5 + 103), h4 = Vfx.hash(i * 5 + 104);
            float a = h1 * MathHelper.TAU, rho = 1.8f + 16f * h2 * h2;
            float speed = 30f + 50f * h3;
            float s = ((h4 * L - tt * speed) % L + L) % L - L * 0.5f;     // along the axis, flying past (toward -axis)
            float len = (3f + 12f * h3) * (0.4f + 0.6f * ramp);
            float ox = MathHelper.cos(a) * rho, oy = MathHelper.sin(a) * rho;
            float bx = (float) camRel.x + side.x * ox + up.x * oy, by = (float) camRel.y + side.y * ox + up.y * oy, bz = (float) camRel.z + side.z * ox + up.z * oy;
            float[] c = NEON[i % NEON.length];
            float fade = 1f - Math.abs(s) / (L * 0.5f);
            float f = G * fade * ramp * (0.45f + 0.55f * h2);
            Vfx.streak(vc, en, camRel, bx + axis.x * s, by + axis.y * s, bz + axis.z * s,
                    bx + axis.x * (s + len), by + axis.y * (s + len), bz + axis.z * (s + len), 0.05f + 0.1f * h1 * h1,
                    c[0] * f, c[1] * f, c[2] * f, 1);
        }
        // torn white nebula fragments drifting by
        VertexConsumer frag = vcp.getBuffer(VfxLayers.additive("nebula_frags"));
        for (int i = 0; i < 26; i++) {
            float h1 = Vfx.hash(i * 7 + 301), h2 = Vfx.hash(i * 7 + 302), h3 = Vfx.hash(i * 7 + 303);
            float a = h1 * MathHelper.TAU, rho = 4f + 12f * h2;
            float s = ((h3 * 60f - tt * 18f) % 60f + 60f) % 60f - 30f;
            float ox = MathHelper.cos(a) * rho, oy = MathHelper.sin(a) * rho * 0.7f;
            float f = 1.1f * G * ramp * (1f - Math.abs(s) / 30f);
            Vfx.billboardFrame(frag, en, (float) camRel.x + side.x * ox + axis.x * s, (float) camRel.y + oy, (float) camRel.z + side.z * ox + axis.z * s,
                    1.6f + 2.8f * h2, h1 * 3f, f, 0.95f * f, f, 1, i % 16, 4, Vfx.FULL_BRIGHT);
        }
    }

    // =================================================================================================== the void
    private static void interior(DomainEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float t, float G) {
        MatrixStack.Entry en = ms.peek();
        var cfg = ConfigManager.get().client;
        float B = (float) cfg.domainBrightness;
        float open = e.isInstant() ? MathHelper.clamp((t - I_VOID) / 1.5f, 0f, 1f) : MathHelper.clamp((t - D_OPEN) / 4f, 0f, 1f);
        float secs = t / 20f;
        float yr = e.getYawDeg() * MathHelper.RADIANS_PER_DEGREE;
        // the panorama: deep space around you (Blender, Cycles)
        Identifier pano = LoopTextures.frame("void_pano", 0f);
        RenderLayer sky = VfxLayers.sky(pano != null ? pano : WHITE);
        float pc = pano != null ? B : 0.02f;
        sphere(vcp.getBuffer(sky), en, camRel, 60f, 24, 40, yr, pc * open + 0.02f * (1 - open), pc * open + 0.02f * (1 - open), pc * open + 0.04f * (1 - open));
        flush(vcp, sky);
        // the black hole ahead of the caster, high in the void (Blender, Cycles loop)
        if (cfg.domainBlackHole) {
            Vector3f fwd = new Vector3f(-MathHelper.sin(yr), 0, MathHelper.cos(yr));
            float el = 0.21f;                                           // ~12° up
            Vector3f dir = new Vector3f(fwd.x * MathHelper.cos(el), MathHelper.sin(el), fwd.z * MathHelper.cos(el));
            float D = 45f;
            Vector3f n = new Vector3f(dir).negate();                    // faces the viewer
            Vector3f right = new Vector3f(0, 1, 0).cross(n).normalize(), up = new Vector3f(n).cross(right).normalize();
            // in the Cycles frame the hole sits 15.8° left of centre (its radius is 10.4°): shift the frame right
            Vector3f hole = new Vector3f(dir).mul(D);
            Vector3f qc = new Vector3f(hole).add(new Vector3f(right).mul(D * (float) Math.tan(Math.toRadians(15.8))));
            float halfW = D * (float) Math.tan(Math.toRadians(39)), halfH = halfW * 0.5f;
            Vector3f hc = new Vector3f((float) camRel.x, (float) camRel.y, (float) camRel.z);
            Vector3f hP = new Vector3f(hc).add(hole), qP = new Vector3f(hc).add(qc);
            float rd = D * (float) Math.tan(Math.toRadians(10.2));
            quad(vcp.getBuffer(VfxLayers.alpha("dark_disk")), en, hP, right, up, rd * 1.25f, rd * 1.25f, 0f, 0f, 0f, open, 0, 0, 1, 1);
            Identifier hl = LoopTextures.frame("void_hole", secs);
            if (hl != null) quad(vcp.getBuffer(VfxLayers.additive(hl)), en, qP, right, up, halfW, halfH, open * B, open * B, open * B, 1, 0, 0, 1, 1);
        }
        // smoke wisps drifting around you, in the world (Blender volumetric sprites)
        if (cfg.domainWisps) {
            VertexConsumer w = vcp.getBuffer(VfxLayers.additive("void_wisps"));
            float R = e.getRadius();
            for (int i = 0; i < 22; i++) {
                float h1 = Vfx.hash(i * 9 + 501), h2 = Vfx.hash(i * 9 + 502), h3 = Vfx.hash(i * 9 + 503), h4 = Vfx.hash(i * 9 + 504);
                float a = h1 * MathHelper.TAU + secs * (0.02f + 0.04f * h3) * (i % 2 == 0 ? 1 : -1);
                float rr = 9f + (R * 0.85f - 9f) * h2;
                float x = MathHelper.cos(a) * rr, z = MathHelper.sin(a) * rr, y = -1.5f + 9f * h4 + MathHelper.sin(secs * 0.3f + i) * 0.6f;
                float dx = x - (float) camRel.x, dy = y - (float) camRel.y, dz = z - (float) camRel.z;
                float dcam = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                float f = 0.16f * G * B * open * Vfx.smooth(4f, 10f, dcam);
                Vfx.billboardFrame(w, en, x, y, z, 2.5f + 4f * h3, h1 * 6f + secs * 0.05f, 0.55f * f, 0.75f * f, f, 1, i % 16, 4, Vfx.FULL_BRIGHT);
            }
            VertexConsumer sp = vcp.getBuffer(VfxLayers.additive("spark"));
            for (int i = 0; i < 90; i++) {
                float h1 = Vfx.hash(i * 5 + 801), h2 = Vfx.hash(i * 5 + 802), h3 = Vfx.hash(i * 5 + 803);
                float a = h1 * MathHelper.TAU + secs * 0.03f;
                float rr = 2f + R * 0.9f * h2;
                float y = -2f + 12f * h3 + (secs * 0.15f * (0.5f + h2)) % 3f;
                float tw = 0.5f + 0.5f * MathHelper.sin(secs * (1f + 2f * h3) + i);
                float f = 0.5f * tw * G * B * open;
                Vfx.billboard(sp, en, MathHelper.cos(a) * rr, y, MathHelper.sin(a) * rr, 0.05f + 0.07f * h1, 0, 0.6f * f, 0.75f * f, f, 1);
            }
        }
    }

    /** A quad centred at c spanning ±hx along `right` and ±hy along `up`. */
    private static void quad(VertexConsumer vc, MatrixStack.Entry en, Vector3f c, Vector3f right, Vector3f up, float hx, float hy,
                             float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        Vfx.vert(vc, en, c.x - right.x * hx - up.x * hy, c.y - right.y * hx - up.y * hy, c.z - right.z * hx - up.z * hy, r, g, b, a, u0, v1);
        Vfx.vert(vc, en, c.x - right.x * hx + up.x * hy, c.y - right.y * hx + up.y * hy, c.z - right.z * hx + up.z * hy, r, g, b, a, u0, v0);
        Vfx.vert(vc, en, c.x + right.x * hx + up.x * hy, c.y + right.y * hx + up.y * hy, c.z + right.z * hx + up.z * hy, r, g, b, a, u1, v0);
        Vfx.vert(vc, en, c.x + right.x * hx - up.x * hy, c.y + right.y * hx - up.y * hy, c.z + right.z * hx - up.z * hy, r, g, b, a, u1, v1);
    }

    // =================================================================================================== outside
    /** From outside: the barrier, a black dome with a faint violet rim. */
    private static void barrier(DomainEntity e, MatrixStack ms, VertexConsumerProvider vcp, Vec3d camRel, float t) {
        if (!ConfigManager.get().client.domainBarrier) return;
        float k;
        if (e.isInstant()) k = t >= I_VOID && t < I_WIPE ? 1f : 0f;
        else {
            float up = MathHelper.clamp((t - D_INK) / 10f, 0f, 1f);
            float down = 1f - MathHelper.clamp((t - e.getCloseAt()) / 10f, 0f, 1f);
            k = up * down;
        }
        if (k <= 0.01f) return;
        float R = e.getRadius() * (0.3f + 0.7f * k);
        MatrixStack.Entry en = ms.peek();
        RenderLayer l = VfxLayers.sky(WHITE);
        sphere(vcp.getBuffer(l), en, Vec3d.ZERO, R, 24, 36, 0f, 0.012f, 0.008f, 0.02f);
        flush(vcp, l);
        Vfx.fresnelSphere(vcp.getBuffer(VfxLayers.additive("glow_soft")), en, camRel, R * 1.005f, 24, 36, 0.4f * k, 0.18f * k, 0.8f * k, 0f, 0.7f, 4f);
    }
}
