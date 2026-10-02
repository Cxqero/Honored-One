package com.gojolimitless.client.fx.effects;

import com.gojolimitless.client.fx.FxContext;
import com.gojolimitless.client.fx.TransientFx;
import com.gojolimitless.client.render.RenderPath;
import com.gojolimitless.client.render.Vfx;
import com.gojolimitless.client.render.VfxLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Maximum Output let go over the caster's head: after the collapse, what's left of Blue disperses. Streams of blue
 * light spread out and thin away, glittering motes drift out and sink, and a soft glow fades.
 */
public class BlueDisperseFx implements TransientFx {
    private static final int LIFE = 64, DELAY = 3;
    private final Vec3d pos;
    private final float r;
    private int age;

    public BlueDisperseFx(Vec3d pos, float r) { this.pos = pos; this.r = Math.max(1f, r); }

    @Override public boolean tick() { return ++age < LIFE; }

    @Override
    public void render(FxContext c) {
        float tt = age + c.tickDelta() - DELAY;
        if (tt <= 0f) return;
        float k = MathHelper.clamp(tt / (LIFE - DELAY), 0f, 1f);
        float G = RenderPath.glowScale();
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        var e = c.matrices().peek();
        Vec3d camHost = c.camera().subtract(c.origin());
        float fade = (1f - k) * (1f - k);

        // the glow of the released space, fading
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
        float gf = 0.55f * fade * G;
        Vfx.billboard(vc, e, x, y, z, r * (3.5f + 5f * k), 0, 0.25f * gf, 0.55f * gf, gf, 1);

        // streams of light spreading out and thinning
        vc = c.consumers().getBuffer(VfxLayers.additive("streak"));
        for (int i = 0; i < 44; i++) {
            float h1 = Vfx.hash(i * 5 + 4001), h2 = Vfx.hash(i * 5 + 4002), h3 = Vfx.hash(i * 5 + 4003);
            Vector3f d = new Vector3f(h1 - 0.5f, h2 * 0.8f - 0.2f, h3 - 0.5f).normalize();
            float head = r * (0.6f + 4.2f * (float) Math.pow(k, 0.55)) * (0.6f + 0.4f * Vfx.hash(i * 5 + 4004));
            float tail = head * (0.45f + 0.3f * k);
            float f = fade * G * (0.5f + 0.5f * Vfx.hash(i * 5 + 4005));
            Vfx.streak(vc, e, camHost, x + d.x * tail, y + d.y * tail, z + d.z * tail, x + d.x * head, y + d.y * head, z + d.z * head,
                    (0.06f + 0.035f * r) * (1f - 0.5f * k), 0.3f * f, 0.65f * f, f, 1);
        }

        // glittering motes: out, then sinking
        vc = c.consumers().getBuffer(VfxLayers.additive("spark"));
        for (int i = 0; i < 90; i++) {
            float h1 = Vfx.hash(i * 3 + 5001), h2 = Vfx.hash(i * 3 + 5002), h3 = Vfx.hash(i * 3 + 5003);
            Vector3f d = new Vector3f(h1 - 0.5f, h2 - 0.4f, h3 - 0.5f).normalize();
            float out = r * (0.8f + 4.5f * (float) Math.pow(k, 0.5)) * (0.5f + 0.5f * h2);
            float sink = r * 1.6f * k * k;
            float tw = 0.55f + 0.45f * MathHelper.sin(tt * (0.5f + 0.4f * h3) + i);
            float f = (1f - k) * tw * G;
            Vfx.billboard(vc, e, x + d.x * out, y + d.y * out - sink, z + d.z * out, 0.08f + 0.03f * r, 0, 0.45f * f, 0.75f * f, f, 1);
        }
    }
}
