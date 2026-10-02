package com.gojolimitless.ability;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.PurpleEntity;
import com.gojolimitless.net.FxType;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

/**
 * 虚式「茈」 — tap: Blue and Red brought together at once. Hold: the full incantation
 * 九綱・偏光・烏と声明・表裏の間 (Nine Ropes, Polarized Light, Crow and Declaration, Between Front and Back)
 * with hand signs, released as the 200% Hollow Purple.
 */
public class PurpleAbility implements Ability {
    public static final int STAGES = 4;

    @Override
    public void tap(ServerPlayerEntity p, CasterState s) {
        PurpleEntity.tap((ServerWorld) p.getWorld(), p);
        AbilityManager.broadcastStage(p, MoveType.PURPLE, CastStage.TAP, PurpleEntity.TAP_FORM_TICKS + 10);
    }

    @Override
    public void chargeStart(ServerPlayerEntity p, CasterState s) {
        PurpleEntity e = PurpleEntity.charge((ServerWorld) p.getWorld(), p);
        s.purpleId = e.getId();
        AbilityManager.broadcastStage(p, MoveType.PURPLE, CastStage.CHARGING, 0);
    }

    @Override
    public void chargeTick(ServerPlayerEntity p, CasterState s, int ticks) {
        if (!(p.getWorld().getEntityById(s.purpleId) instanceof PurpleEntity e) || e.getPhase() != PurpleEntity.PHASE_CHARGING) return;
        float power = (float) Math.min(1.0, ticks / (ConfigManager.get().purple.chargeSeconds * 20.0));
        e.setPower(power);
        int stage = power >= 1f ? STAGES : Math.min(STAGES, 1 + (int) Math.floor(power * STAGES - 1e-4));
        if (e.setStage(stage)) {
            ServerWorld w = (ServerWorld) p.getWorld();
            w.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.PURPLE_INCANT, SoundCategory.PLAYERS, 2.5f, 0.8f + 0.1f * stage);
            Payloads.Fx pkt = new Payloads.Fx(FxType.PURPLE_STAGE, e.getX(), e.getY(), e.getZ(), stage, power, e.getId());
            for (ServerPlayerEntity pl : PlayerLookup.around(w, e.getPos(), 200)) ServerPlayNetworking.send(pl, pkt);
        }
    }

    @Override
    public void chargeRelease(ServerPlayerEntity p, CasterState s, int ticks) {
        if (p.getWorld().getEntityById(s.purpleId) instanceof PurpleEntity e && e.getPhase() == PurpleEntity.PHASE_CHARGING) {
            if (e.getPower() < 0.25f) {
                // released almost immediately: an ordinary Purple
                e.discard();
                tap(p, s);
            } else {
                e.enterForming((ServerWorld) p.getWorld(), p);
                AbilityManager.broadcastStage(p, MoveType.PURPLE, CastStage.RELEASE, PurpleEntity.FULL_FORM_TICKS + 20);
            }
        }
        s.purpleId = -1;
    }

    @Override
    public void cancel(ServerPlayerEntity p, CasterState s) {
        if (p.getWorld().getEntityById(s.purpleId) instanceof PurpleEntity e && e.getPhase() == PurpleEntity.PHASE_CHARGING) e.discard();
        s.purpleId = -1;
    }
}
