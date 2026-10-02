package com.gojolimitless.client.render;

import com.gojolimitless.client.fx.FxManager;
import com.gojolimitless.entity.FxHostEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class FxHostRenderer extends EntityRenderer<FxHostEntity> {
    public FxHostRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override public boolean shouldRender(FxHostEntity entity, Frustum frustum, double x, double y, double z) { return true; }

    @Override public Identifier getTexture(FxHostEntity entity) { return VfxLayers.tex("spark"); }

    @Override
    public void render(FxHostEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        vcp = DeferredVfx.route(vcp);
        FxManager.render(e, td, ms, vcp, RenderPath.inShadowPass());
    }
}
