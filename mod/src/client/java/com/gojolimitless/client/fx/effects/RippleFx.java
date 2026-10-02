package com.gojolimitless.client.fx.effects;

import com.gojolimitless.client.fx.FxContext;
import com.gojolimitless.client.fx.TransientFx;
import com.gojolimitless.client.render.Vfx;
import com.gojolimitless.client.render.VfxLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Infinity: a faint ripple in space where something was stopped. */
public class RippleFx implements TransientFx {
    private final Vec3d pos;
    private final float size;
    private int age;
    private static final int LIFE = 16;

    public RippleFx(Vec3d pos, float size) { this.pos = pos; this.size = size; }

    @Override public boolean tick() { return ++age < LIFE; }

    @Override
    public void render(FxContext c) {
        float k = MathHelper.clamp((age + c.tickDelta()) / LIFE, 0, 1);
        float f = (1 - k) * 0.45f;
        var e = c.matrices().peek();
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        Vfx.billboard(vc, e, x, y, z, size * (0.15f + 0.85f * (float) Math.sqrt(k)), 0, 0.75f * f, 0.9f * f, f, 1);
        Vfx.billboard(vc, e, x, y, z, size * (0.1f + 0.5f * k), 0, 0.4f * f, 0.5f * f, 0.6f * f, 1);
    }
}
