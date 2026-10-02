package com.gojolimitless.mixin.client;

import com.gojolimitless.client.domain.DomainClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside Unlimited Void, also stop Sodium's own terrain draw (every overload of drawChunkLayer). The WorldRenderer
 * hook already covers Sodium 0.6, which draws from inside renderLayer; this is for Sodium versions that don't.
 * Applies only when the class and method exist.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public class SodiumTerrainMixin {
    @Inject(method = "drawChunkLayer", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gojolimitless$hideTerrain(CallbackInfo ci) {
        if (DomainClient.hideTerrain()) ci.cancel();
    }
}
