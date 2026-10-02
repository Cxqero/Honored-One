package com.gojolimitless.mixin.client;

import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native address of a NativeImage's pixels, so decoded insert frames can be bulk-copied in. */
@Mixin(NativeImage.class)
public interface NativeImageAccessor {
    @Accessor("pointer")
    long gojolimitless$pointer();
}
