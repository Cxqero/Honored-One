package com.gojolimitless.mixin.client;

import com.gojolimitless.client.cutscene.CutsceneDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cutscene lens: field of view and camera roll from the Blender camera. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void gojolimitless$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (CutsceneDirector.active()) cir.setReturnValue((double) CutsceneDirector.fov());
    }

    @Inject(method = "renderWorld", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void gojolimitless$effectsAfterPack(net.minecraft.client.render.RenderTickCounter tc, CallbackInfo ci) {
        com.gojolimitless.client.render.DeferredVfx.flush();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void gojolimitless$roll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CutsceneDirector.active() && CutsceneDirector.roll() != 0f)
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(CutsceneDirector.roll()));
    }
}
