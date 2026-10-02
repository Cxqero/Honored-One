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
 * Blue's end: everything is crushed into a point, then the released space rebounds —
 * a hard star flash, a spherical shock ring and a ground-hugging ring.
 */
public class BlueCollapseFx implements TransientFx {
    private final Vec3d pos;
    private final float r;
    private int age;
    private static final int LIFE = 26;

    public BlueCollapseFx(Vec3d pos, float r) { this.pos = pos; this.r = r; }

    @Override public boolean tick() { return ++age < LIFE; }

    @Override
    public void render(FxContext c) {
        float tt = age + c.tickDelta();
        float k = MathHelper.clamp(tt / LIFE, 0, 1);
        float G = RenderPath.glowScale();
        float x = c.lx(pos), y = c.ly(pos), z = c.lz(pos);
        var e = c.matrices().peek();

        // flash: white-blue star that blooms for ~4 ticks. Sized for watching from afar (the glow spans ~18 radii); when
        // it goes off near the camera (Maximum Output let go overhead) it would white out the whole view, so up close
        // it shrinks to stay inside the view and dims (the HUD flash still marks the moment)
        float fk = MathHelper.clamp(tt / 5f, 0, 1);
        float dist = (float) c.camera().distanceTo(pos);
        float near = MathHelper.clamp((dist - r) / (4f * r), 0.3f, 1f);
        float flash = (fk < 0.25f ? fk / 0.25f : (1 - fk) / 0.75f) * 1.4f * G * near;
        float cap = Math.max(r, dist * 1.2f);
        if (flash > 0) {
            VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("glow_soft"));
            Vfx.billboard(vc, e, x, y, z, Math.min(r * 9f, cap * 1.5f), 0, 0.5f * flash, 0.75f * flash, flash, 1);
            vc = c.consumers().getBuffer(VfxLayers.additive("flash_star"));
            Vfx.billboard(vc, e, x, y, z, Math.min(r * 7f, cap), 0, flash, flash, flash, 1);
            Vfx.billboard(vc, e, x, y, z, Math.min(r * 4.5f, cap * 0.65f), 0.785f, 0.5f * flash, 0.7f * flash, flash, 1);
        }
        // spherical shock ring
        VertexConsumer vc = c.consumers().getBuffer(VfxLayers.additive("ring"));
        float ringR = r * (0.4f + 13f * (float) Math.pow(k, 0.5));
        float rf = (1 - k) * (1 - k) * 0.9f * G;
        Vfx.billboard(vc, e, x, y, z, ringR, 0, 0.4f * rf, 0.75f * rf, rf, 1);
        // flat ring along the ground plane
        float gr = r * (0.4f + 17f * (float) Math.pow(k, 0.45));
        Vfx.planeQuad(vc, e, x, y, z, new Vector3f(0, 1, 0), gr, 0, 0.3f * rf, 0.6f * rf, 0.9f * rf, 1);
    }
}
