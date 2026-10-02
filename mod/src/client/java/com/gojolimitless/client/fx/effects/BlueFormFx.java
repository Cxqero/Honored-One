package com.gojolimitless.client.fx.effects;

import com.gojolimitless.client.fx.FxContext;
import com.gojolimitless.client.fx.TransientFx;
import com.gojolimitless.client.render.RenderPath;
import com.gojolimitless.client.render.Vfx;
import com.gojolimitless.client.render.VfxLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Space snapping inward as Blue forms: a contracting ring and a star glint. */
public class BlueFormFx implements TransientFx {
    private final Vec3d pos;
    private final float r;
    private int age;
    private static final int LIFE = 12;

    public BlueFormFx(Vec3d pos, float r) { this.pos = pos; this.r = r; }

    @Override public boolean tick() { return ++age < LIFE; }

    @Override
    public void render(FxContext c) {
        float k = MathHelper.clamp((age + c.tickDelta()) / LIFE, 0, 1);
        float G = RenderPath.glowScale();
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        var e = c.matrices().peek();
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
        float ringK = 1 - (float) Math.pow(k, 0.6);
        float f = (float) Math.sin(k * Math.PI) * 0.55f * G;
        Vfx.billboard(vc, e, x, y, z, r * (0.6f + 4.5f * ringK), 0, 0.3f * f, 0.7f * f, 1f * f, 1);
        vc = c.consumers().getBuffer(VfxLayers.additive("flash_star"));
        float sf = (1 - k) * (1 - k) * 1.2f * G;
        Vfx.billboard(vc, e, x, y, z, r * 3.2f, 0.785f * k, 0.7f * sf, 0.9f * sf, 1f * sf, 1);
    }
}
