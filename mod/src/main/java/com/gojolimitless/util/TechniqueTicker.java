package com.gojolimitless.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Vanilla only ticks entities inside the simulation distance. A thrown Blue, a Red or a Purple can outrun it in a
 * second; if they stopped ticking they'd hang frozen in the sky. Every technique entity registers here and, if the
 * world skipped it this tick, it is ticked by hand (and keeps the chunks ahead of it loaded).
 */
public final class TechniqueTicker {
    private TechniqueTicker() {}

    private static final Set<Entity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    public interface Tracked {
        long lastServerTick();
    }

    public static void register(Entity e) { ACTIVE.add(e); }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        for (Entity e : ACTIVE.toArray(new Entity[0])) {
            if (e.isRemoved()) { ACTIVE.remove(e); continue; }
            if (e.getWorld() != world || !(e instanceof Tracked t)) continue;
            if (t.lastServerTick() != now) {
                e.resetPosition();
                e.age++;
                e.tick();
            }
        }
    }
}
