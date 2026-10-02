package com.gojolimitless.ability;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.DomainEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Domain Expansion: Unlimited Void. Hold: the full domain, expanded as soon as the hold registers. Tap: the
 * 0.2-second domain. Pressing again while it is up collapses it (the white ink wipe, then the world returns).
 */
public class DomainAbility implements Ability {
    @Override
    public void tap(ServerPlayerEntity p, CasterState s) { expand(p, s, true); }

    @Override
    public void chargeStart(ServerPlayerEntity p, CasterState s) { expand(p, s, false); }

    private static void expand(ServerPlayerEntity p, CasterState s, boolean instant) {
        if (DomainEntity.active(p, s.domainId)) {
            // pressed again while it is up: collapse it
            if (ConfigManager.get().domain.collapseOnRecast && p.getWorld().getEntityById(s.domainId) instanceof DomainEntity d) d.requestCollapse();
            return;
        }
        long now = p.getWorld().getTime();
        if (now < s.domainReadyAt) return;
        DomainEntity e = DomainEntity.cast((ServerWorld) p.getWorld(), p, instant);
        s.domainId = e.getId();
        AbilityManager.broadcastStage(p, MoveType.DOMAIN, instant ? CastStage.TAP : CastStage.CHARGING, e.endTick());
    }
}
