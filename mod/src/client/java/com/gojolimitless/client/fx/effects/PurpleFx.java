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

/** The moment the infinities collide (COLLIDE) and the moment the mass rushes forth (LAUNCH). */
public class PurpleFx implements TransientFx {
    public enum Kind { COLLIDE, LAUNCH }

    private final Vec3d pos;
    private final float R, pw;
    private final Kind kind;
    private final int life;
    private int age;

    public PurpleFx(Kind kind, Vec3d pos, float radius, float power) {
        this.kind = kind; this.pos = pos; this.R = radius; this.pw = power;
        this.life = kind == Kind.COLLIDE ? 18 : 24;
    }

    @Override public boolean tick() { return ++age < life; }

    @Override
    public void render(FxContext c) {
        float tt = age + c.tickDelta();
        float k = MathHelper.clamp(tt / life, 0f, 1f);
        float G = RenderPath.glowScale();
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        var e = c.matrices().peek();
        float fk = MathHelper.clamp(tt / 5f, 0, 1);
        float flash = (fk < 0.2f ? fk / 0.2f : (1f - fk) / 0.8f) * G * (kind == Kind.COLLIDE ? 1.5f : 1.0f);
        if (flash > 0) {
            VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.billboard(vc, e, x, y, z, R * (kind == Kind.COLLIDE ? 7f : 4f), 0, 0.9f * flash, 0.7f * flash, flash, 1);
            vc = c.consumers().getBuffer(VfxLayers.additive("flash_star"));
            Vfx.billboard(vc, e, x, y, z, R * (kind == Kind.COLLIDE ? 5.5f : 3f), 0, flash, 0.85f * flash, flash, 1);
            Vfx.billboard(vc, e, x, y, z, R * 3.5f, 0.785f, 0.7f * flash, 0.4f * flash, flash, 1);
        }
        float rf = (1 - k) * (1 - k) * G;
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
        float ringR = R * (0.5f + (kind == Kind.COLLIDE ? 5f : 3.5f) * (float) Math.sqrt(k));
        Vfx.billboard(vc, e, x, y, z, ringR, 0, 0.75f * rf, 0.35f * rf, rf, 1);
        if (kind == Kind.LAUNCH) {
            Vfx.planeQuad(vc, e, x, y - R * 0.5f, z, new Vector3f(0, 1, 0), R * (0.6f + 5f * (float) Math.sqrt(k)), 0, 0.6f * rf, 0.25f * rf, rf, 1);
        }
    }
}
