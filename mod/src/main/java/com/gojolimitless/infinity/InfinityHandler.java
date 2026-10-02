package com.gojolimitless.infinity;

import com.gojolimitless.ability.AbilityManager;
import com.gojolimitless.ability.CasterState;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.net.FxType;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 無下限 Infinity. The space between the user and anything approaching is divided endlessly:
 * projectiles decelerate and hang in the air, blows never land.
 */
public final class InfinityHandler {
    private InfinityHandler() {}

    /** Projectiles currently held in place → the player holding them and where they stopped. */
    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();

    private record Frozen(UUID player, Vec3d pos, ServerWorld world) {}

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            if (!AbilityManager.state(p).infinity) return true;
            LimitlessConfig.Infinity c = ConfigManager.get().infinity;
            if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            if (source.isIn(DamageTypeTags.IS_FALL)) return !c.blockFall;
            if (source.isIn(DamageTypeTags.IS_EXPLOSION)) return !c.blockExplosions;
            if (source.isIn(DamageTypeTags.IS_PROJECTILE)) return !c.blockProjectiles;
            Entity attacker = source.getAttacker();
            if (attacker != null && attacker != p) return !c.blockMelee;
            return true;
        });
    }

    public static void toggle(ServerPlayerEntity p) {
        CasterState s = AbilityManager.state(p);
        s.infinity = !s.infinity;
        sync(p);
        p.sendMessage(Text.translatable(s.infinity ? "gojolimitless.infinity.on" : "gojolimitless.infinity.off")
                .formatted(s.infinity ? Formatting.AQUA : Formatting.GRAY), true);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.INFINITY_TOGGLE, SoundCategory.PLAYERS, 0.8f, s.infinity ? 1.2f : 0.8f);
        if (!s.infinity) releaseAll(p.getUuid());
    }

    public static void sync(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, new Payloads.InfinityState(AbilityManager.state(p).infinity));
    }

    public static void tick(MinecraftServer server) {
        LimitlessConfig.Infinity c = ConfigManager.get().infinity;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (!AbilityManager.state(p).infinity) continue;
            ServerWorld world = p.getServerWorld();
            Vec3d center = p.getBoundingBox().getCenter();
            Box area = p.getBoundingBox().expand(c.slowRadius + 2);
            for (ProjectileEntity pe : world.getEntitiesByClass(ProjectileEntity.class, area, e -> e.getOwner() != p && e.isAlive())) {
                if (FROZEN.containsKey(pe.getUuid())) continue;
                Vec3d rel = pe.getPos().subtract(center);
                double d = Math.max(0, rel.length() - 0.6);
                if (d > c.slowRadius) continue;
                Vec3d v = pe.getVelocity();
                Vec3d toward = rel.lengthSquared() > 1e-6 ? rel.normalize().negate() : Vec3d.ZERO;
                double approach = v.dotProduct(toward);
                if (approach <= 0) continue;                       // moving away: leave it alone
                if (d <= c.stopRadius) {
                    freeze(p, pe, world, c);
                    continue;
                }
                // convergent sequence: the closer it gets, the more of its approach speed is divided away
                double k = (d - c.stopRadius) / Math.max(0.05, c.slowRadius - c.stopRadius);
                double keep = Math.pow(Math.max(0, k), 1.6);
                Vec3d radial = toward.multiply(approach);
                Vec3d tangential = v.subtract(radial);
                pe.setVelocity(tangential.multiply(0.7).add(radial.multiply(keep)));
                pe.setNoGravity(true);
                pe.velocityModified = true;
            }
            if (c.blockMelee) {
                for (Entity e : world.getOtherEntities(p, p.getBoundingBox().expand(0.9),
                        e -> e instanceof MobEntity m && (m instanceof HostileEntity || m.getTarget() == p))) {
                    Vec3d away = e.getPos().subtract(p.getPos()).multiply(1, 0, 1);
                    if (away.lengthSquared() < 1e-4) continue;
                    e.setVelocity(e.getVelocity().multiply(0.3).add(away.normalize().multiply(0.18)));
                    e.velocityModified = true;
                }
            }
        }
        // hold frozen projectiles; drop them when the holder walks away or turns Infinity off
        Iterator<Map.Entry<UUID, Frozen>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> en = it.next();
            Frozen f = en.getValue();
            Entity pe = f.world.getEntity(en.getKey());
            if (pe == null || !pe.isAlive()) { it.remove(); continue; }
            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(f.player);
            boolean keep = holder != null && holder.isAlive() && AbilityManager.state(holder).infinity
                    && holder.getWorld() == pe.getWorld()
                    && holder.getBoundingBox().getCenter().distanceTo(f.pos) < c.slowRadius + 1.5;
            if (keep) {
                pe.setPosition(f.pos);
                pe.setVelocity(Vec3d.ZERO);
                pe.velocityModified = true;
            } else {
                pe.setNoGravity(false);
                it.remove();
            }
        }
    }

    private static void freeze(ServerPlayerEntity p, ProjectileEntity pe, ServerWorld world, LimitlessConfig.Infinity c) {
        pe.setVelocity(Vec3d.ZERO);
        pe.setNoGravity(true);
        pe.velocityModified = true;
        FROZEN.put(pe.getUuid(), new Frozen(p.getUuid(), pe.getPos(), world));
        world.playSound(null, pe.getX(), pe.getY(), pe.getZ(), ModSounds.INFINITY_STOP, SoundCategory.PLAYERS, 0.7f, 1.0f + world.random.nextFloat() * 0.2f);
        if (c.ripples) {
            Payloads.Fx pkt = new Payloads.Fx(FxType.INFINITY_RIPPLE, pe.getX(), pe.getY(), pe.getZ(), 0.9f, 0, p.getId());
            ServerPlayNetworking.send(p, pkt);
        }
    }

    private static void releaseAll(UUID player) {
        FROZEN.entrySet().removeIf(en -> {
            if (!en.getValue().player.equals(player)) return false;
            Entity pe = en.getValue().world.getEntity(en.getKey());
            if (pe != null) pe.setNoGravity(false);
            return true;
        });
    }
}
