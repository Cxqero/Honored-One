package com.gojolimitless.client.fx;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** What a transient effect needs to draw itself relative to the FX host. */
public record FxContext(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d origin, Vec3d camera, float tickDelta) {
    public float lx(Vec3d p) { return (float) (p.x - origin.x); }
    public float ly(Vec3d p) { return (float) (p.y - origin.y); }
    public float lz(Vec3d p) { return (float) (p.z - origin.z); }
    /** Camera position relative to point p. */
    public Vec3d camRel(Vec3d p) { return camera.subtract(p); }
}
