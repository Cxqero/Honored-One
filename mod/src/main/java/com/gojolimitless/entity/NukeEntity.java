package com.gojolimitless.entity;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.destruction.DestructionEngine;
import com.gojolimitless.net.FxType;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModDamage;
import com.gojolimitless.registry.ModEntities;
import com.gojolimitless.registry.ModSounds;
import com.gojolimitless.util.ChunkKeeper;
import com.gojolimitless.util.Targets;
import com.gojolimitless.util.TechniqueTicker;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Remote Hollow Purple — "the nuke" (ch. 234–235). One entity, parked at the convergence point, runs the whole
 * cinematic timeline on the server (lift, convergence, erasure) while clients derive every visual from its age.
 *
 * Canon beats (ch. 235): Red's incantation (位相・波羅蜜・光の柱) as Red forms at the fingertip, and Red is fired into
 * the sky · a Blue is boosted by chanting its incantation
 * after the fact (位相・黄昏・智慧の瞳) · caster and target are airborne · the Purple incantation
 * (九綱・偏光・烏と声明・表裏の間) · Red and Blue converge on the target · the imaginary mass erases a huge stretch of
 * land · the caster, caught inside it, takes only minimal damage.
 *
 * "C space": origin at the convergence point, x = right, y = forward (caster → target), z = up.
 */
public class NukeEntity extends Entity implements TechniqueTicker.Tracked {
    // ---- timeline, ticks from the cast. Keep in sync with blender/cutscene/build_cutscenes.py and NukeClient.
    public static final int T_RED_FORM = 4, T_RED_1 = 12, T_RED_2 = 30, T_RED_3 = 48, T_SIGN = 60, T_THROW = 84, T_SKY = 140, T_BOOST = 160,
            T_BLUE_1 = 172, T_BLUE_2 = 194, T_BLUE_3 = 214, T_LIFT = 232, T_LEAP = 262, T_LEAP_END = 292,
            T_PURPLE_1 = 290, T_PURPLE_2 = 308, T_PURPLE_3 = 326, T_PURPLE_4 = 344, T_CONVERGE = 296,
            T_COLLIDE = 356, T_IMPLODE = 380, T_BLOOM = 396, T_GLINT = 462, T_WHITE = 470, T_END = 580;

    // ---- layout in C space (blocks)
    public static final Vec3d HOVER = new Vec3d(0, -16, -4);
    public static final Vec3d RED_HOLD = new Vec3d(22, 3, 9), BLUE_HOLD = new Vec3d(-22, 3, 9);
    public static final double RED_APEX = 38;

    /** The detonation's shock front: how far out along the ground it is `ticks` after T_WHITE (fast, then slowing). */
    public static double shockRadius(double blastRadius, double ticks) {
        return blastRadius * (0.5 + 6.0 * (1 - Math.exp(-Math.max(0, ticks) / 40.0)));
    }

