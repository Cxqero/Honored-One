package com.gojolimitless.ability;

import net.minecraft.server.network.ServerPlayerEntity;

/** A technique bound to one key. Tap = released before the hold threshold. */
public interface Ability {
    void tap(ServerPlayerEntity player, CasterState state);

    default void chargeStart(ServerPlayerEntity player, CasterState state) {}

    default void chargeTick(ServerPlayerEntity player, CasterState state, int ticks) {}

    default void chargeRelease(ServerPlayerEntity player, CasterState state, int ticks) {}

    /** Player died / left / switched away mid-charge. */
    default void cancel(ServerPlayerEntity player, CasterState state) {}
}
