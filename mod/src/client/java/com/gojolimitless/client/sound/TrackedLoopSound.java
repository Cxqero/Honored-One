package com.gojolimitless.client.sound;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** A looping sound whose position, volume and pitch come from suppliers (e.g. a point on a technique's timeline). */
public class TrackedLoopSound extends MovingSoundInstance {
    private final Supplier<Vec3d> pos;
    private final DoubleSupplier vol, pit;
    private final BooleanSupplier alive;

    public TrackedLoopSound(SoundEvent event, Supplier<Vec3d> pos, DoubleSupplier volume, DoubleSupplier pitch, BooleanSupplier alive) {
        super(event, SoundCategory.PLAYERS, Random.create());
        this.pos = pos; this.vol = volume; this.pit = pitch; this.alive = alive;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.01f;
        Vec3d p = pos.get();
        this.x = p.x; this.y = p.y; this.z = p.z;
    }

    @Override public boolean shouldAlwaysPlay() { return true; }

    @Override
    public void tick() {
        if (!alive.getAsBoolean()) { setDone(); return; }
        Vec3d p = pos.get();
        x = p.x; y = p.y; z = p.z;
        volume += ((float) vol.getAsDouble() - volume) * 0.3f;
        pitch = (float) pit.getAsDouble();
    }
}
