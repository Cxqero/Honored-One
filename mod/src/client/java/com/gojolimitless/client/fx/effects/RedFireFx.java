package com.gojolimitless.client.fx.effects;

import com.gojolimitless.client.fx.FxContext;
import com.gojolimitless.client.fx.TransientFx;
import com.gojolimitless.client.render.RenderPath;
import com.gojolimitless.client.render.Vfx;
import com.gojolimitless.client.render.VfxLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Muzzle flash when Red leaves the fingertip (bigger for the chanted version). */
public class RedFireFx implements TransientFx {
    private final Vec3d pos;
    private final float pw;
    private int age;

    public RedFireFx(Vec3d pos, float power) { this.pos = pos; this.pw = power; }

    @Override public boolean tick() { return ++age < 8; }

    @Override
    public void render(FxContext c) {
        float k = MathHelper.clamp((age + c.tickDelta()) / 8f, 0f, 1f);
        float f = (1f - k) * (1f - k) * RenderPath.glowScale() * (0.8f + 0.8f * pw);
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        var e = c.matrices().peek();
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("flash_star"));
        Vfx.billboard(vc, e, x, y, z, 1.2f + 3f * pw, 0.4f, f, 0.5f * f, 0.4f * f, 1);
        vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
        Vfx.billboard(vc, e, x, y, z, (0.5f + 4f * pw) * (0.3f + k), 0, f, 0.2f * f, 0.1f * f, 1);
    }
}
