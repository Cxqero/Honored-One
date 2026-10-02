package com.gojolimitless.registry;

import com.gojolimitless.GojoLimitless;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Damage types live in data/gojolimitless/damage_type/*.json. */
public final class ModDamage {
    private ModDamage() {}

    public static final RegistryKey<DamageType> LAPSE_BLUE = key("lapse_blue");
    public static final RegistryKey<DamageType> REVERSAL_RED = key("reversal_red");
    public static final RegistryKey<DamageType> HOLLOW_PURPLE = key("hollow_purple");

    private static RegistryKey<DamageType> key(String path) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(GojoLimitless.MOD_ID, path));
    }

    public static DamageSource source(World world, RegistryKey<DamageType> key, Entity direct, Entity attacker) {
        return world.getDamageSources().create(key, direct, attacker);
    }
}
