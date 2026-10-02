package com.gojolimitless.client.fx.effects;

import com.gojolimitless.client.fx.FxContext;
import com.gojolimitless.client.fx.TransientFx;
import com.gojolimitless.client.render.Lightning;
import com.gojolimitless.client.render.RenderPath;
import com.gojolimitless.client.render.Vfx;
import com.gojolimitless.client.render.VfxLayers;
import com.gojolimitless.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Hollow Purple's explosion. Where the mass first strikes something (tap and 200%, which then flies on), and the nuke's
 * detonation, much bigger, rising into a column with a cap.
 * <ol>
 *   <li>a white-violet flash</li>
 *   <li>a roiling fireball of turbulent purple plasma that swells, then cools into dark violet smoke glowing from inside</li>
 *   <li>a shock sphere, and a ring racing along the ground pushing a wall of dust</li>
 *   <li>radial blast streaks, lightning through the fireball, embers thrown out</li>
 *   <li>smoke that climbs and lingers</li>
 * </ol>
 */
public class PurpleBlastFx implements TransientFx {
    private static final int PLASMA_FRAMES = 32, PLASMA_GRID = 6, SMOKE_FRAMES = 32, SMOKE_GRID = 6;

    private final Vec3d pos;
    private final float R, groundOff, T;
    private final boolean nuke;
    private final int life, lobes, dust, embers, seed;
    private int age;

    /**
     * @param radius  fireball radius at full size
     * @param groundY the ground under the blast (for the ring and the dust wall)
     * @param nuke    the nuke's detonation: slower, bigger, with a rising column and cap
     */
    public PurpleBlastFx(Vec3d pos, float radius, double groundY, boolean nuke) {
        this.pos = pos;
        this.R = Math.max(1.5f, radius);
        this.nuke = nuke;
        this.groundOff = (float) (groundY - pos.y);
        this.T = nuke ? 1f : MathHelper.clamp(0.35f + R * 0.02f, 0.35f, 0.6f);
        int q = ConfigManager.get().client.quality.ordinal();
        this.life = nuke ? 300 : (int) (70 + R * 3);
        this.lobes = nuke ? 44 + q * 14 : 14 + q * 4 + (int) Math.min(12, R);
        this.dust = nuke ? 96 + q * 24 : 18 + q * 4 + (int) Math.min(16, R * 1.5f);
        this.embers = nuke ? 120 + q * 30 : 30 + q * 10;
        this.seed = (int) (pos.x * 31 + pos.z * 17 + pos.y * 7);
    }

    private boolean shockHit;

