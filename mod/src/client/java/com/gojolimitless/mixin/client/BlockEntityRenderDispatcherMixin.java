package com.gojolimitless.mixin.client;

import com.gojolimitless.client.domain.DomainClient;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chests, signs and the like are part of the world that Unlimited Void replaces. */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void gojolimitless$hide(E be, float td, MatrixStack ms, VertexConsumerProvider vcp, CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }
}
