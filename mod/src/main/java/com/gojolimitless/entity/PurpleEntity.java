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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 虚式「茈」 Hollow Technique: Purple. The two infinities — convergence (Blue) and divergence (Red) — collide into an
 * imaginary mass that rushes forth and erases whatever it touches. No explosion, no rubble: things simply stop existing.
 * CHARGING (200% only, while the incantation is chanted) → FORMING (Blue and Red meet) → FLYING → FADING.
 */
public class PurpleEntity extends Entity implements com.gojolimitless.util.TechniqueTicker.Tracked {
    private long lastServerTick = -1;

    @Override public long lastServerTick() { return lastServerTick; }
    public static final int PHASE_CHARGING = 0, PHASE_FORMING = 1, PHASE_FLYING = 2, PHASE_FADING = 3;
    public static final int TAP_FORM_TICKS = 34, TAP_COLLIDE = 20;
    public static final int FULL_FORM_TICKS = 44, FULL_COLLIDE = 16;
    public static final int FADE_TICKS = 14;

    private static final TrackedData<Integer> PHASE = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> PHASE_START = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> FULL = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> POWER = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(PurpleEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private UUID ownerUuid;
    private Vec3d dir = Vec3d.ZERO;
    private double speed, range, traveled;

    public final Vec3d[] trail = new Vec3d[20];
    public int trailHead = -1;

    public PurpleEntity(EntityType<? extends PurpleEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
        if (!world.isClient) com.gojolimitless.util.TechniqueTicker.register(this);
    }

    /** Tap: Blue and Red are brought together immediately. */
    public static PurpleEntity tap(ServerWorld world, PlayerEntity owner) {
        PurpleEntity e = spawn(world, owner, false);
        e.enterForming(world, owner);
        return e;
    }

    /** Hold: the incantation begins; Blue and Red circle the caster until release. */
    public static PurpleEntity charge(ServerWorld world, PlayerEntity owner) {
        PurpleEntity e = spawn(world, owner, true);
        // the gathering pressure lays the grass flat around him (keeps the release's shots clear)
        if (ConfigManager.get().general.destruction && com.gojolimitless.destruction.DestructionEngine.allowed(world))
            NukeEntity.flattenPlants(world, owner.getBlockPos(), 4);
        e.setPhase(PHASE_CHARGING);
        return e;
    }

    private static PurpleEntity spawn(ServerWorld world, PlayerEntity owner, boolean full) {
        PurpleEntity e = new PurpleEntity(ModEntities.PURPLE, world);
        e.ownerUuid = owner.getUuid();
        e.dataTracker.set(OWNER, owner.getId());
        e.dataTracker.set(FULL, full);
        LimitlessConfig.Purple c = ConfigManager.get().purple;
        e.dataTracker.set(RADIUS, (float) (full ? c.fullRadius : c.tapRadius));
        e.setPosition(formPos(owner, full, (float) (full ? c.fullRadius : c.tapRadius)));
        world.spawnEntity(e);
        return e;
    }

    /** Where the imaginary mass forms: level with the eyes, straight ahead, clear of the caster. */
    public static Vec3d formPos(Entity owner, boolean full, float radius) {
        float yaw = owner.getYaw();
        Vec3d fwd = new Vec3d(-MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE), 0, MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE));
        double dist = full ? radius + 3.5 : radius + 2.0;
        return owner.getEyePos().add(fwd.multiply(dist)).add(0, full ? radius * 0.35 : radius * 0.1, 0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(PHASE, PHASE_FORMING);
        b.add(PHASE_START, 0);
        b.add(OWNER, -1);
        b.add(FULL, false);
        b.add(POWER, 0f);
        b.add(STAGE, 0);
        b.add(RADIUS, 4f);
    }

    public int getPhase() { return dataTracker.get(PHASE); }
    public int getPhaseStart() { return dataTracker.get(PHASE_START); }
    public int getOwnerId() { return dataTracker.get(OWNER); }
    public boolean isFull() { return dataTracker.get(FULL); }
    public float getPower() { return dataTracker.get(POWER); }
    public int getStage() { return dataTracker.get(STAGE); }
    public float getRadius() { return dataTracker.get(RADIUS); }
    public int formTicks() { return isFull() ? FULL_FORM_TICKS : TAP_FORM_TICKS; }
    public int collideTick() { return isFull() ? FULL_COLLIDE : TAP_COLLIDE; }

    private Vec3d getVelocityForTickets() { return getPhase() == PHASE_FLYING ? dir.multiply(speed) : Vec3d.ZERO; }

    public void setPower(float p) { dataTracker.set(POWER, MathHelper.clamp(p, 0f, 1f)); }

