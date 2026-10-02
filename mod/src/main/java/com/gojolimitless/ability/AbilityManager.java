package com.gojolimitless.ability;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Turns key presses into technique calls: tap vs hold, charge ticking, release. */
public final class AbilityManager {
    private static final Map<UUID, CasterState> STATES = new HashMap<>();
    private static final EnumMap<MoveType, Ability> ABILITIES = new EnumMap<>(MoveType.class);

    static {
        ABILITIES.put(MoveType.BLUE, new BlueAbility());
        ABILITIES.put(MoveType.RED, new RedAbility());
        ABILITIES.put(MoveType.PURPLE, new PurpleAbility());
        ABILITIES.put(MoveType.NUKE, new NukeAbility());
        ABILITIES.put(MoveType.DOMAIN, new DomainAbility());
    }

    private AbilityManager() {}

    public static CasterState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), u -> {
            CasterState s = new CasterState();
            s.infinity = ConfigManager.get().infinity.enabledByDefault;
            return s;
        });
    }

    public static void remove(ServerPlayerEntity p) {
        CasterState s = STATES.remove(p.getUuid());
        if (s != null && s.held != null && s.charging) ABILITIES.get(s.held).cancel(p, s);
    }

    private static int holdThresholdTicks() {
        return Math.max(2, Math.round(ConfigManager.get().general.holdThresholdMs / 50f));
    }

    public static void onInput(ServerPlayerEntity p, MoveType move, boolean pressed) {
        CasterState s = state(p);
        if (pressed) {
            if (s.held != null) return;            // one technique at a time
            if (!p.isAlive() || p.isSpectator()) return;
            if (com.gojolimitless.entity.NukeEntity.busy(p, s.nukeId)) return;
            if (com.gojolimitless.registry.ModEffects.paralysed(p)) return;   // caught in someone's Unlimited Void
            s.held = move;
            s.heldTicks = 0;
            s.charging = false;
            s.autoReleaseAt = -1;
        } else {
            if (s.held != move) return;
            finish(p, s);
        }
    }

    private static void finish(ServerPlayerEntity p, CasterState s) {
        Ability a = ABILITIES.get(s.held);
        if (s.charging) a.chargeRelease(p, s, s.heldTicks);
        else a.tap(p, s);
        s.held = null;
        s.charging = false;
        s.heldTicks = 0;
        s.autoReleaseAt = -1;
    }

    /** Debug / autotest: press now, release after {@code holdTicks} (0 = tap). */
    public static void simulate(ServerPlayerEntity p, MoveType move, int holdTicks) {
        onInput(p, move, true);
        CasterState s = state(p);
        if (s.held != move) return;
        if (holdTicks <= 0) finish(p, s);
        else s.autoReleaseAt = holdTicks;
    }

    public static void tick(MinecraftServer server) {
        int threshold = holdThresholdTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            CasterState s = STATES.get(p.getUuid());
            if (s == null || s.held == null) continue;
            if (!p.isAlive()) {
                MoveType m = s.held;
                if (s.charging) ABILITIES.get(m).cancel(p, s);
                s.held = null; s.charging = false;
                broadcastStage(p, m, CastStage.END, 0);
                continue;
            }
            s.heldTicks++;
            Ability a = ABILITIES.get(s.held);
            if (!s.charging && s.heldTicks >= threshold) {
                s.charging = true;
                a.chargeStart(p, s);
            }
            if (s.charging) a.chargeTick(p, s, s.heldTicks - threshold);
            if (s.autoReleaseAt > 0 && s.heldTicks >= s.autoReleaseAt) finish(p, s);
        }
    }

    public static void broadcastStage(ServerPlayerEntity p, MoveType move, int stage, int duration) {
        Payloads.CastState pkt = new Payloads.CastState(p.getId(), move.ordinal(), stage, duration);
        ServerPlayNetworking.send(p, pkt);
        for (ServerPlayerEntity other : PlayerLookup.tracking(p)) if (other != p) ServerPlayNetworking.send(other, pkt);
    }
}
