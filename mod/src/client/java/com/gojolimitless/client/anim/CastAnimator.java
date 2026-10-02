package com.gojolimitless.client.anim;

import com.gojolimitless.GojoLimitless;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * Gives every client-side player a {@link CastAnimation} layer (above emotes, which usually sit at 1000). The layer
 * is stored on the player itself (playerAnimator's associated data), since players are registered mid-construction.
 */
public final class CastAnimator {
    private CastAnimator() {}

    private static final int PRIORITY = 1500;
    private static final Identifier ID = Identifier.of(GojoLimitless.MOD_ID, "cast");

    public static void init() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            CastAnimation a = new CastAnimation(player);
            PlayerAnimationAccess.getPlayerAssociatedData(player).set(ID, a);
            stack.addAnimLayer(PRIORITY, a);
        });
    }

    public static CastAnimation of(Entity e) {
        if (!(e instanceof AbstractClientPlayerEntity p)) return null;
        return PlayerAnimationAccess.getPlayerAssociatedData(p).get(ID) instanceof CastAnimation c ? c : null;
    }
}