    public boolean setStage(int s) {
        if (s == getStage()) return false;
        dataTracker.set(STAGE, s);
        return true;
    }

    private void setPhase(int p) {
        dataTracker.set(PHASE, p);
        dataTracker.set(PHASE_START, age);
    }

    /** Release of the 200% charge, or immediately for a tap. */
    public void enterForming(ServerWorld world, PlayerEntity owner) {
        setPhase(PHASE_FORMING);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.PURPLE_FORM, SoundCategory.PLAYERS, isFull() ? 4f : 2.5f, 1.0f);
        boolean play = ConfigManager.get().client.cutscenes && (isFull() || ConfigManager.get().client.cutsceneTapPurple);
        if (play && owner instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, new Payloads.Cutscene(isFull() ? "purple_200" : "purple_tap",
                    owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), owner.getId()));
        }
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
        int t = age - getPhaseStart();
        lastServerTick = world.getTime();
        com.gojolimitless.util.ChunkKeeper.keepAhead(world, getPos(), getVelocityForTickets(), 3);
        switch (getPhase()) {
            case PHASE_CHARGING -> {
                if (!(owner instanceof PlayerEntity p) || !p.isAlive()) { discard(); return; }
                setPosition(p.getPos().add(0, 1.2, 0));
                // the pressure of the gathering technique pushes the world back
                pressure(world, p, 3 + 5 * getPower(), 0.05 + 0.12 * getPower());
            }
            case PHASE_FORMING -> {
                if (owner == null || !owner.isAlive()) { discard(); return; }
                setPosition(formPos(owner, isFull(), getRadius()));
                if (t == collideTick()) {
                    world.playSound(null, getX(), getY(), getZ(), ModSounds.PURPLE_COLLIDE, SoundCategory.PLAYERS, isFull() ? 6f : 3.5f, isFull() ? 0.85f : 1.0f);
                    fx(world, FxType.PURPLE_COLLIDE, getRadius(), isFull() ? 1 : 0);
                }
                if (t > collideTick()) pressure(world, owner, getRadius() * 2.2, isFull() ? 0.35 : 0.15);
                if (t >= formTicks()) launch(world, owner);
            }
            case PHASE_FLYING -> flyTick(world, owner);
            case PHASE_FADING -> { if (t >= FADE_TICKS) discard(); }
            default -> discard();
        }
    }

    private void launch(ServerWorld world, Entity owner) {
        LimitlessConfig.Purple c = ConfigManager.get().purple;
        dir = owner.getRotationVec(1f).normalize();
        speed = (isFull() ? c.fullSpeed : c.tapSpeed) / 20.0;
        range = isFull() ? c.fullRange : c.tapRange;
        traveled = 0;
        setPhase(PHASE_FLYING);
        world.playSound(null, getX(), getY(), getZ(), ModSounds.PURPLE_LAUNCH, SoundCategory.PLAYERS, isFull() ? 8f : 4f, isFull() ? 0.8f : 1.0f);
        fx(world, FxType.PURPLE_LAUNCH, getRadius(), isFull() ? 1 : 0);
    }

    private void flyTick(ServerWorld world, Entity owner) {
        LimitlessConfig.Purple c = ConfigManager.get().purple;
        double r = getRadius();
        Vec3d from = getPos();
        // sub-steps so fast, huge masses leave a continuous trench
        int steps = Math.max(1, (int) Math.ceil(speed / Math.max(1.0, r * 0.6)));
        boolean destroy = isFull() ? c.fullDestroy : c.tapDestroy;
        // the first thing it strikes explodes (before this step erases it); the mass flies on
        if (!exploded && traveled >= r * 2 + 4) {
            Vec3d hit = terrainContact(world, from, from.add(dir.multiply(speed)), r);
            if (hit != null) impact(world, owner, hit, r);
        }
        for (int i = 1; i <= steps; i++) {
            Vec3d p = from.add(dir.multiply(speed * i / steps));
            if (destroy) DestructionEngine.carveSphere(world, p, r, DestructionEngine.Mode.ERASE, getId());
        }
        Vec3d to = from.add(dir.multiply(speed));
        setPosition(to);
        traveled += speed;
        // erase anything caught in the mass
        Entity caster = owner != null ? owner : this;
        double dmg = isFull() ? c.fullDamage : c.tapDamage;
        Box sweep = new Box(from, to).expand(r * 1.05);
        for (Entity e : world.getOtherEntities(this, sweep, e -> Targets.affectable(e, caster))) {
            Vec3d ce = e.getBoundingBox().getCenter();
            Vec3d ab = to.subtract(from);
            double k = MathHelper.clamp(ce.subtract(from).dotProduct(ab) / Math.max(1e-6, ab.lengthSquared()), 0, 1);
            if (from.add(ab.multiply(k)).distanceTo(ce) > r * 1.05) continue;
            if (e instanceof LivingEntity) {
                if (!exploded) impact(world, owner, ce, r);
                e.timeUntilRegen = 0;
                e.damage(ModDamage.source(world, ModDamage.HOLLOW_PURPLE, this, owner), (float) dmg);
            } else {
                e.discard();                         // items, arrows, boats… simply gone
            }
        }
        if (age % 8 == 0) world.playSound(null, getX(), getY(), getZ(), ModSounds.PURPLE_RUSH, SoundCategory.PLAYERS, isFull() ? 6f : 3f, 0.9f);
        if (traveled >= range) setPhase(PHASE_FADING);
    }

    private boolean exploded;

    /** Where the mass's leading surface first meets solid ground along this step (null: clear). */
    private Vec3d terrainContact(ServerWorld world, Vec3d from, Vec3d to, double r) {
        Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d side = dir.crossProduct(up).normalize(), lift = side.crossProduct(dir).normalize();
        Vec3d[] probes = {dir, dir.add(lift.multiply(-0.8)).normalize(), dir.add(lift.multiply(0.6)).normalize(),
                dir.add(side.multiply(0.7)).normalize(), dir.add(side.multiply(-0.7)).normalize()};
        int steps = Math.max(1, (int) Math.ceil(to.distanceTo(from) / Math.max(1.0, r * 0.5)));
        for (int s = 1; s <= steps; s++) {
            Vec3d at = from.lerp(to, s / (double) steps);
            for (Vec3d d : probes) {
                Vec3d p = at.add(d.multiply(r * 0.9));
                net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.ofFloored(p);
                if (!world.getBlockState(bp).getCollisionShape(world, bp).isEmpty()) return p;
            }
        }
        return null;
    }

    /** The explosion at the point of contact: the blast throws and hurts what's around it (the mass erases what it touches). */
    private void impact(ServerWorld world, Entity owner, Vec3d at, double r) {
        exploded = true;
        com.gojolimitless.GojoLimitless.LOG.info("[purple] impact at {} (age {}, travelled {})", at, age, String.format("%.1f", traveled));
        world.playSound(null, at.x, at.y, at.z, ModSounds.PURPLE_IMPACT, SoundCategory.PLAYERS, isFull() ? 10f : 5f, isFull() ? 0.85f : 1.0f);
        Payloads.Fx pkt = new Payloads.Fx(FxType.PURPLE_IMPACT, at.x, at.y, at.z, (float) r, isFull() ? 1 : 0, getId());
        for (ServerPlayerEntity pl : PlayerLookup.around(world, at, 512)) ServerPlayNetworking.send(pl, pkt);
        LimitlessConfig.Purple c = ConfigManager.get().purple;
        Entity caster = owner != null ? owner : this;
        double br = r * (isFull() ? 2.6 : 2.2), dmg = (isFull() ? c.fullDamage : c.tapDamage) * 0.25;
        for (Entity e : world.getOtherEntities(this, new Box(at, at).expand(br), e -> e != owner && Targets.affectable(e, caster))) {
            Vec3d away = e.getBoundingBox().getCenter().subtract(at);
            double d = away.length();
            if (d > br) continue;
            double k = 1 - d / br;
            if (e instanceof LivingEntity) e.damage(ModDamage.source(world, ModDamage.HOLLOW_PURPLE, this, owner), (float) (dmg * k));
            Vec3d push = d > 1e-3 ? away.multiply(1 / d) : new Vec3d(0, 1, 0);
            e.setVelocity(e.getVelocity().add(push.multiply(1.8 * k)).add(0, 0.5 * k, 0));
            e.velocityModified = true;
        }
    }

    private void pressure(ServerWorld world, Entity owner, double radius, double strength) {
        Entity caster = owner != null ? owner : this;
        Vec3d c = getPos();
        for (Entity e : world.getOtherEntities(this, new Box(c, c).expand(radius), e -> Targets.affectable(e, caster))) {
            Vec3d away = e.getPos().subtract(c);
            double d = away.length();
            if (d > radius || d < 1e-3) continue;
            e.setVelocity(e.getVelocity().add(away.multiply(strength * (1 - d / radius) / d)));
            e.velocityModified = true;
        }
    }

    private void fx(ServerWorld world, int type, float a, float b) {
        Payloads.Fx pkt = new Payloads.Fx(type, getX(), getY(), getZ(), a, b, getId());
        for (ServerPlayerEntity pl : PlayerLookup.around(world, getPos(), 512)) ServerPlayNetworking.send(pl, pkt);
    }

    @Override public boolean shouldRender(double distance) { return distance < 1024 * 1024; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(getRadius() * 3 + 8); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
