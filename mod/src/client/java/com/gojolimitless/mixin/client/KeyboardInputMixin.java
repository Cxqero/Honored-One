package com.gojolimitless.mixin.client;

import com.gojolimitless.client.cutscene.CutsceneDirector;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player stands still while a cutscene plays. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void gojolimitless$freeze(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (!CutsceneDirector.active() && !com.gojolimitless.client.nuke.NukeClient.locked() && !com.gojolimitless.client.domain.DomainClient.locked()) return;
        this.movementForward = 0; this.movementSideways = 0;
        this.pressingForward = this.pressingBack = this.pressingLeft = this.pressingRight = false;
        this.jumping = false; this.sneaking = false;
    }
}
