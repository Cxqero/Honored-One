package com.gojolimitless.registry;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.effect.VoidParalysisEffect;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

public final class ModEffects {
    private ModEffects() {}

    public static final RegistryEntry<StatusEffect> UNLIMITED_VOID = Registry.registerReference(Registries.STATUS_EFFECT,
            Identifier.of(GojoLimitless.MOD_ID, "unlimited_void"), new VoidParalysisEffect());

    public static boolean paralysed(PlayerEntity p) { return p.hasStatusEffect(UNLIMITED_VOID); }

    public static void init() {
        // a paralysed player can't swing, break, place or use anything
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> paralysed(p) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((p, w, h, pos, dir) -> paralysed(p) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((p, w, h, hit) -> paralysed(p) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> paralysed(p) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((p, w, h) -> paralysed(p) ? TypedActionResult.fail(p.getStackInHand(h)) : TypedActionResult.pass(p.getStackInHand(h)));
    }
}
