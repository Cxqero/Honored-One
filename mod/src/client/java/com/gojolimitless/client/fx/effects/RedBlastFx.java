package com.gojolimitless.client.fx.effects;

import com.gojolimitless.client.fx.FxContext;
import com.gojolimitless.client.fx.TransientFx;
import com.gojolimitless.client.render.RenderPath;
import com.gojolimitless.client.render.Vfx;
import com.gojolimitless.client.render.VfxLayers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Red's repulsion blast: a white-red flash, a hot core that balloons out, a spherical shock front, a ground ring,
 * radial blast streaks and billowing volumetric dust (Blender-rendered flipbook).
 */
public class RedBlastFx implements TransientFx {
    private final Vec3d pos;
    private final float R, pw;
    private final int life;
    private int age;
    private final float[][] puffs;

    public RedBlastFx(Vec3d pos, float radius, float power) {
        this.pos = pos; this.R = radius; this.pw = power;
        this.life = (int) (46 + radius * 1.5f);
        int n = (int) (10 + radius * 1.2f);
        puffs = new float[n][];
        for (int i = 0; i < n; i++) {
            float a = Vfx.hash(i * 3 + 7) * MathHelper.TAU;
            float d = 0.35f + 0.65f * Vfx.hash(i * 3 + 8);
            float up = Vfx.hash(i * 3 + 9);
            // direction, speed, size, start delay, roll
            puffs[i] = new float[]{MathHelper.cos(a) * d, up * 0.45f - 0.05f, MathHelper.sin(a) * d, 0.7f + 0.6f * Vfx.hash(i + 99),
                    0.6f + 0.5f * Vfx.hash(i + 55), Vfx.hash(i + 77) * 4f, Vfx.hash(i + 33) * 6.28f};
        }
    }

    @Override public boolean tick() { return ++age < life; }

    @Override
    public void render(FxContext c) {
        float tt = age + c.tickDelta();
        float k = MathHelper.clamp(tt / life, 0f, 1f);
        float G = RenderPath.glowScale();
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        var e = c.matrices().peek();
        Vec3d camRel = c.camRel(pos);

        // 1. flash
        float fk = MathHelper.clamp(tt / 6f, 0f, 1f);
        float flash = (fk < 0.2f ? fk / 0.2f : (1f - fk) / 0.8f) * 1.3f * G;
        if (flash > 0) {
            VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.billboard(vc, e, x, y, z, R * 5.5f, 0, flash, 0.35f * flash, 0.2f * flash, 1);
            vc = c.consumers().getBuffer(VfxLayers.additive("flash_star"));
            Vfx.billboard(vc, e, x, y, z, R * 3.5f, 0, flash, 0.75f * flash, 0.65f * flash, 1);
        }
        // 2. fireball core expanding then cooling
        float ck = MathHelper.clamp(tt / 14f, 0f, 1f);
        float core = (1f - ck) * (1f - ck) * G;
        if (core > 0) {
            VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("red_swirl"));
            Vfx.billboard(vc, e, x, y, z, R * (0.6f + 1.4f * ck), tt * 0.2f, core, core, core, 1);
            vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.billboard(vc, e, x, y, z, R * (0.8f + 1.2f * ck), 0, core, 0.4f * core, 0.15f * core, 1);
        }
        // 3. shock front (sphere) + ground ring
        float sk = (float) Math.pow(MathHelper.clamp(tt / 18f, 0f, 1f), 0.55);
        float sf = (1f - MathHelper.clamp(tt / 18f, 0f, 1f));
        sf = sf * sf * G;
        if (sf > 0) {
            e.getPositionMatrix();
            c.matrices().push();
            c.matrices().translate(x, y, z);
            VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.fresnelSphere(vc, c.matrices().peek(), camRel, R * (0.3f + 2.4f * sk), 16, 24, 1.0f * sf, 0.45f * sf, 0.2f * sf, 0f, 0.9f, 3f);
            c.matrices().pop();
            vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
            Vfx.planeQuad(vc, e, x, y - R * 0.15f, z, new Vector3f(0, 1, 0), R * (0.4f + 3.6f * sk), 0, 0.9f * sf, 0.35f * sf, 0.2f * sf, 1);
        }
        // 4. radial blast streaks
        float bk = MathHelper.clamp(tt / 12f, 0f, 1f);
        if (bk < 1f) {
            VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("streak"));
            int n = 36 + (int) (R * 2);
            for (int i = 0; i < n; i++) {
                float h1 = Vfx.hash(i * 11 + 1), h2 = Vfx.hash(i * 11 + 2), h3 = Vfx.hash(i * 11 + 3);
                Vector3f d = new Vector3f(h1 - 0.5f, (h2 - 0.3f) * 0.9f, h3 - 0.5f).normalize();
                float head = R * (0.3f + 3.2f * (float) Math.pow(bk, 0.6)) * (0.6f + 0.4f * h2);
                float tail = head * 0.55f;
                float f = (1f - bk) * G * 0.9f;
                Vfx.streak(vc, e, camRel, x + d.x * tail, y + d.y * tail, z + d.z * tail, x + d.x * head, y + d.y * head, z + d.z * head,
                        0.08f + R * 0.02f, f, 0.3f * f, 0.15f * f, 1);
            }
        }
        // 5. volumetric dust (lit by the world)
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.lit("smoke_puff"));
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos.Mutable bp = new BlockPos.Mutable();
        for (float[] p : puffs) {
            float delay = p[5];
            float lk = MathHelper.clamp((tt - delay) / (life - delay), 0f, 1f);
            if (lk <= 0f) continue;
            float travel = R * (0.5f + 2.2f * p[3] * (1f - (1f - lk) * (1f - lk)));
            float px = x + p[0] * travel, py = y + p[1] * travel + lk * R * 0.25f, pz = z + p[2] * travel;
            int frame = Math.min(31, (int) (lk * 32));
            float size = R * p[4] * (0.5f + 0.9f * lk);
            float a = Math.min(1f, lk * 6f) * (1f - lk * lk) * 0.72f;
            // hot at first, then dusty
            float heat = MathHelper.clamp(1f - lk * 5f, 0f, 1f);
            float r = MathHelper.lerp(heat, 0.62f, 1.0f), g = MathHelper.lerp(heat, 0.56f, 0.45f), b = MathHelper.lerp(heat, 0.5f, 0.3f);
            bp.set(pos.x + p[0] * travel, pos.y + p[1] * travel + 1, pos.z + p[2] * travel);
            int light = mc.world != null ? WorldRenderer.getLightmapCoordinates(mc.world, bp) : Vfx.FULL_BRIGHT;
            Vfx.billboardFrame(vc, e, px, py, pz, size, p[6] + lk * 0.6f, r, g, b, a, frame, 6, light);
        }
    }
}