    private static final TrackedData<Integer> OWNER = DataTracker.registerData(NukeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> YAW = DataTracker.registerData(NukeEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Vector3f> ORIGIN = DataTracker.registerData(NukeEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Vector3f> HOVER_POS = DataTracker.registerData(NukeEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(NukeEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> EXPAND = DataTracker.registerData(NukeEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private long lastServerTick = -1;
    private UUID ownerUuid;
    private final List<Lifted> lifted = new ArrayList<>();
    private final Set<Integer> hit = new HashSet<>();
    private boolean selfHit, released;

    private record Lifted(UUID id, Vec3d slot, boolean hadNoGravity) {}

    @Override public long lastServerTick() { return lastServerTick; }

    public NukeEntity(EntityType<? extends NukeEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
        if (!world.isClient) TechniqueTicker.register(this);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(OWNER, -1);
        b.add(YAW, 0f);
        b.add(ORIGIN, new Vector3f());
        b.add(HOVER_POS, new Vector3f());
        b.add(RADIUS, 64f);
        b.add(EXPAND, 60);
    }

    public int getOwnerId() { return dataTracker.get(OWNER); }
    public float getYawDeg() { return dataTracker.get(YAW); }
    public Vec3d getOrigin() { Vector3f v = dataTracker.get(ORIGIN); return new Vec3d(v.x, v.y, v.z); }
    public Vec3d getHover() { Vector3f v = dataTracker.get(HOVER_POS); return new Vec3d(v.x, v.y, v.z); }
    public float getBlastRadius() { return dataTracker.get(RADIUS); }
    public int getExpandTicks() { return dataTracker.get(EXPAND); }

    // ------------------------------------------------------------------ geometry shared by server and client
    public static Vec3d fwd(float yawDeg) {
        float y = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
    }

    public static Vec3d right(float yawDeg) {
        float y = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.cos(y), 0, -MathHelper.sin(y));
    }

    public static Vec3d toWorld(Vec3d c, float yawDeg, Vec3d cs) {
        return c.add(right(yawDeg).multiply(cs.x)).add(fwd(yawDeg).multiply(cs.y)).add(0, cs.z, 0);
    }

    public Vec3d cs(Vec3d local) { return toWorld(getPos(), getYawDeg(), local); }

    private static double smooth(double e0, double e1, double x) {
        double t = MathHelper.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Radius of the erasure at time t (0 before the bloom). Fast start, easing into its full size. */
    public float blastRadiusAt(float t) {
        if (t < T_BLOOM) return 0f;
        float k = MathHelper.clamp((t - T_BLOOM) / (float) getExpandTicks(), 0f, 1f);
        return getBlastRadius() * (1f - (float) Math.pow(1f - k, 2.6f));
    }

    /** Red, from the fingertip (handPos) up into the sky, holding, then rushing in. */
    public Vec3d redPos(float t, Vec3d handPos) {
        Vec3d hold = cs(RED_HOLD).add(0, 0.6 * Math.sin(t * 0.07), 0);
        if (t < T_THROW) return handPos;
        Vec3d apex = getOrigin().add(0, RED_APEX, 0).add(fwd(getYawDeg()).multiply(4));
        if (t < T_THROW + 26) {
            double k = (t - T_THROW) / 26.0;
            double e = 1 - (1 - k) * (1 - k);
            return handPos.lerp(apex, e);
        }
        if (t < T_SKY) return apex.lerp(hold, smooth(T_THROW + 26, T_SKY - 2, t));
        return convergeFrom(hold, t, +1);
    }

    /** Blue, formed high on the far side at T_SKY, boosted by its incantation, then rushing in. */
    public Vec3d bluePos(float t) {
        Vec3d hold = cs(BLUE_HOLD).add(0, 0.6 * Math.sin(t * 0.06 + 2), 0);
        return convergeFrom(hold, t, -1);
    }

    private Vec3d convergeFrom(Vec3d hold, float t, int side) {
        if (t < T_CONVERGE) return hold;
        double k = MathHelper.clamp((t - T_CONVERGE) / (double) (T_COLLIDE - T_CONVERGE), 0, 1);
        double e = Math.pow(k, 2.4);
        Vec3d meet = cs(new Vec3d(side * 0.6, 0, 0));
        return hold.lerp(meet, e).add(0, Math.sin(Math.PI * e) * 3.0, 0);
    }

    /** Visual radius of Red at t. */
    public float redSize(float t) {
        if (t < T_THROW) return 0.07f + 0.06f * (float) smooth(T_RED_FORM, T_SIGN, t);
        if (t < T_SKY) return 0.13f + 1.07f * (float) smooth(T_THROW, T_THROW + 26, t);
        if (t < T_CONVERGE) return 2.2f;
        return 2.2f + 0.9f * (float) smooth(T_CONVERGE, T_COLLIDE, t);
    }

    /** Visual radius of Blue at t (0 before it forms). */
    public float blueSize(float t) {
        if (t < T_SKY) return 0f;
        float f = (float) smooth(T_SKY, T_SKY + 10, t) * 2.3f;
        f += 0.45f * (float) (smooth(T_BLUE_1, T_BLUE_1 + 6, t) + smooth(T_BLUE_2, T_BLUE_2 + 6, t) + smooth(T_BLUE_3, T_BLUE_3 + 6, t));
        return f + 0.6f * (float) smooth(T_CONVERGE, T_COLLIDE, t);
    }

    // ------------------------------------------------------------------ cast
    /** Pick the target, lay out the technique and start the timeline. Returns null if it can't be cast here. */
    public static NukeEntity cast(ServerWorld world, ServerPlayerEntity p) {
        LimitlessConfig.Nuke c = ConfigManager.get().nuke;
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        double max = c.maxDistance;
        BlockHitResult bh = world.raycast(new RaycastContext(eye, eye.add(look.multiply(max)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double wall = bh.getType() == HitResult.Type.MISS ? max : bh.getPos().distanceTo(eye);

        // creature under (or near) the crosshair, in sight
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        Box search = p.getBoundingBox().stretch(look.multiply(max)).expand(6);
        for (Entity e : world.getOtherEntities(p, search, e -> e instanceof LivingEntity && !(e instanceof PlayerEntity) && Targets.affectable(e, p))) {
            Vec3d v = e.getBoundingBox().getCenter().subtract(eye);
            double along = v.dotProduct(look);
            if (along <= 2 || along > wall + 3 || along > max) continue;
            double perp = v.subtract(look.multiply(along)).length();
            double tol = 1.2 + e.getWidth() + along * 0.045;
            if (perp > tol) continue;
            double score = perp / along;
            if (score < best) { best = score; target = (LivingEntity) e; }
        }
        Vec3d focus = target != null ? target.getPos()
                : bh.getType() == HitResult.Type.MISS ? eye.add(look.multiply(Math.min(max, 60))) : bh.getPos();

        Vec3d flat = new Vec3d(focus.x - p.getX(), 0, focus.z - p.getZ());
        double dist = flat.length();
        Vec3d dir = dist > 1e-3 ? flat.multiply(1 / dist) : new Vec3d(look.x, 0, look.z).normalize();
        double clamped = MathHelper.clamp(dist, c.minDistance, c.maxDistance);
        Vec3d fxz = p.getPos().add(dir.multiply(clamped));
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(fxz.x), MathHelper.floor(fxz.z));
        double groundY = Math.abs(clamped - dist) > 1.5 || target == null && bh.getType() == HitResult.Type.MISS ? top : focus.y;
        // mountains: lift the whole layout (Red and Blue 22 blocks out to the sides, the caster behind, the cameras below)
        // clear of the terrain around the convergence point, or it plays out inside the hillside
        int hills = top;
        for (int dx = -32; dx <= 32; dx += 4)
            for (int dz = -32; dz <= 32; dz += 4)
                if (dx * dx + dz * dz <= 32 * 32)
                    hills = Math.max(hills, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(fxz.x) + dx, MathHelper.floor(fxz.z) + dz));
        double cy = Math.max(Math.max(groundY + c.liftHeight, top + 6), hills + 12);
        cy = Math.min(cy, world.getTopY() - 8);
        Vec3d C = new Vec3d(fxz.x, cy, fxz.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

        // the caster hovers behind and below the convergence point, in open air
        Vec3d hover = toWorld(C, yaw, HOVER);
        for (int i = 0; i < 48; i++) {
            Box b = p.getDimensions(p.getPose()).getBoxAt(hover).expand(0.4);
            if (world.isSpaceEmpty(p, b)) break;
            hover = hover.add(0, 1, 0);
        }

        NukeEntity e = new NukeEntity(ModEntities.NUKE, world);
        e.ownerUuid = p.getUuid();
        e.setPosition(C);
        e.dataTracker.set(OWNER, p.getId());
        e.dataTracker.set(YAW, yaw);
        e.dataTracker.set(ORIGIN, new Vector3f((float) p.getX(), (float) p.getY(), (float) p.getZ()));
        e.dataTracker.set(HOVER_POS, new Vector3f((float) hover.x, (float) hover.y, (float) hover.z));
        e.dataTracker.set(RADIUS, (float) c.radius);
        e.dataTracker.set(EXPAND, Math.max(10, (int) Math.round(c.expandSeconds * 20)));

        // who gets caught: the target and the creatures around it
        List<LivingEntity> caught = new ArrayList<>();
        if (target != null) caught.add(target);
        if (c.liftRadius > 0) {
            Vec3d around = target != null ? target.getPos() : new Vec3d(fxz.x, groundY, fxz.z);
            List<Entity> near = world.getOtherEntities(p, new Box(around, around).expand(c.liftRadius),
                    x -> x instanceof LivingEntity && !(x instanceof PlayerEntity) && Targets.affectable(x, p) && x != null);
            near.sort((a, b) -> Double.compare(a.squaredDistanceTo(around), b.squaredDistanceTo(around)));
            for (Entity x : near) if (!caught.contains(x) && x.squaredDistanceTo(around) <= c.liftRadius * c.liftRadius) caught.add((LivingEntity) x);
        }
        java.util.Random rnd = new java.util.Random(p.getId() * 31L + world.getTime());
        for (int i = 0; i < Math.min(caught.size(), c.maxLifted); i++) {
            LivingEntity x = caught.get(i);
            Vec3d slot = i == 0 ? new Vec3d(0, -x.getHeight() * 0.5, 0)
                    : new Vec3d((rnd.nextDouble() - 0.5) * 9, (rnd.nextDouble() - 0.5) * 4 - x.getHeight() * 0.5, (rnd.nextDouble() - 0.5) * 9);
            e.lifted.add(new Lifted(x.getUuid(), slot, x.hasNoGravity()));
        }

        world.spawnEntity(e);
        if (ConfigManager.get().client.cutscenes)
            ServerPlayNetworking.send(p, new Payloads.Cutscene("nuke_ground", p.getX(), p.getY(), p.getZ(), yaw, p.getId()));
        return e;
    }

    /** True while this player's nuke is running (other techniques are locked out). */
    public static boolean busy(ServerPlayerEntity p, int id) {
        return id >= 0 && p.getWorld().getEntityById(id) instanceof NukeEntity n && !n.isRemoved() && n.age < T_END;
    }

    // ------------------------------------------------------------------ server timeline
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerWorld world = (ServerWorld) getWorld();
        lastServerTick = world.getTime();
        ServerPlayerEntity owner = ownerUuid != null && world.getPlayerByUuid(ownerUuid) instanceof ServerPlayerEntity sp ? sp : null;
        boolean ownerOk = owner != null && owner.isAlive() && !owner.isRemoved();
        int t = age;
        LimitlessConfig.Nuke c = ConfigManager.get().nuke;

        if (!ownerOk && t < T_BLOOM) { finish(world, null); discard(); return; }
        ChunkKeeper.keep(world, getPos(), Math.min(12, 2 + (int) Math.ceil(getBlastRadius() / 16.0)));
        if (ownerOk && t <= T_END + 40) owner.fallDistance = 0;

        Vec3d o = getOrigin();
        switch (t) {
            case T_RED_FORM -> {
                sound(world, ownerOk ? owner.getEyePos() : o, ModSounds.RED_FORM, 2.0f, 0.85f);
                if (c.destroy && DestructionEngine.allowed(world)) flattenPlants(world, ownerOk ? owner.getBlockPos() : BlockPos.ofFloored(o), 4);
            }
            case T_THROW -> {
                sound(world, ownerOk ? owner.getEyePos() : o, ModSounds.NUKE_THROW, 3.0f, 1.0f);
                fx(world, FxType.RED_FIRE, ownerOk ? owner.getEyePos() : o, 1f, 0f);
            }
            case T_SKY -> {
                if (ownerOk && ConfigManager.get().client.cutscenes)
                    ServerPlayNetworking.send(owner, new Payloads.Cutscene("nuke_sky", getX(), getY(), getZ(), getYawDeg(), owner.getId()));
                Vec3d b = cs(BLUE_HOLD);
                sound(world, b, ModSounds.BLUE_FORM, 7.0f, 0.8f);
                fx(world, FxType.BLUE_FORM, b, 3.0f, 0f);
            }
            case T_BOOST -> sound(world, ownerOk ? owner.getPos() : o, ModSounds.NUKE_BOOST, 5.0f, 1.0f);
            case T_LEAP -> sound(world, ownerOk ? owner.getPos() : o, ModSounds.BLUE_THROW, 3.0f, 1.35f);
            case T_CONVERGE -> sound(world, getPos(), ModSounds.NUKE_CONVERGE, 9.0f, 1.0f);
            case T_COLLIDE -> {
                sound(world, getPos(), ModSounds.NUKE_COLLIDE, 10.0f, 1.0f);
                fx(world, FxType.NUKE_COLLIDE, getPos(), 1f, 0f);
            }
            case T_BLOOM -> {
                sound(world, getPos(), ModSounds.NUKE_BLOOM, 18.0f, 1.0f);
                fx(world, FxType.NUKE_BLOOM, getPos(), getBlastRadius(), 0f);
            }
            case T_GLINT -> sound(world, ownerOk ? owner.getPos() : getPos(), ModSounds.NUKE_GLINT, 3.0f, 1.0f);
            case T_WHITE -> {
                // the purple mass detonates: fireball, shockwave, smoke
                sound(world, getPos(), ModSounds.NUKE_EXPLOSION, 24.0f, 1.0f);
                fx(world, FxType.NUKE_EXPLODE, getPos(), getBlastRadius(), 0f);
            }
            case T_END -> {
                sound(world, getPos(), ModSounds.NUKE_AFTERMATH, 10.0f, 1.0f);
                finish(world, owner);
            }
            default -> {}
        }

        if (t >= T_LIFT && t < T_BLOOM) holdCaught(world, t);
        if (t >= T_BLOOM) blast(world, owner, ownerOk, t, c);
        if (t > T_WHITE && t <= T_WHITE + 140) shockwave(world, owner, t - T_WHITE);

        boolean erased = !c.destroy || eraseDone || !DestructionEngine.allowed(world);
        if (t > T_END + 40 && erased) discard();
        if (t > T_END + 20 * 60) discard();            // safety: never linger more than a minute
    }

    /**
     * Red's pressure flattens the grass and flowers around the caster as it ignites (and keeps the cinematic's low
     * camera from staring into a wall of tall grass). Only wild plants: crops and saplings are left alone.
     */
    public static void flattenPlants(ServerWorld world, BlockPos feet, int r) {
        for (BlockPos p : BlockPos.iterate(feet.add(-r, -1, -r), feet.add(r, 2, r))) {
            int dx = p.getX() - feet.getX(), dz = p.getZ() - feet.getZ();
            if (dx * dx + dz * dz > r * r + 1) continue;
            net.minecraft.block.BlockState st = world.getBlockState(p);
            net.minecraft.block.Block b = st.getBlock();
            boolean wild = b instanceof net.minecraft.block.ShortPlantBlock || b instanceof net.minecraft.block.TallPlantBlock
                    || b instanceof net.minecraft.block.FlowerBlock || b instanceof net.minecraft.block.DeadBushBlock;
            if (!wild || b instanceof net.minecraft.block.CropBlock || b instanceof net.minecraft.block.SaplingBlock) continue;
            world.breakBlock(p.toImmutable(), false);
        }
    }

    private void holdCaught(ServerWorld world, int t) {
        double k = smooth(T_LIFT, T_LIFT + 40, t);
        for (Lifted l : lifted) {
            if (!(world.getEntity(l.id) instanceof LivingEntity e) || !e.isAlive()) continue;
            if (t == T_LIFT) e.setNoGravity(true);
            Vec3d slot = getPos().add(l.slot).add(0, 0.5 * Math.sin(t * 0.09 + l.slot.x), 0);
            Vec3d d = slot.subtract(e.getPos());
            double step = Math.min(d.length(), 0.3 + 1.4 * k);
            Vec3d move = d.lengthSquared() > 1e-6 ? d.normalize().multiply(step * (0.25 + 0.75 * k)) : Vec3d.ZERO;
            e.setVelocity(move);
            e.velocityModified = true;
            e.fallDistance = 0;
            if (e instanceof MobEntity m) m.getNavigation().stop();
        }
    }

    private void release(ServerWorld world) {
        if (released) return;
        released = true;
        for (Lifted l : lifted) {
            if (world.getEntity(l.id) instanceof LivingEntity e && e.isAlive()) {
                e.setNoGravity(l.hadNoGravity);
                e.fallDistance = 0;
            }
        }
    }

    private void finish(ServerWorld world, ServerPlayerEntity owner) {
        release(world);
        if (owner != null) {
            double s = ConfigManager.get().nuke.slowFallSeconds;
            if (s > 0) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, (int) (s * 20), 0, false, false, true));
            owner.fallDistance = 0;
            com.gojolimitless.ability.AbilityManager.broadcastStage(owner, com.gojolimitless.ability.MoveType.NUKE,
                    com.gojolimitless.ability.CastStage.END, 0);
        }
    }

    // ------------------------------------------------------------------ the erasure
    private void blast(ServerWorld world, ServerPlayerEntity owner, boolean ownerOk, int t, LimitlessConfig.Nuke c) {
        float r = blastRadiusAt(t);
        if (t == T_BLOOM) release(world);
        Vec3d C = getPos();
        Entity caster = ownerOk ? owner : this;
        if (r > 0.5f) {
            // everything the mass reaches stops existing
            for (Entity e : world.getOtherEntities(this, new Box(C, C).expand(r), x -> x != caster)) {
                if (hit.contains(e.getId())) continue;
                if (e.getBoundingBox().getCenter().squaredDistanceTo(C) > (double) r * r) continue;
                if (e instanceof PlayerEntity pl && (pl.isCreative() || pl.isSpectator())) continue;
                if (!Targets.affectable(e, caster)) continue;
                hit.add(e.getId());
                if (e instanceof LivingEntity le) {
                    le.timeUntilRegen = 0;
                    le.damage(ModDamage.source(world, ModDamage.HOLLOW_PURPLE, this, ownerOk ? owner : null), (float) c.damage);
                } else if (!(e instanceof PlayerEntity)) {
                    e.discard();
                }
            }
            // the caster is caught in it too — canon: minimal damage, and never lethal here
            if (ownerOk && !selfHit && owner.getPos().squaredDistanceTo(C) <= (double) r * r) {
                selfHit = true;
                if (!owner.isCreative() && c.selfDamage > 0) {
                    float dmg = (float) Math.min(c.selfDamage, owner.getHealth() - 1.0);
                    if (dmg > 0) {
                        owner.timeUntilRegen = 0;
                        owner.damage(ModDamage.source(world, ModDamage.HOLLOW_PURPLE, this, owner), dmg);
                    }
                }
            }
        }
        if (c.destroy && DestructionEngine.allowed(world)) eraseStep(world, r, c.blocksPerTick);
    }

    // Budgeted shell sweep: columns sorted by horizontal distance; each pass erases the shell (rDone, shellEnd].
    private int[] cols, colH;
    private double rDone = 0, shellEnd = -1;
    private int cursor;
    private boolean eraseDone;

    private void buildColumns() {
        int R = (int) Math.ceil(getBlastRadius());
        List<int[]> l = new ArrayList<>();
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                int h = x * x + z * z;
                if (h <= R * R) l.add(new int[]{x, z, h});
            }
        l.sort((a, b) -> Integer.compare(a[2], b[2]));
        cols = new int[l.size() * 2];
        colH = new int[l.size()];
        for (int i = 0; i < l.size(); i++) {
            cols[i * 2] = l.get(i)[0]; cols[i * 2 + 1] = l.get(i)[1]; colH[i] = l.get(i)[2];
        }
    }

    private void eraseStep(ServerWorld world, double rNow, int budget) {
        if (eraseDone) return;
        if (cols == null) buildColumns();
        int checks = budget * 6;
        int cx = MathHelper.floor(getX()), cy = MathHelper.floor(getY()), cz = MathHelper.floor(getZ());
        BlockPos.Mutable m = new BlockPos.Mutable();
        while (budget > 0 && checks > 0) {
            if (shellEnd < 0) {
                if (rNow <= rDone + 1e-3) {
                    if (rDone >= getBlastRadius() - 1e-3) eraseDone = true;
                    return;
                }
                shellEnd = rNow;
                cursor = 0;
            }
            double lo2 = rDone * rDone, hi2 = shellEnd * shellEnd;
            int n = colH.length;
            while (cursor < n) {
                int h = colH[cursor];
                if (h > hi2) { cursor = n; break; }
                int dx = cols[cursor * 2], dz = cols[cursor * 2 + 1];
                int b = (int) Math.floor(Math.sqrt(hi2 - h));
                int a = lo2 - h < 0 ? 0 : (int) Math.floor(Math.sqrt(lo2 - h)) + 1;
                int x = cx + dx, z = cz + dz;
                cursor++;
                if (a > b) continue;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int dy = a; dy <= b; dy++) {
                    for (int s = 0; s < (dy == 0 ? 1 : 2); s++) {
                        int y = s == 0 ? cy + dy : cy - dy;
                        checks--;
                        if (y < world.getBottomY() || y >= world.getTopY()) continue;
                        m.set(x, y, z);
                        if (world.getBlockState(m).isAir()) continue;
                        if (DestructionEngine.eraseAt(world, m)) budget--;
                    }
                }
                if (budget <= 0 || checks <= 0) return;
            }
            rDone = shellEnd;
            shellEnd = -1;
        }
    }

    // ------------------------------------------------------------------ helpers
    private void sound(ServerWorld world, Vec3d at, SoundEvent s, float vol, float pitch) {
        world.playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    private void fx(ServerWorld world, int type, Vec3d at, float a, float b) {
        Payloads.Fx pkt = new Payloads.Fx(type, at.x, at.y, at.z, a, b, getId());
        for (ServerPlayerEntity pl : PlayerLookup.around(world, at, 640)) ServerPlayNetworking.send(pl, pkt);
    }

    @Override public boolean shouldRender(double distance) { return true; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(Math.max(80, getBlastRadius() * 1.6)); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @SuppressWarnings("unused")
    private static String dbg(int[] a) { return Arrays.toString(a); }

    /** The shock front sweeping out along the ground throws everything it passes (the caster rides it out). */
    private void shockwave(ServerWorld world, Entity owner, int dt) {
        double R = getBlastRadius(), r0 = shockRadius(R, dt - 1), r1 = shockRadius(R, dt), max = R * 6.5;
        Vec3d c = getPos();
        for (Entity e : world.getOtherEntities(this, new Box(c, c).expand(r1, R * 1.5, r1), x -> x != owner && x.isAlive())) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < r0 || d > r1 || d < 1e-3) continue;
            double k = Math.max(0, 1 - d / max);
            e.setVelocity(e.getVelocity().add(dx / d * (0.6 + 2.4 * k), 0.35 + 0.8 * k, dz / d * (0.6 + 2.4 * k)));
            e.velocityModified = true;
            if (e instanceof LivingEntity le && Targets.affectable(e, owner != null ? owner : this))
                le.damage(ModDamage.source(world, ModDamage.HOLLOW_PURPLE, this, owner), (float) (4 + 16 * k));
        }
    }
}
