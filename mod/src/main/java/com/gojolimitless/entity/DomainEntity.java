package com.gojolimitless.entity;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModEffects;
import com.gojolimitless.registry.ModEntities;
import com.gojolimitless.registry.ModSounds;
import com.gojolimitless.util.Targets;
import com.gojolimitless.util.TechniqueTicker;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Domain Expansion: Unlimited Void (無量空処). One entity at the caster's feet runs the expansion (the seal, the white,
 * the ink, the tunnel of information, the flash), holds the barrier open and collapses it; clients derive every
 * visual from its age, mode and radius.
 *
 * Canon: the sure-hit floods the victims with infinite information — they perceive everything and can do nothing.
 * Gojo excluded Itadori by touching him, so anything touching the caster when the domain expands is left out. The
 * 0.2-second version (Shibuya) is a single glimpse that leaves its victims paralysed long after.
 */
public class DomainEntity extends Entity implements TechniqueTicker.Tracked {
    // ---- full domain (hold), ticks from the cast. Keep in sync with blender/cutscene (domain_full) and DomainClient.
    public static final int D_WHITE = 44, D_INK = 62, D_TUNNEL = 102, D_FLASH = 158, D_OPEN = 160, D_CLOSE_LEN = 14;
    // ---- the 0.2-second domain (tap)
    public static final int I_WHITE = 8, I_INK = 11, I_VOID = 15, I_WIPE = 19, I_END = 31;

