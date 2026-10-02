package com.gojolimitless.mixin.client;

import com.gojolimitless.client.domain.DomainClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside Unlimited Void the world is gone: no terrain, sky, clouds or block outline — only the void (drawn by the
 * domain itself) and the creatures caught in it. Sodium and Iris draw terrain from inside renderLayer too, so
 * cancelling here (ahead of them, priority 900) covers them as well.
 */
@Mixin(value = WorldRenderer.class, priority = 900)
public class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void gojolimitless$frameMatrices(net.minecraft.client.render.RenderTickCounter tc, boolean outline, Camera camera,
                                             net.minecraft.client.render.GameRenderer gr, net.minecraft.client.render.LightmapTextureManager lm,
                                             Matrix4f view, Matrix4f proj, CallbackInfo ci) {
        com.gojolimitless.client.render.DeferredVfx.begin(view, proj);
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    private void gojolimitless$hideTerrain(RenderLayer layer, double x, double y, double z, Matrix4f m1, Matrix4f m2, CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void gojolimitless$hideSky(Matrix4f m1, Matrix4f m2, float td, Camera camera, boolean fog, Runnable fogCb, CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void gojolimitless$hideClouds(MatrixStack ms, Matrix4f m1, Matrix4f m2, float td, double x, double y, double z, CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void gojolimitless$hideWeather(net.minecraft.client.render.LightmapTextureManager lm, float td, double x, double y, double z, CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void gojolimitless$hideOutline(MatrixStack ms, VertexConsumer vc, Entity e, double x, double y, double z, BlockPos pos, BlockState st, CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }
}
