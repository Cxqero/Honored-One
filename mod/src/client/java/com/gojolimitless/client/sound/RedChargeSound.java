package com.gojolimitless.client.sound;

import com.gojolimitless.entity.RedOrbEntity;
import com.gojolimitless.registry.ModSounds;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/** Crackling hum of the incantation Red, rising with power; stops the moment it is fired. */
public class RedChargeSound extends MovingSoundInstance {
    private final RedOrbEntity orb;

    public RedChargeSound(RedOrbEntity orb) {
        super(ModSounds.RED_CHARGE, SoundCategory.PLAYERS, Random.create());
        this.orb = orb;
        this.repeat = true;
        this.volume = 0.01f;
        this.x = orb.getX(); this.y = orb.getY(); this.z = orb.getZ();
    }

    @Override public boolean shouldAlwaysPlay() { return true; }

    @Override
    public void tick() {
        if (orb.isRemoved() || orb.getMode() != RedOrbEntity.MODE_CHARGING) { setDone(); return; }
        x = orb.getX(); y = orb.getY(); z = orb.getZ();
        float p = orb.getPower();
        volume += ((0.5f + 1.3f * p) - volume) * 0.3f;
        pitch = 0.8f + 0.45f * p;
    }
}
