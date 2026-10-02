package com.gojolimitless.ability;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.RedOrbEntity;
import com.gojolimitless.net.FxType;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

/** 術式反転「赫」 — tap: a quick Red. Hold: chant 位相・波羅蜜・光の柱 (Phase, Paramita, Pillars of Light), release to fire. */
public class RedAbility implements Ability {
    public static final int STAGES = 3;

    @Override
    public void tap(ServerPlayerEntity p, CasterState s) {
        RedOrbEntity.create((ServerWorld) p.getWorld(), p, false);
        AbilityManager.broadcastStage(p, MoveType.RED, CastStage.TAP, 12);
    }

    @Override
    public void chargeStart(ServerPlayerEntity p, CasterState s) {
        RedOrbEntity orb = RedOrbEntity.create((ServerWorld) p.getWorld(), p, true);
        s.redOrbId = orb.getId();
        AbilityManager.broadcastStage(p, MoveType.RED, CastStage.CHARGING, 0);
    }

    @Override
    public void chargeTick(ServerPlayerEntity p, CasterState s, int ticks) {
        if (!(p.getWorld().getEntityById(s.redOrbId) instanceof RedOrbEntity orb)) return;
        double full = ConfigManager.get().red.chargeSeconds * 20.0;
        float power = (float) Math.min(1.0, ticks / full);
        orb.setPower(power);
        // one incantation word per third of the charge; the first word is spoken immediately
        int stage = Math.min(STAGES, 1 + (int) Math.floor(power * STAGES - 1e-4));
        if (power >= 1f) stage = STAGES;
        if (orb.setStage(stage)) {
            ServerWorld w = (ServerWorld) p.getWorld();
            w.playSound(null, orb.getX(), orb.getY(), orb.getZ(), ModSounds.RED_INCANT, SoundCategory.PLAYERS, 2.0f, 0.85f + 0.12f * stage);
            Payloads.Fx pkt = new Payloads.Fx(FxType.RED_STAGE, orb.getX(), orb.getY(), orb.getZ(), stage, power, orb.getId());
            for (ServerPlayerEntity pl : PlayerLookup.around(w, orb.getPos(), 160)) ServerPlayNetworking.send(pl, pkt);
        }
    }

    @Override
    public void chargeRelease(ServerPlayerEntity p, CasterState s, int ticks) {
        if (p.getWorld().getEntityById(s.redOrbId) instanceof RedOrbEntity orb) orb.fire(p);
        s.redOrbId = -1;
        AbilityManager.broadcastStage(p, MoveType.RED, CastStage.RELEASE, 12);
    }

    @Override
    public void cancel(ServerPlayerEntity p, CasterState s) {
        if (p.getWorld().getEntityById(s.redOrbId) instanceof RedOrbEntity orb) orb.discard();
        s.redOrbId = -1;
    }
}
