package com.gojolimitless.mixin.client;

import com.gojolimitless.client.nuke.NukeClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Silhouettes against the bloom of the remote Hollow Purple: creatures near it are drawn with the light taken away. */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int gojolimitless$silhouette(int light, LivingEntity entity, float yaw, float tickDelta, MatrixStack ms, VertexConsumerProvider vcp) {
        float k = NukeClient.silhouette(entity);
        if (k <= 0.01f) return light;
        int block = LightmapTextureManager.getBlockLightCoordinates(light), sky = LightmapTextureManager.getSkyLightCoordinates(light);
        return LightmapTextureManager.pack(Math.round(MathHelper.lerp(k, block, 0)), Math.round(MathHelper.lerp(k, sky, 0)));
    }
}
