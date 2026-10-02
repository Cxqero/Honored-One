package com.gojolimitless.mixin.client;

import com.gojolimitless.client.cutscene.CutsceneDirector;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse look is ignored during a cutscene so the aim at cast time is kept. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void gojolimitless$lockLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (CutsceneDirector.active() && (Object) this instanceof ClientPlayerEntity) ci.cancel();
    }
}
