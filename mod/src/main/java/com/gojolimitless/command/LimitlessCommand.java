package com.gojolimitless.command;

import com.gojolimitless.ability.AbilityManager;
import com.gojolimitless.ability.MoveType;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.infinity.InfinityHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /limitless cast <move> [holdTicks] · /limitless infinity · /limitless reload */
public final class LimitlessCommand {
    private LimitlessCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("limitless")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("cast")
                        .then(CommandManager.argument("move", StringArgumentType.word())
                                .suggests((ctx, b) -> { for (MoveType m : MoveType.values()) b.suggest(m.key); return b.buildFuture(); })
                                .executes(ctx -> cast(ctx, 0))
                                .then(CommandManager.argument("holdTicks", IntegerArgumentType.integer(0, 2400))
                                        .executes(ctx -> cast(ctx, IntegerArgumentType.getInteger(ctx, "holdTicks"))))))
                .then(CommandManager.literal("infinity").executes(ctx -> {
                    InfinityHandler.toggle(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("reload").executes(ctx -> {
                    ConfigManager.load();
                    ctx.getSource().sendFeedback(() -> Text.literal("Limitless config reloaded"), false);
                    return 1;
                })));
    }

    private static int cast(CommandContext<ServerCommandSource> ctx, int hold) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        String key = StringArgumentType.getString(ctx, "move");
        for (MoveType m : MoveType.values()) {
            if (m.key.equals(key)) {
                AbilityManager.simulate(p, m, hold);
                return 1;
            }
        }
        ctx.getSource().sendError(Text.literal("Unknown technique: " + key));
        return 0;
    }
}
