package com.gojolimitless.client.sound;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** A looping sound that rides on an entity while a condition holds, with volume/pitch driven by the entity. */
public class FollowLoopSound<T extends Entity> extends MovingSoundInstance {
    private final T entity;
    private final Predicate<T> alive;
    private final ToDoubleFunction<T> vol, pit;

    public FollowLoopSound(T entity, SoundEvent event, Predicate<T> alive, ToDoubleFunction<T> volume, ToDoubleFunction<T> pitch) {
        super(event, SoundCategory.PLAYERS, Random.create());
        this.entity = entity; this.alive = alive; this.vol = volume; this.pit = pitch;
        this.repeat = true;
        this.volume = 0.01f;
        this.x = entity.getX(); this.y = entity.getY(); this.z = entity.getZ();
    }

    @Override public boolean shouldAlwaysPlay() { return true; }

    @Override
    public void tick() {
        if (entity.isRemoved() || !alive.test(entity)) { setDone(); return; }
        x = entity.getX(); y = entity.getY(); z = entity.getZ();
        volume += ((float) vol.applyAsDouble(entity) - volume) * 0.25f;
        pitch = (float) pit.applyAsDouble(entity);
    }
}
