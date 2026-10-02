package com.gojolimitless.entity;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.destruction.DestructionEngine;
import com.gojolimitless.net.FxType;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModDamage;
import com.gojolimitless.registry.ModEntities;
import com.gojolimitless.registry.ModSounds;
import com.gojolimitless.util.Targets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 術式反転「赫」 Cursed Technique Reversal: Red. Positive energy through Limitless: repulsion.
 * CHARGING — held at the caster's fingertip while the incantation is spoken (power 0..1, stages 1..3).
 * FLYING   — fired along the crosshair; a charged Red drills straight through terrain and people.
 * On impact / end of range it detonates, blasting everything outward.
 */
public class RedOrbEntity extends Entity implements com.gojolimitless.util.TechniqueTicker.Tracked {
    private long lastServerTick = -1;

    @Override public long lastServerTick() { return lastServerTick; }
    public static final int MODE_CHARGING = 0, MODE_FLYING = 1, MODE_DONE = 2;

    private static final TrackedData<Integer> MODE = DataTracker.registerData(RedOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(RedOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> POWER = DataTracker.registerData(RedOrbEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(RedOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> CHARGED = DataTracker.registerData(RedOrbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private UUID ownerUuid;
    private int autoFireAt = -1;
    private Vec3d dir = Vec3d.ZERO;
    private double speed, range, traveled, pierceLeft, tunnelR, targetDist = Double.MAX_VALUE;
    private final Set<UUID> pierced = new HashSet<>();

    // client-side trail
    public final Vec3d[] trail = new Vec3d[18];
    public int trailHead = -1;

    public RedOrbEntity(EntityType<? extends RedOrbEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
        if (!world.isClient) com.gojolimitless.util.TechniqueTicker.register(this);
    }

    public static RedOrbEntity create(ServerWorld world, PlayerEntity owner, boolean charged) {
        RedOrbEntity e = new RedOrbEntity(ModEntities.RED_ORB, world);
        e.ownerUuid = owner.getUuid();
        e.dataTracker.set(OWNER, owner.getId());
        e.dataTracker.set(CHARGED, charged);
        e.setPosition(handPos(owner));
        if (!charged) e.autoFireAt = 4;          // tap: forms at the fingertip for a beat, then flies
        world.spawnEntity(e);
        world.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.RED_FORM, SoundCategory.PLAYERS, charged ? 2.0f : 1.4f, charged ? 0.9f : 1.1f);
        return e;
    }

    /** Right index fingertip, roughly: in front of the eyes, offset to the right and down. */
    public static Vec3d handPos(PlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        return p.getEyePos().add(look.multiply(1.1)).add(right.multiply(0.38)).add(0, -0.22, 0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(MODE, MODE_CHARGING);
        b.add(OWNER, -1);
        b.add(POWER, 0f);
        b.add(STAGE, 0);
        b.add(CHARGED, false);
    }

    public int getMode() { return dataTracker.get(MODE); }
    public float getPower() { return dataTracker.get(POWER); }
    public int getStage() { return dataTracker.get(STAGE); }
    public boolean isCharged() { return dataTracker.get(CHARGED); }
    public int getOwnerId() { return dataTracker.get(OWNER); }

    /** Visual/physical radius of the orb itself. */
    public float coreRadius() { return isCharged() ? 0.35f + 0.6f * getPower() : 0.36f; }

    private Vec3d getVelocityForTickets() { return getMode() == MODE_FLYING ? dir.multiply(speed) : Vec3d.ZERO; }

    public void setPower(float p) { dataTracker.set(POWER, MathHelper.clamp(p, 0f, 1f)); }

    public boolean setStage(int s) {
        if (s == getStage()) return false;
        dataTracker.set(STAGE, s);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            trailHead = (trailHead + 1) % trail.length;
            trail[trailHead] = getPos();
            return;
        }
        ServerWorld world = (ServerWorld) getWorld();
        Entity owner = ownerUuid != null ? world.getEntity(ownerUuid) : null;
        lastServerTick = world.getTime();
        com.gojolimitless.util.ChunkKeeper.keepAhead(world, getPos(), getVelocityForTickets(), 3);
        switch (getMode()) {
            case MODE_CHARGING -> {
                if (!(owner instanceof PlayerEntity p) || !p.isAlive()) { discard(); return; }
                setPosition(handPos(p));
                if (autoFireAt > 0 && age >= autoFireAt) fire(p);
                // the gathering force already shoves things away from the caster's hand
                if (isCharged()) repel(world, owner, getPos(), 2.0 + 4.0 * getPower(), 0.08 + 0.2 * getPower(), false);
            }
            case MODE_FLYING -> flyTick(world, owner);
            default -> discard();
        }
    }

    /** Release: fly toward the crosshair. */
    public void fire(PlayerEntity p) {
        if (getMode() != MODE_CHARGING) return;
        LimitlessConfig.Red c = ConfigManager.get().red;
        float pw = isCharged() ? getPower() : 0f;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        double maxR = isCharged() ? c.fullRange : c.tapRange;
        HitResult aim = p.getWorld().raycast(new RaycastContext(eye, eye.add(look.multiply(maxR)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d target = aim.getType() == HitResult.Type.MISS ? eye.add(look.multiply(maxR)) : aim.getPos();
        dir = target.subtract(getPos()).normalize();
        // a chanted Red bursts exactly where it was aimed, boring through anything on the way there
        targetDist = aim.getType() == HitResult.Type.MISS ? maxR : target.distanceTo(getPos());
        speed = (isCharged() ? MathHelper.lerp(pw, c.tapSpeed, c.fullSpeed) : c.tapSpeed) / 20.0;
        range = maxR;
        pierceLeft = isCharged() ? c.pierceBlocks * pw : 0;
        tunnelR = isCharged() ? c.fullTunnelRadius * (0.4 + 0.6 * pw) : 0;
        dataTracker.set(MODE, MODE_FLYING);
        ServerWorld w = (ServerWorld) getWorld();
        w.playSound(null, getX(), getY(), getZ(), ModSounds.RED_FIRE, SoundCategory.PLAYERS, 2.5f + 3f * pw, 1.0f - 0.2f * pw);
        fx(w, FxType.RED_FIRE, pw, 0);
    }

    private void flyTick(ServerWorld world, Entity owner) {
        LimitlessConfig.Red c = ConfigManager.get().red;
        float pw = isCharged() ? getPower() : 0f;
        Vec3d from = getPos();
        Vec3d to = from.add(dir.multiply(speed));
        Entity caster = owner != null ? owner : this;

        // people in the way
        Box sweep = new Box(from, to).expand(coreRadius() + 0.6);
        for (Entity e : world.getOtherEntities(this, sweep, e -> Targets.affectable(e, caster) && !pierced.contains(e.getUuid()))) {
            if (e.getBoundingBox().expand(coreRadius() + 0.3).raycast(from, to).isEmpty()) continue;
            if (!isCharged()) { setPosition(e.getBoundingBox().getCenter()); detonate(world, owner); return; }
            pierced.add(e.getUuid());
            if (e instanceof LivingEntity) {
                e.timeUntilRegen = 0;
                e.damage(ModDamage.source(world, ModDamage.REVERSAL_RED, this, owner), (float) (c.fullDamage * 0.35 * pw));
            }
            Vec3d side = e.getPos().subtract(from).subtract(dir.multiply(e.getPos().subtract(from).dotProduct(dir)));
            Vec3d push = dir.multiply(1.6 + 2.5 * pw).add(side.lengthSquared() > 1e-4 ? side.normalize().multiply(1.2) : Vec3d.ZERO).add(0, 0.6, 0);
            e.setVelocity(e.getVelocity().add(push)); e.velocityModified = true;
        }

        // terrain in the way
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (pierceLeft <= 0) {
                setPosition(hit.getPos().subtract(dir.multiply(0.6)));
                detonate(world, owner);
                return;
            }
            // drill: carve a tunnel along this step and keep going
            double stepLen = to.distanceTo(from);
            double solid = to.distanceTo(hit.getPos());
            pierceLeft -= solid;
            if (c.fullDestroy) {
                for (double d = 0; d <= stepLen; d += Math.max(1.0, tunnelR * 0.8)) {
                    DestructionEngine.carveSphere(world, from.add(dir.multiply(d)), tunnelR, DestructionEngine.Mode.BLAST, getId());
                }
            }
        }
        if (hit.getType() != HitResult.Type.BLOCK && isCharged() && tunnelR > 0 && c.fullDestroy) {
            // even in the open, a charged Red gouges whatever it grazes
            DestructionEngine.carveSphere(world, to, tunnelR * 0.8, DestructionEngine.Mode.BLAST, getId());
        }

        setPosition(to);
        traveled += speed;
        // wake: shove things aside as it passes
        repel(world, owner, to, 2.5 + 5.0 * pw, 0.35 + 0.8 * pw, true);
        if (age % 6 == 0) world.playSound(null, getX(), getY(), getZ(), ModSounds.RED_FLY, SoundCategory.PLAYERS, 1.2f + pw * 2f, 0.9f + 0.2f * (1 - pw));
        if (traveled >= range || traveled >= targetDist) {
            if (traveled > targetDist) setPosition(from.add(dir.multiply(targetDist - (traveled - speed))));
            detonate(world, owner);
        }
    }

    private void repel(ServerWorld world, Entity owner, Vec3d c, double radius, double strength, boolean sideways) {
        Entity caster = owner != null ? owner : this;
        for (Entity e : world.getOtherEntities(this, new Box(c, c).expand(radius), e -> Targets.affectable(e, caster))) {
            Vec3d away = e.getBoundingBox().getCenter().subtract(c);
            if (sideways) away = away.subtract(dir.multiply(away.dotProduct(dir)));
            double d = away.length();
            if (d > radius || d < 1e-3) continue;
            double k = strength * (1 - d / radius);
            e.setVelocity(e.getVelocity().add(away.multiply(k / d)).add(0, k * 0.25, 0));
            e.velocityModified = true;
        }
    }

    /** The repulsion blast. */
    private void detonate(ServerWorld world, Entity owner) {
        LimitlessConfig.Red c = ConfigManager.get().red;
        boolean charged = isCharged();
        float pw = charged ? getPower() : 0f;
        double R = charged ? MathHelper.lerp(pw, c.tapBlastRadius * 1.3, c.fullBlastRadius) : c.tapBlastRadius;
        double kb = charged ? MathHelper.lerp(pw, c.tapKnockback, c.fullKnockback) : c.tapKnockback;
        double dmg = charged ? MathHelper.lerp(pw, c.tapDamage * 1.5, c.fullDamage) : c.tapDamage;
        Vec3d p = getPos();
        com.gojolimitless.GojoLimitless.LOG.debug("Red detonates at {} radius {} (charged={}, power={})", p, R, charged, pw);
        Entity caster = owner != null ? owner : this;
        double reach = R * 2.6;
        for (Entity e : world.getOtherEntities(this, new Box(p, p).expand(reach), e -> Targets.affectable(e, caster))) {
            Vec3d away = e.getBoundingBox().getCenter().subtract(p);
            double d = away.length();
            if (d > reach) continue;
            double f = Math.pow(1 - d / reach, 0.7);
            Vec3d n = d < 1e-3 ? new Vec3d(0, 1, 0) : away.multiply(1 / d);
            e.setVelocity(e.getVelocity().add(n.multiply(kb * f)).add(0, 0.35 * kb * f, 0));
            e.velocityModified = true;
            if (e instanceof LivingEntity && d < R * 1.6) {
                e.timeUntilRegen = 0;
                e.damage(ModDamage.source(world, ModDamage.REVERSAL_RED, this, owner), (float) (dmg * (1 - d / (R * 1.6)) + dmg * 0.15));
            }
        }
        if (charged ? c.fullDestroy : c.tapDestroy) {
            DestructionEngine.carveSphere(world, p, R, DestructionEngine.Mode.BLAST, -1);
        }
        world.playSound(null, p.x, p.y, p.z, R > 10 ? ModSounds.RED_DETONATE_BIG : ModSounds.RED_DETONATE, SoundCategory.PLAYERS, (float) (3 + R * 0.5), 1.0f);
        fx(world, FxType.RED_DETONATE, (float) R, pw);
        dataTracker.set(MODE, MODE_DONE);
        discard();
    }

    private void fx(ServerWorld world, int type, float a, float b) {
        Payloads.Fx pkt = new Payloads.Fx(type, getX(), getY(), getZ(), a, b, getId());
        for (ServerPlayerEntity pl : PlayerLookup.around(world, getPos(), 400)) ServerPlayNetworking.send(pl, pkt);
    }

    @Override public boolean shouldRender(double distance) { return distance < 600 * 600; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(isCharged() ? 64 : 6); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