    private static final TrackedData<Integer> OWNER = DataTracker.registerData(DomainEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> INSTANT = DataTracker.registerData(DomainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(DomainEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> YAW = DataTracker.registerData(DomainEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Tick the barrier starts to collapse (full domain; may come early if the caster falls). */
    private static final TrackedData<Integer> CLOSE_AT = DataTracker.registerData(DomainEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private long lastServerTick = -1;
    private UUID ownerUuid;
    private final Set<UUID> excluded = new HashSet<>();
    private int plannedClose = Integer.MAX_VALUE;

    public DomainEntity(EntityType<? extends DomainEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
        if (!world.isClient) TechniqueTicker.register(this);
    }

    @Override public long lastServerTick() { return lastServerTick; }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(OWNER, -1);
        b.add(INSTANT, false);
        b.add(RADIUS, 32f);
        b.add(YAW, 0f);
        b.add(CLOSE_AT, Integer.MAX_VALUE);
    }

    public int getOwnerId() { return dataTracker.get(OWNER); }
    public boolean isInstant() { return dataTracker.get(INSTANT); }
    public float getRadius() { return dataTracker.get(RADIUS); }
    public float getYawDeg() { return dataTracker.get(YAW); }
    public int getCloseAt() { return dataTracker.get(CLOSE_AT); }
    public int endTick() { return isInstant() ? I_END : getCloseAt() + D_CLOSE_LEN; }

    /** The tick the barrier is up and the sure-hit lands. */
    public int expandTick() { return isInstant() ? I_VOID : D_INK; }

    /** True while the interior (the void, the black hole) is showing. */
    public boolean interior(float t) {
        return isInstant() ? t >= I_VOID && t < I_WIPE + 4 : t >= D_OPEN && t < getCloseAt() + 8;
    }

    // ------------------------------------------------------------------ cast
    public static DomainEntity cast(ServerWorld world, ServerPlayerEntity p, boolean instant) {
        LimitlessConfig.Domain c = ConfigManager.get().domain;
        DomainEntity e = new DomainEntity(ModEntities.DOMAIN, world);
        e.ownerUuid = p.getUuid();
        e.setPosition(p.getPos());
        e.dataTracker.set(OWNER, p.getId());
        e.dataTracker.set(INSTANT, instant);
        e.dataTracker.set(RADIUS, (float) (instant ? c.instantRadius : c.radius));
        e.dataTracker.set(YAW, p.getYaw());
        if (!instant) {
            e.plannedClose = D_OPEN + (int) Math.round(c.seconds * 20);
            e.dataTracker.set(CLOSE_AT, e.plannedClose);
        }
        world.spawnEntity(e);
        e.layFloor(world, p);
        // the pressure of the expanding barrier lays the grass flat around him (keeps the seal's close shots clear)
        if (!instant && ConfigManager.get().general.destruction && com.gojolimitless.destruction.DestructionEngine.allowed(world))
            NukeEntity.flattenPlants(world, p.getBlockPos(), 3);
        if (ConfigManager.get().client.cutscenes)
            ServerPlayNetworking.send(p, new Payloads.Cutscene(instant ? "domain_instant" : "domain_full", p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getId()));
        return e;
    }

    /** The caster pressed the key again: collapse (full domain, once it is open). Returns true if it will close. */
    public boolean requestCollapse() {
        if (isInstant() || age < D_OPEN) return false;
        // a few ticks' grace so the white ink wipe starts before the void is gone
        if (getCloseAt() > age + 6) dataTracker.set(CLOSE_AT, age + 6);
        return true;
    }

    /** True while this player's domain is up. */
    public static boolean active(ServerPlayerEntity p, int id) {
        return id >= 0 && p.getWorld().getEntityById(id) instanceof DomainEntity d && !d.isRemoved();
    }

    // ------------------------------------------------------------------ server timeline
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerWorld world = (ServerWorld) getWorld();
        lastServerTick = world.getTime();
        ServerPlayerEntity owner = ownerUuid != null && world.getPlayerByUuid(ownerUuid) instanceof ServerPlayerEntity sp ? sp : null;
        boolean ownerOk = owner != null && owner.isAlive() && !owner.isRemoved() && owner.getWorld() == world;
        int t = age;
        LimitlessConfig.Domain c = ConfigManager.get().domain;

        if (!ownerOk && t < expandTick()) { discard(); return; }
        // the caster fell: the barrier collapses
        if (!ownerOk && !isInstant() && getCloseAt() > t + 1) dataTracker.set(CLOSE_AT, Math.max(D_OPEN, t + 1));

        if (isInstant()) {
            switch (t) {
                case 0 -> sound(world, ModSounds.DOMAIN_INSTANT, 3.0f, 1.0f);
                case I_VOID -> expand(world, owner, c);
                default -> {}
            }
            if (t >= I_END) end(owner, c);
            return;
        }
        switch (t) {
            case 0 -> sound(world, ModSounds.DOMAIN_SEAL, 2.0f, 1.0f);
            case D_WHITE -> sound(world, ModSounds.DOMAIN_WHITE, 3.0f, 1.0f);
            case D_INK -> {
                sound(world, ModSounds.DOMAIN_INK, 5.0f, 1.0f);
                expand(world, owner, c);
            }
            case D_TUNNEL -> sound(world, ModSounds.DOMAIN_TUNNEL, 5.0f, 1.0f);
            case D_FLASH -> sound(world, ModSounds.DOMAIN_OPEN, 6.0f, 1.0f);
            default -> {}
        }
        int close = getCloseAt();
        // collapsed early: the buffs go with it
        if (t == close && ownerOk && close < plannedClose) {
            int left = plannedClose - t + 10;
            dropBuff(owner, StatusEffects.STRENGTH, c.strength, left);
            dropBuff(owner, StatusEffects.SPEED, c.speed, left);
            dropBuff(owner, StatusEffects.RESISTANCE, c.resistance, left);
            dropBuff(owner, StatusEffects.REGENERATION, c.regeneration, left);
        }
        // sure-hit: anything inside the barrier (entering included) stays caught while it is up
        if (t > D_INK && t < close && t % 10 == 0) catchAll(world, owner, 45);
        if (t == close) {
            sound(world, ModSounds.DOMAIN_COLLAPSE, 5.0f, 1.0f);
            catchAll(world, owner, (int) Math.round(c.afterSeconds * 20) + 5);
        }
        if (t >= close + D_CLOSE_LEN) end(owner, c);
    }

    /** Gone: the caster may expand again after the cooldown. */
    // ------------------------------------------------------------------ the floor (cast in mid-air)
    private final java.util.List<net.minecraft.util.math.BlockPos> floor = new java.util.ArrayList<>();

    /** Cast in mid-air: an invisible floor under the caster across the domain, so no one inside falls through it. */
    private void layFloor(ServerWorld world, ServerPlayerEntity p) {
        if (p.isOnGround()) return;
        net.minecraft.util.math.BlockPos feet = p.getBlockPos();
        for (int dy = 1; dy <= 3; dy++) {                       // ground within a jump: they'll land on it anyway
            net.minecraft.util.math.BlockPos b = feet.down(dy);
            if (!world.getBlockState(b).getCollisionShape(world, b).isEmpty()) return;
        }
        int y = net.minecraft.util.math.MathHelper.floor(p.getY() + 1e-4) - 1;
        int R = (int) Math.ceil(getRadius());
        net.minecraft.block.BlockState f = com.gojolimitless.registry.ModBlocks.DOMAIN_FLOOR.getDefaultState();
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                if (dx * dx + dz * dz > R * R) continue;
                m.set(feet.getX() + dx, y, feet.getZ() + dz);
                if (!world.getBlockState(m).isAir()) continue;
                world.setBlockState(m, f, net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
                floor.add(m.toImmutable());
            }
    }

    private void clearFloor() {
        if (floor.isEmpty() || !(getWorld() instanceof ServerWorld world)) return;
        for (net.minecraft.util.math.BlockPos b : floor)
            if (world.getBlockState(b).isOf(com.gojolimitless.registry.ModBlocks.DOMAIN_FLOOR))
                world.setBlockState(b, net.minecraft.block.Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
        floor.clear();
    }

    /** Ended, cancelled or killed: the floor goes with it (unloaded: its blocks clean themselves up). */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) clearFloor();
        super.remove(reason);
    }

    private void end(ServerPlayerEntity owner, LimitlessConfig.Domain c) {
        if (owner != null) com.gojolimitless.ability.AbilityManager.state(owner).domainReadyAt =
                getWorld().getTime() + Math.round(c.cooldownSeconds * 20);
        discard();
    }

    /** The barrier closes: note who is touching the caster (left out), catch everyone else, empower the caster. */
    private void expand(ServerWorld world, ServerPlayerEntity owner, LimitlessConfig.Domain c) {
        if (owner != null && c.excludeTouching) {
            Box touch = owner.getBoundingBox().expand(0.35);
            for (Entity e : world.getOtherEntities(owner, touch.expand(1.5), x -> x instanceof LivingEntity)) {
                if (e.getBoundingBox().intersects(touch)) excluded.add(e.getUuid());
            }
        }
        if (owner != null) {
            int buff = isInstant() ? (int) Math.round(c.instantBuffSeconds * 20) : getCloseAt() - age + 10;
            if (buff > 0) {
                if (c.strength > 0) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, buff, c.strength - 1, false, false, true));
                if (c.speed > 0) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, buff, c.speed - 1, false, false, true));
                if (c.resistance > 0) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, buff, c.resistance - 1, false, false, true));
                if (c.regeneration > 0) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, buff, c.regeneration - 1, false, false, true));
            }
        }
        catchAll(world, owner, isInstant() ? (int) Math.round(c.instantParalysisSeconds * 20) : 45);
    }

    /** Take back a buff this domain gave (only if it is still ours: same level, no longer than we granted). */
    private static void dropBuff(ServerPlayerEntity p, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> fx, int level, int left) {
        StatusEffectInstance cur = p.getStatusEffect(fx);
        if (level > 0 && cur != null && cur.getAmplifier() == level - 1 && cur.getDuration() <= left + 2) p.removeStatusEffect(fx);
    }

    private static boolean caught(Entity e, LimitlessConfig.Domain c) {
        if (e instanceof net.minecraft.entity.player.PlayerEntity) return c.affectPlayers;
        if (e instanceof net.minecraft.entity.mob.Monster || boss(e)) return c.affectHostile;
        return c.affectPassive;
    }

    private static boolean boss(Entity e) {
        return e instanceof net.minecraft.entity.boss.WitherEntity || e instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity
                || e instanceof net.minecraft.entity.mob.WardenEntity || e instanceof net.minecraft.entity.mob.ElderGuardianEntity;
    }

    private void catchAll(ServerWorld world, ServerPlayerEntity owner, int ticks) {
        LimitlessConfig.Domain c = ConfigManager.get().domain;
        double r = getRadius();
        Vec3d o = getPos();
        Entity caster = owner != null ? owner : this;
        for (Entity e : world.getOtherEntities(caster, new Box(o, o).expand(r), x -> x instanceof LivingEntity)) {
            if (excluded.contains(e.getUuid()) || !Targets.affectable(e, caster)) continue;
            if (e.getPos().squaredDistanceTo(o) > r * r) continue;
            if (!caught(e, c)) continue;
            LivingEntity le = (LivingEntity) e;
            int dur = boss(e) ? (int) Math.max(20, ticks / c.bossRecovery) : ticks;
            StatusEffectInstance cur = le.getStatusEffect(ModEffects.UNLIMITED_VOID);
            if (cur != null && cur.getDuration() >= dur) continue;
            le.addStatusEffect(new StatusEffectInstance(ModEffects.UNLIMITED_VOID, dur, 0, false, false, true), owner);
        }
    }

    private void sound(ServerWorld world, SoundEvent s, float volume, float pitch) {
        world.playSound(null, getX(), getY() + 1, getZ(), s, SoundCategory.PLAYERS, volume, pitch);
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    @Override public boolean shouldRender(double distance) { return true; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(Math.max(64, getRadius() * 2.5)); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean isPushable() { return false; }
}
