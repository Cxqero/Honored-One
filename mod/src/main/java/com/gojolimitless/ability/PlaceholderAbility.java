package com.gojolimitless.ability;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Techniques still in development. */
public class PlaceholderAbility implements Ability {
    private final String name;

    public PlaceholderAbility(String name) { this.name = name; }

    @Override
    public void tap(ServerPlayerEntity p, CasterState s) {
        p.sendMessage(Text.literal(name + " arrives in a later build.").formatted(Formatting.GRAY), true);
    }

    @Override
    public void chargeStart(ServerPlayerEntity p, CasterState s) { tap(p, s); }
}
