package com.gojolimitless.util;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

/**
 * Techniques fly far past the simulation distance (a Purple can travel 400+ blocks). Vanilla stops ticking entities
 * outside it, which would freeze them mid-flight — so each technique keeps its surrounding chunks entity-ticking
 * with a short-lived ticket, the way ender pearls do in newer versions.
 */
public final class ChunkKeeper {
    private ChunkKeeper() {}

    private static final ChunkTicketType<ChunkPos> TECHNIQUE =
            ChunkTicketType.create("gojolimitless_technique", Comparator.comparingLong(ChunkPos::toLong), 40);

    /** Keep the current chunk and the chunks it's about to fly into loaded (tickets expire by themselves). */
    public static void keepAhead(ServerWorld world, Vec3d pos, Vec3d velocity, int radius) {
        keep(world, pos, radius);
        double v = velocity.length();
        if (v < 1e-3) return;
        Vec3d d = velocity.multiply(1.0 / v);
        double look = Math.max(48, v * 25);                  // a bit more than a second ahead
        for (double s = 16; s <= look; s += 16) keep(world, pos.add(d.multiply(s)), 2);
    }

    /** radius 2 → the chunk itself is entity-ticking, neighbours loaded. */
    public static void keep(ServerWorld world, Vec3d pos, int radius) {
        ChunkPos cp = new ChunkPos(BlockPos.ofFloored(pos));
        world.getChunkManager().addTicket(TECHNIQUE, cp, Math.max(2, radius), cp);
    }
}
