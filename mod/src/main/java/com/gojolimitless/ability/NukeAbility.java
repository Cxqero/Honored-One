package com.gojolimitless.ability;

import com.gojolimitless.entity.NukeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Remote Hollow Purple (ch. 234–235). A single cinematic technique: tap (or hold and release) to cast at whatever
 * is under the crosshair. Other techniques are locked out until it finishes.
 */
public class NukeAbility implements Ability {
    @Override
    public void tap(ServerPlayerEntity p, CasterState s) {
        if (NukeEntity.busy(p, s.nukeId)) return;
        NukeEntity e = NukeEntity.cast((ServerWorld) p.getWorld(), p);
        if (e == null) return;
        s.nukeId = e.getId();
        AbilityManager.broadcastStage(p, MoveType.NUKE, CastStage.TAP, NukeEntity.T_END);
    }

    @Override
    public void chargeRelease(ServerPlayerEntity p, CasterState s, int ticks) {
        tap(p, s);
    }
}
