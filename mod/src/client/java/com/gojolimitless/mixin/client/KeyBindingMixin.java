package com.gojolimitless.mixin.client;

import com.gojolimitless.client.input.Keybinds;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft hands each key to a single binding. When a technique key is shared with another mod's binding (Iris binds
 * R, the default for Blue, to "reload shaders"), whichever registered last silently gets it. While playing, the
 * technique keys are served first (see {@link Keybinds#claim}).
 */
@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @Inject(method = "setKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void gojolimitless$claimHeld(InputUtil.Key key, boolean pressed, CallbackInfo ci) {
        if (Keybinds.claim(key, pressed)) ci.cancel();
    }

    @Inject(method = "onKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void gojolimitless$claimPress(InputUtil.Key key, CallbackInfo ci) {
        if (Keybinds.owns(key)) ci.cancel();
    }
}
