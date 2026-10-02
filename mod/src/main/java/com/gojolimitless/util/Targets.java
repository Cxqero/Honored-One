package com.gojolimitless.util;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.BlueOrbEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Who techniques are allowed to affect. */
public final class Targets {
    private Targets() {}

    public static boolean affectable(Entity e, Entity caster) {
        if (e == caster || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof BlueOrbEntity) return false;
        if (e instanceof PlayerEntity p && p.isCreative() && p != caster) return false;
        if (caster instanceof PlayerEntity owner && ConfigManager.get().general.sparePets
                && e instanceof TameableEntity t && t.isTamed() && t.isOwner(owner)) return false;
        if (e.hasPassenger(caster) || caster.hasPassenger(e)) return false;
        return true;
    }
}
