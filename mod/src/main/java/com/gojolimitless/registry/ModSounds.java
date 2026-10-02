package com.gojolimitless.registry;

import com.gojolimitless.GojoLimitless;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    private ModSounds() {}

    public static final SoundEvent BLUE_FORM = reg("blue.form");
    public static final SoundEvent BLUE_LOOP = reg("blue.loop");
    public static final SoundEvent BLUE_MAX_LOOP = reg("blue.max_loop");
    public static final SoundEvent BLUE_CHARGE = reg("blue.charge");
    public static final SoundEvent BLUE_THROW = reg("blue.throw");
    public static final SoundEvent BLUE_COLLAPSE = reg("blue.collapse");
    public static final SoundEvent BLUE_TEAR = reg("blue.tear");
    public static final SoundEvent RED_FORM = reg("red.form");
    public static final SoundEvent RED_CHARGE = reg("red.charge");
    public static final SoundEvent RED_INCANT = reg("red.incant");
    public static final SoundEvent RED_FIRE = reg("red.fire");
    public static final SoundEvent RED_FLY = reg("red.fly");
    public static final SoundEvent RED_DETONATE = reg("red.detonate");
    public static final SoundEvent RED_DETONATE_BIG = reg("red.detonate_big");
    public static final SoundEvent PURPLE_FORM = reg("purple.form");
    public static final SoundEvent PURPLE_COLLIDE = reg("purple.collide");
    public static final SoundEvent PURPLE_HUM = reg("purple.hum");
    public static final SoundEvent PURPLE_LAUNCH = reg("purple.launch");
    public static final SoundEvent PURPLE_RUSH = reg("purple.rush");
    public static final SoundEvent PURPLE_CHARGE = reg("purple.charge");
    public static final SoundEvent PURPLE_INCANT = reg("purple.incant");
    public static final SoundEvent NUKE_THROW = reg("nuke.throw");
    public static final SoundEvent NUKE_BOOST = reg("nuke.boost");
    public static final SoundEvent BLUE_INCANT = reg("blue.incant");
    public static final SoundEvent NUKE_CONVERGE = reg("nuke.converge");
    public static final SoundEvent NUKE_COLLIDE = reg("nuke.collide");
    public static final SoundEvent NUKE_BLOOM = reg("nuke.bloom");
    public static final SoundEvent NUKE_GLINT = reg("nuke.glint");
    public static final SoundEvent NUKE_AFTERMATH = reg("nuke.aftermath");
    public static final SoundEvent NUKE_EXPLOSION = reg("nuke.explosion");
    public static final SoundEvent NUKE_SHOCKWAVE = reg("nuke.shockwave");
    public static final SoundEvent PURPLE_IMPACT = reg("purple.impact");
    public static final SoundEvent NUKE_SKY_HUM = reg("nuke.sky_hum");
    public static final SoundEvent DOMAIN_SEAL = reg("domain.seal");
    public static final SoundEvent DOMAIN_WHITE = reg("domain.white");
    public static final SoundEvent DOMAIN_INK = reg("domain.ink");
    public static final SoundEvent DOMAIN_TUNNEL = reg("domain.tunnel");
    public static final SoundEvent DOMAIN_OPEN = reg("domain.open");
    public static final SoundEvent DOMAIN_VOID = reg("domain.void");
    public static final SoundEvent DOMAIN_COLLAPSE = reg("domain.collapse");
    public static final SoundEvent DOMAIN_INSTANT = reg("domain.instant");
    public static final SoundEvent INFINITY_STOP = reg("infinity.stop");
    public static final SoundEvent INFINITY_TOGGLE = reg("infinity.toggle");

    private static SoundEvent reg(String path) {
        Identifier id = Identifier.of(GojoLimitless.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {}
}
