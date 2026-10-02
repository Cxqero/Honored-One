package com.gojolimitless.client.render;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.client.nuke.NukeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

/**
 * Six Eyes: the caster's eyes blaze cyan during the big techniques. The glow uses the vanilla "eyes" layer (like a
 * spider's or an enderman's), which every shaderpack treats as emissive, on the standard skin eye pixels, plus a
 * soft bloom quad over each eye.
 */
public class EyeGlowFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEX = Identifier.of(GojoLimitless.MOD_ID, "textures/entity/six_eyes.png");

    public EyeGlowFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry en, float cx, float cy, float cz, float hx, float hy, float r, float g, float b) {
        Vfx.vert(vc, en, cx - hx, cy - hy, cz, r, g, b, 1, 0, 0);
        Vfx.vert(vc, en, cx - hx, cy + hy, cz, r, g, b, 1, 0, 1);
        Vfx.vert(vc, en, cx + hx, cy + hy, cz, r, g, b, 1, 1, 1);
        Vfx.vert(vc, en, cx + hx, cy - hy, cz, r, g, b, 1, 1, 0);
    }

    public static float intensity(LivingEntity e) {
        return Math.max(NukeClient.eyeGlow(e), com.gojolimitless.client.domain.DomainClient.eyeGlow(e));
    }

    @Override
    public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity e, float limbAngle,
                       float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        vcp = DeferredVfx.route(vcp);
        if (RenderPath.inShadowPass() || e.isInvisible()) return;
        float g = intensity(e);
        if (g <= 0.01f) return;
        int v = (int) (255 * Math.min(1f, g) * 0.75f);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TEX));
        getContextModel().head.render(ms, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, ColorHelper.Argb.getArgb(255, v, v, v));
        ms.push();
        getContextModel().head.rotate(ms);
        MatrixStack.Entry en = ms.peek();
        VertexConsumer glow = vcp.getBuffer(VfxLayers.additive("glow_soft"));
        float flick = 1f + 0.06f * (float) Math.sin(animationProgress * 1.7f);
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * 2f / 16f, cy = -3.5f / 16f, cz = -4.12f / 16f;
            // halo: cyan-blue, a little wider than the eye
            quad(glow, en, cx, cy, cz, 3.6f / 16f * flick, 3.0f / 16f * flick, 0.06f * g, 0.32f * g, 0.9f * g);
            // the iris itself burning pale cyan
            quad(glow, en, cx, cy, cz - 0.002f, 1.5f / 16f, 1.1f / 16f, 0.3f * g, 0.75f * g, 1.0f * g);
            // a thin horizontal glint
            quad(glow, en, cx + side * 0.6f / 16f, cy, cz - 0.004f, 4.2f / 16f * g, 0.22f / 16f, 0.25f * g, 0.7f * g, 1.0f * g);
        }
        ms.pop();
    }
}
