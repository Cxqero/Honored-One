package com.gojolimitless.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * A client-side-only entity parked at the camera every frame. Its renderer draws all free-floating
 * effects through the normal entity pipeline, which is what shaderpacks (Iris) handle best.
 */
public class FxHostEntity extends Entity {
    public FxHostEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    @Override public boolean shouldRender(double distance) { return true; }
    @Override public boolean shouldRender(double x, double y, double z) { return true; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(1.0e4); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public void tick() { /* driven by the client effect manager */ }
}
