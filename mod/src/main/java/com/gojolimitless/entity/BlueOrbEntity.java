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
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Cursed Technique Lapse: Blue. Space converges on this point: everything nearby is pulled in and crushed.
 * Modes: TAP (placed singularity), ORBIT (Maximum Output, circling the caster while the key is held),
 * THROWN (Maximum Output released), COLLAPSING (final implosion).
 */
public class BlueOrbEntity extends Entity implements com.gojolimitless.util.TechniqueTicker.Tracked {
    private long lastServerTick = -1;

    @Override public long lastServerTick() { return lastServerTick; }
    public static final int MODE_TAP = 0, MODE_ORBIT = 1, MODE_THROWN = 2, MODE_COLLAPSING = 3, MODE_RAISING = 4;
    /** Maximum Output let go: brought up over the caster's head (ticks), held there a beat, then it disperses. */
    public static final int RAISE_TICKS = 16, HOLD_TICKS = 8;
    public static final int COLLAPSE_TICKS = 12;

    private static final TrackedData<Integer> MODE = DataTracker.registerData(BlueOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(BlueOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(BlueOrbEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> PULL = DataTracker.registerData(BlueOrbEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> COLLAPSE_START = DataTracker.registerData(BlueOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> CHARGE = DataTracker.registerData(BlueOrbEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // server-side state
    private UUID ownerUuid;
    private int lifetime;
    private double orbitAngle;
    private Vec3d throwDir = Vec3d.ZERO;
    private double traveled;
    private int swingTicks;              // after release: swing around to the front before launching
    private double swingTarget;
    private int prevMode = -1;
    // client-side smoothing for rendering
    public float renderRadius = -1, prevRenderRadius = -1;
    /** Last positions (client), newest at trailHead, for the motion trail. */
    public final Vec3d[] trail = new Vec3d[14];
    public int trailHead = -1;

    public BlueOrbEntity(EntityType<? extends BlueOrbEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
        if (!world.isClient) com.gojolimitless.util.TechniqueTicker.register(this);
    }

    /** Tap: a singularity placed in the world. */
    public static BlueOrbEntity tap(ServerWorld world, PlayerEntity owner, Vec3d pos) {
        LimitlessConfig.Blue c = ConfigManager.get().blue;
        BlueOrbEntity e = new BlueOrbEntity(ModEntities.BLUE_ORB, world);
        e.setPosition(pos);
        e.setOwner(owner);
        e.dataTracker.set(MODE, MODE_TAP);
        e.dataTracker.set(RADIUS, (float) c.tapCoreRadius);
        e.dataTracker.set(PULL, (float) c.tapPullRadius);
        e.lifetime = (int) Math.round(c.tapDuration * 20);
        world.spawnEntity(e);
        return e;
    }

    /** Maximum Output: forms at the caster's hand and starts orbiting while the key is held. */
    public static BlueOrbEntity maxOutput(ServerWorld world, PlayerEntity owner) {
        LimitlessConfig.Blue c = ConfigManager.get().blue;
        BlueOrbEntity e = new BlueOrbEntity(ModEntities.BLUE_ORB, world);
        Vec3d look = owner.getRotationVec(1f);
        e.orbitAngle = Math.atan2(look.z, look.x);
        e.setPosition(owner.getEyePos().add(look.multiply(3.0)));
        e.setOwner(owner);
        e.dataTracker.set(MODE, MODE_ORBIT);
        e.dataTracker.set(RADIUS, (float) c.maxStartRadius);
        e.dataTracker.set(PULL, (float) (c.maxStartRadius * c.maxPullRadiusMul));
        world.spawnEntity(e);
        return e;
    }

    private Vec3d getVelocityForTickets() {
        return getMode() == MODE_THROWN ? throwDir.multiply(ConfigManager.get().blue.maxThrowSpeed / 20.0) : Vec3d.ZERO;
    }

    public void setOwner(Entity owner) {
        this.ownerUuid = owner.getUuid();
        this.dataTracker.set(OWNER, owner.getId());
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(MODE, MODE_TAP);
        b.add(OWNER, -1);
        b.add(RADIUS, 1.5f);
        b.add(PULL, 12f);
        b.add(COLLAPSE_START, -1);
        b.add(CHARGE, 0f);
    }

    public int getMode() { return dataTracker.get(MODE); }
    public float getRadius() { return dataTracker.get(RADIUS); }
    public float getPullRadius() { return dataTracker.get(PULL); }
    public int getOwnerId() { return dataTracker.get(OWNER); }
    public int getCollapseStart() { return dataTracker.get(COLLAPSE_START); }
    public float getCharge() { return dataTracker.get(CHARGE); }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            float target = getRadius();
            if (renderRadius < 0) renderRadius = prevRenderRadius = target;
            prevRenderRadius = renderRadius;
            renderRadius += (target - renderRadius) * 0.3f;
            trailHead = (trailHead + 1) % trail.length;
            trail[trailHead] = getPos();
            return;
        }
        ServerWorld world = (ServerWorld) getWorld();
        LimitlessConfig.Blue c = ConfigManager.get().blue;
        lastServerTick = world.getTime();
        com.gojolimitless.util.ChunkKeeper.keepAhead(world, getPos(), getVelocityForTickets(), 3);
        Entity owner = ownerUuid != null ? world.getEntity(ownerUuid) : null;
        int mode = getMode();
        if (mode != prevMode) { prevMode = mode; onModeEntered(world, mode); }

        switch (mode) {
            case MODE_TAP -> {
                if (age >= lifetime) startCollapse();
                double grow = MathHelper.clamp(age / 22.0, 0, 1);
                affect(world, owner, getRadius(), getPullRadius() * (0.35 + 0.65 * grow), c.tapPullStrength, c.tapDamagePerSecond);
                if (c.tapDestroy) DestructionEngine.carveSphere(world, getPos(), c.tapCarveRadius * easeOut(grow), DestructionEngine.Mode.PULL, getId());
            }
            case MODE_ORBIT -> {
                if (!(owner instanceof PlayerEntity p) || !owner.isAlive()) { startCollapse(); break; }
                // radius and orbit grow while held (charge is written by the ability each tick)
                float charge = getCharge();
                double r = MathHelper.lerp(easeOut(charge), c.maxStartRadius, c.maxFullRadius);
                dataTracker.set(RADIUS, (float) r);
                dataTracker.set(PULL, (float) (r * c.maxPullRadiusMul));
                double orbitR = Math.max(MathHelper.lerp(charge, 4.0, c.maxOrbitRadius), r + 3.5);
                orbitAngle += 2 * Math.PI * c.maxOrbitSpeed / 20.0 * (1.0 - 0.35 * charge);
                Vec3d center = p.getPos().add(0, 1.1, 0);
                double bob = Math.sin(age * 0.11) * 0.35 * r;
                Vec3d target = center.add(Math.cos(orbitAngle) * orbitR, bob - 0.15 * r, Math.sin(orbitAngle) * orbitR);
                // ease toward the orbit point so the formation looks like it swings out from the hand
                Vec3d pos = getPos().lerp(target, age < 20 ? 0.18 : 0.55);
                setPosition(pos);
                affect(world, p, r, r * c.maxPullRadiusMul, c.maxPullStrength, c.maxDamagePerSecond);
                if (c.maxDestroy) DestructionEngine.carveSphere(world, pos, r * c.maxCarveMul, DestructionEngine.Mode.PULL, getId());
                if (charge > 0.2f) tearSound(world);
            }
            case MODE_THROWN -> {
                if (swingTicks > 0 && owner instanceof PlayerEntity p) {
                    // keep circling, but race around to the side the caster is facing, then launch
                    swingTicks--;
                    double diff = MathHelper.wrapDegrees(Math.toDegrees(swingTarget - orbitAngle));
                    orbitAngle += Math.toRadians(diff) * 0.45;
                    double r = getRadius();
                    double orbitR = Math.max(c.maxOrbitRadius * 0.8, r + 3.5);
                    Vec3d center = p.getPos().add(0, 1.1, 0);
                    Vec3d target = center.add(Math.cos(orbitAngle) * orbitR, -0.15 * r, Math.sin(orbitAngle) * orbitR);
                    setPosition(getPos().lerp(target, 0.6));
                    affect(world, p, r, r * c.maxPullRadiusMul, c.maxPullStrength, c.maxDamagePerSecond);
                    if (c.maxDestroy) DestructionEngine.carveSphere(world, getPos(), r * c.maxCarveMul, DestructionEngine.Mode.PULL, getId());
                    if (swingTicks == 0) {
                        Vec3d aim = p.getEyePos().add(p.getRotationVec(1f).multiply(c.maxThrowDistance));
                        throwDir = aim.subtract(getPos()).normalize();
                        world.playSound(null, getX(), getY(), getZ(), ModSounds.BLUE_THROW, SoundCategory.PLAYERS, 5.0f, 0.9f);
                        fx(world, FxType.BLUE_THROW, (float) r, 0);
                    }
                    break;
                }
                double speed = c.maxThrowSpeed / 20.0;
                Vec3d pos = getPos().add(throwDir.multiply(speed));
                setPosition(pos);
                traveled += speed;
                tearSound(world);
                double r = getRadius();
                affect(world, owner, r, r * c.maxPullRadiusMul, c.maxPullStrength, c.maxDamagePerSecond);
                if (c.maxDestroy) DestructionEngine.carveSphere(world, pos, r * c.maxCarveMul, DestructionEngine.Mode.PULL, getId());
                if (traveled >= c.maxThrowDistance) startCollapse();
            }
            case MODE_RAISING -> {
                if (!(owner instanceof PlayerEntity p) || !owner.isAlive()) { startCollapse(); break; }
                int t = age - raiseStart;
                double r = getRadius();
                Vec3d top = p.getPos().add(0, 3.4 + r * 1.15, 0);
                double k = MathHelper.clamp(t / (double) RAISE_TICKS, 0, 1);
                k = k * k * (3 - 2 * k);
                // up over the head in an arc (never through the caster), still pulling what's near
                Vec3d from = raiseFrom != null ? raiseFrom : getPos();
                setPosition(from.lerp(top, k).add(0, Math.sin(Math.PI * k) * r * 0.4, 0));
                affect(world, p, r, r * c.maxPullRadiusMul * 0.8, c.maxPullStrength * 0.7, c.maxDamagePerSecond);
                if (t >= RAISE_TICKS + HOLD_TICKS) startCollapse();
            }
            case MODE_COLLAPSING -> {
                int t = age - getCollapseStart();
                double r = getRadius();
                boolean fromMax = r > c.tapCoreRadius * 1.5;
                double pull = getPullRadius() * (1.0 + t / (double) COLLAPSE_TICKS);
                affect(world, owner, r, pull, (fromMax ? c.maxPullStrength : c.tapPullStrength) * 1.8, 0);
                if (t >= COLLAPSE_TICKS) {
                    finalCrush(world, owner, r, fromMax ? c.maxCollapseDamage : c.tapCollapseDamage);
                    discard();
                }
            }
            default -> discard();
        }
    }

    private void tearSound(ServerWorld world) {
        if (age % 14 == 0 && ConfigManager.get().blue.maxDestroy)
            world.playSound(null, getX(), getY(), getZ(), ModSounds.BLUE_TEAR, SoundCategory.PLAYERS, 1.5f + getRadius() * 0.25f, 0.8f + world.random.nextFloat() * 0.3f);
    }

    private void onModeEntered(ServerWorld world, int mode) {
        float r = getRadius();
        switch (mode) {
            case MODE_TAP, MODE_ORBIT -> {
                world.playSound(null, getX(), getY(), getZ(), ModSounds.BLUE_FORM, SoundCategory.PLAYERS, 3.0f, 1.0f);
                fx(world, FxType.BLUE_FORM, r, 0);
            }
            case MODE_THROWN -> {
                if (swingTicks == 0) {
                    world.playSound(null, getX(), getY(), getZ(), ModSounds.BLUE_THROW, SoundCategory.PLAYERS, 5.0f, 0.9f);
                    fx(world, FxType.BLUE_THROW, r, 0);
                }
            }
            case MODE_RAISING -> world.playSound(null, getX(), getY(), getZ(), ModSounds.BLUE_THROW, SoundCategory.PLAYERS, 3.0f, 1.25f);
            case MODE_COLLAPSING ->
                world.playSound(null, getX(), getY(), getZ(), ModSounds.BLUE_COLLAPSE, SoundCategory.PLAYERS, 4.0f + r, 1.0f);
            default -> {}
        }
    }

    /** Pull everything in range toward the singularity; crush what reaches the core. */
    private void affect(ServerWorld world, Entity owner, double coreR, double pullR, double strength, double dps) {
        Vec3d c = getPos();
        Box box = new Box(c, c).expand(pullR);
        Entity caster = owner != null ? owner : this;
        for (Entity e : world.getOtherEntities(this, box, e -> Targets.affectable(e, caster))) {
            Vec3d to = c.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d > pullR || d < 1e-3) continue;
            double t = 1.0 - d / pullR;
            double accel = strength * (0.3 + 1.7 * t * t);
            Vec3d dir = to.multiply(1.0 / d);
            Vec3d swirl = dir.crossProduct(new Vec3d(0, 1, 0));
            if (swirl.lengthSquared() > 1e-6) swirl = swirl.normalize().multiply(accel * 0.45);
            Vec3d v = e.getVelocity().multiply(0.82).add(dir.multiply(accel)).add(swirl);
            if (d < coreR * 0.9) v = to.multiply(0.45);   // held in the core
            e.setVelocity(v);
            e.velocityModified = true;
            e.fallDistance = 0;
            if (e instanceof ItemEntity && d < coreR) { e.discard(); continue; }
            if (dps > 0 && d < coreR * 1.15 && age % 10 == 0 && e instanceof LivingEntity) {
                e.damage(ModDamage.source(world, ModDamage.LAPSE_BLUE, this, owner), (float) (dps * 0.5));
            }
        }
    }

    private void finalCrush(ServerWorld world, Entity owner, double r, double damage) {
        Vec3d c = getPos();
        Entity caster = owner != null ? owner : this;
        for (Entity e : world.getOtherEntities(this, new Box(c, c).expand(r * 1.6), e -> Targets.affectable(e, caster))) {
            if (e instanceof LivingEntity && damage > 0) {
                e.timeUntilRegen = 0;
                e.damage(ModDamage.source(world, ModDamage.LAPSE_BLUE, this, owner), (float) damage);
            }
        }
        fx(world, FxType.BLUE_COLLAPSE, (float) r, dispersing ? 1 : 0);
        LimitlessConfig.Blue cfg = ConfigManager.get().blue;
        if ((r > cfg.tapCoreRadius * 1.5 ? cfg.maxDestroy : cfg.tapDestroy))
            DestructionEngine.carveSphere(world, c, r * 1.1, DestructionEngine.Mode.PULL, -1);
    }

    private int raiseStart;
    private Vec3d raiseFrom;
    private boolean dispersing;

    public void startCollapse() {
        if (getMode() == MODE_COLLAPSING) return;
        dataTracker.set(COLLAPSE_START, age);
        dataTracker.set(MODE, MODE_COLLAPSING);
    }

    /** Called by the ability every tick while the key is held (0..1). */
    public void setCharge(float charge) { dataTracker.set(CHARGE, MathHelper.clamp(charge, 0f, 1f)); }

    /** Maximum Output released. */
    public void release(PlayerEntity owner, LimitlessConfig.ReleaseMode mode) {
        if (getMode() != MODE_ORBIT) return;
        if (mode == LimitlessConfig.ReleaseMode.RAISE_AND_DISPERSE) {
            raiseFrom = getPos();
            raiseStart = age;
            dispersing = true;
            dataTracker.set(MODE, MODE_RAISING);
            return;
        }
        if (mode == LimitlessConfig.ReleaseMode.COLLAPSE_IN_PLACE) {
            startCollapse();
            return;
        }
        Vec3d look = owner.getRotationVec(1f);
        // swing round to the front first so it never flies back through the caster, then launch at the crosshair
        swingTarget = Math.atan2(look.z, look.x);
        double diff = Math.abs(MathHelper.wrapDegrees(Math.toDegrees(swingTarget - orbitAngle)));
        swingTicks = diff > 30 ? 6 : 0;
        if (swingTicks == 0) {
            Vec3d aim = owner.getEyePos().add(look.multiply(ConfigManager.get().blue.maxThrowDistance));
            throwDir = aim.subtract(getPos()).normalize();
        }
        dataTracker.set(MODE, MODE_THROWN);
    }

    private void fx(ServerWorld world, int type, float a, float b) {
        Payloads.Fx pkt = new Payloads.Fx(type, getX(), getY(), getZ(), a, b, getId());
        for (ServerPlayerEntity p : PlayerLookup.around(world, getPos(), 320)) ServerPlayNetworking.send(p, pkt);
    }

    private static double easeOut(double t) { t = MathHelper.clamp(t, 0, 1); return 1 - Math.pow(1 - t, 3); }

    @Override public boolean shouldRender(double distance) { return distance < 512 * 512; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(getPullRadius() + 4); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) { return true; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