    @Override
    public boolean tick() {
        age++;
        if (nuke && !shockHit) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Vec3d cam = mc.gameRenderer.getCamera().getPos();
            double d = Math.hypot(cam.x - pos.x, cam.z - pos.z);
            if (d <= com.gojolimitless.entity.NukeEntity.shockRadius(R, age) && d < R * 6.5) {
                shockHit = true;
                float k = (float) Math.max(0.25, 1 - d / (R * 6.5));
                com.gojolimitless.client.cam.CameraShake.add(1.4f * k, cam, 60);
                com.gojolimitless.client.hud.HudOverlay.flash(0.18f * k, 1f, 0.92f, 1f);
                mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(com.gojolimitless.registry.ModSounds.NUKE_SHOCKWAVE, 1f, 0.6f + 0.4f * k));
            }
        }
        return age < life;
    }

    private float h(int i) { return Vfx.hash(seed + i); }

    @Override
    public void render(FxContext c) {
        float tt = age + c.tickDelta();
        float G = RenderPath.glowScale();
        MatrixStack.Entry e = c.matrices().peek();
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        Vec3d camHost = c.camera().subtract(c.origin());

        float grow = 22f * T, hot = 44f * T, cool = 130f * T;
        float g = MathHelper.clamp(tt / grow, 0f, 1f);
        g = 1f - (1f - g) * (1f - g) * (1f - g);
        float heat = 1f - Vfx.smooth(hot * 0.5f, cool, tt);
        float gone = 1f - Vfx.smooth(life * 0.62f, life, tt);
        // the nuke's fireball climbs on its column and spreads into a cap
        float climb = nuke ? R * 1.1f * Vfx.smooth(grow * 0.6f, life * 0.8f, tt) : R * 0.5f * Vfx.smooth(grow, life, tt);
        float fr = R * (0.12f + 0.88f * g) * (1f + 0.18f * Vfx.smooth(grow, cool, tt));
        float cx = x, cy = y + climb, cz = z;
        float flat = nuke ? 1f - 0.35f * Vfx.smooth(grow, cool * 1.3f, tt) : 1f;       // cap: squashes as it climbs
        float wide = nuke ? 1f + 0.35f * Vfx.smooth(grow, cool * 1.3f, tt) : 1f;

        // ---- smoke (drawn first: the glow burns inside it)
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.alpha("smoke_puff"));
        float smokeIn = Vfx.smooth(hot * 0.12f, cool * 0.7f, tt) * gone;       // early, so the fire has something to burn against
        if (smokeIn > 0.01f) {
            for (int i = 0; i < lobes; i++) {
                Vector3f d = dir(i);
                float rr = fr * (nuke ? 0.45f + 0.75f * h(i * 7 + 3) : (0.55f + 0.4f * h(i * 7 + 3)) * 1.08f);
                float px = cx + d.x * rr * wide, py = cy + d.y * rr * flat + (tt - hot) * 0.012f * R * h(i * 7 + 4), pz = cz + d.z * rr * wide;
                float size = fr * (nuke ? 0.75f : 0.62f + 0.0f + 0.35f * h(i * 7 + 5)) * (1f + 0.25f * Vfx.smooth(hot, life, tt)) * (nuke ? 1f + 0.35f * h(i * 7 + 5) : 1f);
                int frame = Math.min(SMOKE_FRAMES - 1, 10 + (int) (Vfx.smooth(0, life, tt) * 21));
                float glow = heat * (0.5f + 0.5f * Math.max(0f, -d.y));             // lit from the fire below
                float near = near(camHost, px, py, pz, size);
                float r = MathHelper.lerp(glow, 0.13f, 0.5f), gg = MathHelper.lerp(glow, 0.09f, 0.16f), b = MathHelper.lerp(glow, 0.17f, 0.66f);
                Vfx.billboardFrame(vc, e, px, py, pz, size, h(i * 7 + 6) * 6.28f + tt * 0.002f, r, gg, b, 0.8f * smokeIn * near,
                        frame, SMOKE_GRID, Vfx.FULL_BRIGHT);
            }
            if (nuke) {
                // the column: from the ground up into the cap
                float top = cy - fr * 0.5f, bottom = y + Math.max(groundOff, -R * 2f);
                int n = 18;
                for (int j = 0; j < n; j++) {
                    float k = (j + 0.5f) / n;
                    float sy = MathHelper.lerp(k, bottom, top);
                    float wob = R * 0.08f * MathHelper.sin(k * 9f + h(j) * 6f);
                    float size = R * (0.42f + 0.18f * h(j + 200)) * (1f - 0.35f * k) * (0.6f + 0.4f * Vfx.smooth(grow, cool, tt));
                    float glow = heat * (1f - k) * 0.8f;
                    Vfx.billboardFrame(vc, e, x + wob, sy, z + wob * 0.6f, size, h(j + 300) * 6.28f,
                            MathHelper.lerp(glow, 0.15f, 0.55f), MathHelper.lerp(glow, 0.1f, 0.18f), MathHelper.lerp(glow, 0.18f, 0.7f),
                            0.75f * smokeIn * Vfx.smooth(grow * 0.8f, grow * 2.5f, tt) * near(camHost, x + wob, sy, z + wob * 0.6f, size),
                            14 + j % 12, SMOKE_GRID, Vfx.FULL_BRIGHT);
                }
            }
        }

        // ---- the fireball
        if (heat > 0.01f) {
            float F = heat * G * gone * (nuke ? 0.3f + 0.7f * (float) Math.exp(-tt / 30f) : 1f);
            vc = c.consumers().getBuffer(VfxLayers.additive("purple_plasma"));
            for (int i = 0; i < lobes; i++) {
                Vector3f d = dir(i);
                float rr = fr * (nuke ? 0.2f + 0.85f * h(i * 7 + 3) : 0.3f + 0.45f * h(i * 7 + 3));
                float size = fr * (nuke ? 0.45f + 0.8f * h(i * 7 + 5) * h(i * 7 + 8) + 0.2f : 0.7f + 0.45f * h(i * 7 + 5));
                int frame = ((int) (tt * 0.7f) + i * 5) % PLASMA_FRAMES;
                float w = 0.3f + 0.7f * h(i * 7 + 1);
                float white = Math.max(0f, 1f - tt / (grow * 0.9f));                 // white-hot at first
                float r = MathHelper.lerp(white, 0.62f, 1f) * w * (0.55f + 0.45f * heat), gg = MathHelper.lerp(white, 0.13f, 0.7f) * w * heat, b = w;
                float px = cx + d.x * rr * wide, py = cy + d.y * rr * flat, pz = cz + d.z * rr * wide;
                float nf = (0.45f + 0.2f * white) * F * near(camHost, px, py, pz, size);
                Vfx.billboardFrame(vc, e, px, py, pz, size, h(i * 7 + 2) * 6.28f + tt * 0.01f,
                        nf * r, nf * gg, nf * b, 1, frame, PLASMA_GRID, Vfx.FULL_BRIGHT);
            }
            vc = c.consumers().getBuffer(VfxLayers.additive("purple_core"));
            float core = F * (0.35f + 0.7f * (float) Math.exp(-tt / (8f * T)));
            Vfx.billboard(vc, e, cx, cy, cz, fr * 1.15f, 0, 0.9f * core, 0.6f * core, core, 1);
            vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.billboard(vc, e, cx, cy, cz, fr * 3.2f, 0, 0.35f * F, 0.06f * F, 0.45f * F, 1);
            c.matrices().push();
            c.matrices().translate(cx, cy, cz);
            Vec3d camRel = camHost.subtract(cx, cy, cz);
            Vfx.fresnelSphere(vc, c.matrices().peek(), camRel, fr * 1.02f, 18, 26, 0.9f * F, 0.25f * F, F, 0f, 0.7f, 2.6f);
            c.matrices().pop();
            // lightning through it
            int bolts = nuke ? 8 : 3;
            long tick = (long) (tt / 2f);
            for (int i = 0; i < bolts; i++) {
                long s = tick * 41 + i * 1013 + seed;
                Vector3f a = new Vector3f(Vfx.hash((int) s) - 0.5f, Vfx.hash((int) s + 1) - 0.5f, Vfx.hash((int) s + 2) - 0.5f).normalize().mul(fr * 0.2f);
                Vector3f b = new Vector3f(Vfx.hash((int) s + 3) - 0.5f, Vfx.hash((int) s + 4) - 0.5f, Vfx.hash((int) s + 5) - 0.5f).normalize().mul(fr * 1.15f);
                float[] pts = Lightning.bolt(cx + a.x, cy + a.y, cz + a.z, cx + b.x, cy + b.y, cz + b.z, s, 4, 0.3f);
                Lightning.draw(e, c.consumers(), camHost, pts, 0.06f + fr * 0.008f, 0.95f, 0.5f, 1f, F * (0.5f + 0.5f * Vfx.hash((int) s + 6)));
            }
        }

        // ---- the flash
        float fk = tt / (7f * T);
        if (fk < 1f) {
            float f = (fk < 0.15f ? fk / 0.15f : (1f - fk) / 0.85f) * 1.4f * G;
            vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            float fs = nuke ? 3f : 5f;
            Vfx.billboard(vc, e, x, y, z, R * fs, 0, f, 0.75f * f, f, 1);
            vc = c.consumers().getBuffer(VfxLayers.additive("flash_star"));
            Vfx.billboard(vc, e, x, y, z, R * fs * 0.75f, 0.4f, f, 0.65f * f, f, 1);
        }

        // ---- shock sphere and the ring along the ground
        float sk = MathHelper.clamp(tt / (34f * T), 0f, 1f);
        if (sk < 1f) {
            float sf = (1f - sk) * (1f - sk) * G;
            float sr = R * (0.4f + 2.4f * (float) Math.pow(sk, 0.55));
            if (nuke) { sf = 0f; }                            // the nuke's shell rides the shock front instead (below)
            vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            c.matrices().push();
            c.matrices().translate(x, y, z);
            Vfx.fresnelSphere(vc, c.matrices().peek(), camHost.subtract(x, y, z), sr, 18, 26, 0.85f * sf, 0.6f * sf, sf, 0f, 0.55f, 4.5f);
            c.matrices().pop();
        }
        boolean grounded = groundOff > -R * (nuke ? 3f : 2.2f);
        float gk, ringR;
        if (nuke) {
            float bR = R / 1.0f;
            ringR = (float) com.gojolimitless.entity.NukeEntity.shockRadius(bR, tt);
            gk = MathHelper.clamp(ringR / (bR * 6.5f), 0f, 1f);
            gk = MathHelper.clamp((gk - 0.08f) / 0.92f, 0f, 1f);
            float life = Math.min(1f, tt / 3f) * (float) Math.pow(1f - gk, 0.8);
            // (the shell fades before it reaches the camera: from inside it would only draw stray arcs)
            float camD = (float) Math.sqrt(camHost.squaredDistanceTo(x, y, z));
            float shellK = life * (1f - Vfx.smooth(0.55f, 0.85f, ringR / Math.max(1f, camD)));
            // the condensation shell racing out through the air
            if (life > 0.01f) {
                vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
                c.matrices().push();
                c.matrices().translate(x, y, z);
                float sh = 0.55f * shellK * G;
                Vfx.fresnelSphere(vc, c.matrices().peek(), camHost.subtract(x, y, z), ringR * 0.92f, 20, 30, 0.9f * sh, 0.85f * sh, sh, 0f, 0.6f, 7f);
                c.matrices().pop();
                // the pressure flash on the ground under it
                float pk = MathHelper.clamp(tt / 14f, 0f, 1f);
                float pf = (1f - pk) * (1f - pk) * G * 0.25f;
                if (pf > 0.01f && grounded) Vfx.planeQuad(vc, e, x, y + groundOff + 0.3f, z, new Vector3f(0, 1, 0), R * (1.2f + 1.3f * pk), 0, pf, 0.8f * pf, pf, 1);
                if (grounded) {
                    vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
                    float rf = life * G * 0.35f;                  // (seen from above, bright rings read as stray arcs)
                    Vfx.planeQuad(vc, e, x, y + groundOff + 0.4f, z, new Vector3f(0, 1, 0), ringR, tt * 0.01f, 0.75f * rf, 0.3f * rf, rf, 1);
                }
            }
        } else {
            gk = MathHelper.clamp(tt / (58f * T), 0f, 1f);
            ringR = R * (0.5f + 3.4f * (float) Math.pow(gk, 0.5));
            if (grounded && gk < 1f) {
                float rf = (float) Math.pow(1f - gk, 1.5) * G;
                vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
                Vfx.planeQuad(vc, e, x, y + groundOff + 0.4f, z, new Vector3f(0, 1, 0), ringR, tt * 0.01f, 0.8f * rf, 0.35f * rf, rf, 1);
            }
        }

        // ---- radial streaks
        float bk = tt / (14f * T);
        if (bk < 1f) {
            vc = c.consumers().getBuffer(VfxLayers.additive("streak"));
            int n = nuke ? 90 : 30 + (int) R;
            for (int i = 0; i < n; i++) {
                Vector3f d = new Vector3f(h(i * 5 + 900) - 0.5f, (h(i * 5 + 901) - 0.35f) * 0.9f, h(i * 5 + 902) - 0.5f).normalize();
                float head = R * (0.3f + 3.4f * (float) Math.pow(bk, 0.6)) * (0.55f + 0.45f * h(i * 5 + 903));
                float tail = head * 0.5f;
                float f = (1f - bk) * G;
                Vfx.streak(vc, e, camHost, x + d.x * tail, y + d.y * tail, z + d.z * tail, x + d.x * head, y + d.y * head, z + d.z * head,
                        0.08f + R * 0.02f, 0.85f * f, 0.35f * f, f, 1);
            }
        }

        // ---- embers thrown out, falling
        float ek = tt / (60f * T);
        if (ek < 1f) {
            vc = c.consumers().getBuffer(VfxLayers.additive("spark"));
            float secs = tt / 20f;
            for (int i = 0; i < embers; i++) {
                Vector3f d = new Vector3f(h(i * 3 + 500) - 0.5f, h(i * 3 + 501) * 0.9f - 0.15f, h(i * 3 + 502) - 0.5f).normalize();
                float v = R * (0.9f + 1.6f * h(i * 3 + 503)) / Math.max(0.4f, T);
                float px = x + d.x * v * secs, py = y + d.y * v * secs - 9.8f * secs * secs * (nuke ? 1.2f : 0.8f), pz = z + d.z * v * secs;
                float f = (1f - ek) * G * (0.5f + 0.5f * h(i * 3 + 504));
                Vfx.billboard(vc, e, px, py, pz, 0.12f + R * 0.012f, 0, f, 0.45f * f, f, 1);
            }
        }

        // ---- the dust wall pushed along the ground by the ring
        if (grounded && gk < 1f) {
            vc = c.consumers().getBuffer(VfxLayers.lit("smoke_puff"));
            MinecraftClient mc = MinecraftClient.getInstance();
            BlockPos.Mutable bp = new BlockPos.Mutable();
            int tiers = nuke ? 2 : 1;
            for (int i = 0; i < dust * tiers; i++) {
                int tier = i / dust;
                float a = (i + h(i + 700) * 0.6f) / dust * MathHelper.TAU;
                float rr = ringR * (0.88f + 0.12f * h(i + 701)) * (tier == 1 ? 0.95f : 1f);
                float size = nuke ? R * (0.4f + 0.35f * h(i + 702)) * (0.55f + 0.6f * gk) * (tier == 1 ? 0.8f : 1f)
                        : R * (0.35f + 0.3f * h(i + 702)) * (0.5f + 0.9f * gk) * 0.9f;
                float px = x + MathHelper.cos(a) * rr, pz = z + MathHelper.sin(a) * rr, py = y + groundOff + size * (tier == 1 ? 0.95f : 0.3f);
                float al = (nuke ? Math.min(1f, tt / 4f) * (float) Math.pow(1f - gk, 0.9) * (tier == 1 ? 0.5f : 0.85f)
                        : Math.min(1f, gk * 9f) * (float) Math.pow(1f - gk, 1.2) * 0.75f) * near(camHost, px, py, pz, size);
                int frame = Math.min(SMOKE_FRAMES - 1, (int) (gk * 28));
                bp.set(pos.x + MathHelper.cos(a) * rr, pos.y + groundOff + 1, pos.z + MathHelper.sin(a) * rr);
                int light = mc.world != null ? WorldRenderer.getLightmapCoordinates(mc.world, bp) : Vfx.FULL_BRIGHT;
                Vfx.billboardFrame(vc, e, px, py, pz, size, h(i + 703) * 6.28f, 0.62f, 0.55f, 0.6f, al, frame, SMOKE_GRID, light);
            }
        }
    }

    /** 0 at the camera, 1 once a puff of this size is clear of it: nothing swallows the view from inside. */
    private static float near(Vec3d cam, float px, float py, float pz, float size) {
        double d = Math.sqrt((cam.x - px) * (cam.x - px) + (cam.y - py) * (cam.y - py) + (cam.z - pz) * (cam.z - pz));
        return Vfx.smooth(size * 0.35f, size * 1.1f, (float) d);
    }

    /** Lobe directions: an even spread over the sphere (slightly more above than below). */
    private Vector3f dir(int i) {
        float k = (i + 0.5f) / lobes;
        float yy = 1f - 2f * k;
        float r = (float) Math.sqrt(Math.max(0f, 1f - yy * yy));
        float a = i * 2.39996f + h(i * 7) * 0.8f;
        return new Vector3f(MathHelper.cos(a) * r, yy * 0.85f + 0.1f, MathHelper.sin(a) * r);
    }
}
