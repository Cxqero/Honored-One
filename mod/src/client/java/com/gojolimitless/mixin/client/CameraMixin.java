package com.gojolimitless.mixin.client;

import com.gojolimitless.client.cam.CameraShake;
import com.gojolimitless.client.cutscene.CutsceneDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();
    @Shadow private boolean thirdPerson;

    @Inject(method = "update", at = @At("TAIL"))
    private void gojolimitless$cutsceneAndShake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        CutsceneDirector.update();
        if (CutsceneDirector.active()) {
            setPos(CutsceneDirector.x(), CutsceneDirector.y(), CutsceneDirector.z());
            setRotation(CutsceneDirector.yaw(), CutsceneDirector.pitch());
            this.thirdPerson = true;     // draw the player's own model from the cutscene camera
        } else if (CutsceneDirector.debugCamActive()) {
            double[] c = CutsceneDirector.debugCamPose();
            setPos(c[0], c[1], c[2]);
            setRotation((float) c[3], (float) c[4]);
            this.thirdPerson = true;
        }
        float[] o = CameraShake.offsets(tickDelta);
        if (o != null) setRotation(getYaw() + o[0], getPitch() + o[1]);
    }
}
