package com.gojolimitless.client.sound;

import com.gojolimitless.entity.BlueOrbEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** Hum that follows a Blue orb; deeper and louder as it grows. */
public class OrbLoopSound extends MovingSoundInstance {
    private final BlueOrbEntity orb;
    private final boolean max;

    public OrbLoopSound(BlueOrbEntity orb, SoundEvent event, boolean max) {
        super(event, SoundCategory.PLAYERS, Random.create());
        this.orb = orb;
        this.max = max;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.01f;
        this.x = orb.getX(); this.y = orb.getY(); this.z = orb.getZ();
    }

    @Override
    public boolean shouldAlwaysPlay() { return true; }

    @Override
    public void tick() {
        if (orb.isRemoved()) { setDone(); return; }
        x = orb.getX(); y = orb.getY(); z = orb.getZ();
        float r = orb.getRadius();
        float target = max ? MathHelper.clamp(0.6f + r * 0.18f, 0.6f, 3.0f) : 1.4f;
        if (orb.getMode() == BlueOrbEntity.MODE_COLLAPSING) target *= 0.5f;
        volume += (target - volume) * 0.25f;
        pitch = max ? MathHelper.clamp(1.15f - r * 0.045f, 0.55f, 1.2f) : 1.0f;
    }
}
