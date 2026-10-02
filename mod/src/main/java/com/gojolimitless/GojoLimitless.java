package com.gojolimitless;

import com.gojolimitless.ability.AbilityManager;
import com.gojolimitless.ability.MoveType;
import com.gojolimitless.command.LimitlessCommand;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.destruction.DestructionEngine;
import com.gojolimitless.infinity.InfinityHandler;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModEntities;
import com.gojolimitless.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GojoLimitless implements ModInitializer {
    public static final String MOD_ID = "gojolimitless";
    public static final Logger LOG = LoggerFactory.getLogger("GojoLimitless");

    @Override
    public void onInitialize() {
        ConfigManager.load();
        ModSounds.init();
        com.gojolimitless.registry.ModBlocks.init();
        ModEntities.init();
        com.gojolimitless.registry.ModEffects.init();
        Payloads.register();
        InfinityHandler.init();

        ServerPlayNetworking.registerGlobalReceiver(Payloads.Input.ID, (payload, ctx) -> {
            MoveType m = MoveType.byId(payload.move());
            if (m != null) AbilityManager.onInput(ctx.player(), m, payload.pressed());
        });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.InfinityToggle.ID, (payload, ctx) -> InfinityHandler.toggle(ctx.player()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AbilityManager.tick(server);
            InfinityHandler.tick(server);
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            com.gojolimitless.util.TechniqueTicker.tick(world);
            DestructionEngine.tick(world);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> InfinityHandler.sync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AbilityManager.remove(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> LimitlessCommand.register(dispatcher));

        LOG.info("Limitless initialized");
    }
}
