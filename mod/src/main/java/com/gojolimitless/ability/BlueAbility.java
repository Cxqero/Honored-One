package com.gojolimitless.ability;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import com.gojolimitless.entity.BlueOrbEntity;
import com.gojolimitless.registry.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** 術式順転「蒼」 Cursed Technique Lapse: Blue / 出力最大「蒼」 Maximum Output: Blue. */
public class BlueAbility implements Ability {
    /** Never place the singularity so close that its crater swallows the caster's footing. */
    private static double minDist() { return Math.max(7.0, ConfigManager.get().blue.tapCarveRadius + 4.0); }

    @Override
    public void tap(ServerPlayerEntity p, CasterState s) {
        LimitlessConfig.Blue c = ConfigManager.get().blue;
        Vec3d pos = aimPoint(p, c.tapRange);
        // sit on top of whatever was hit rather than half-buried in it
        Vec3d back = p.getRotationVec(1f).multiply(-c.tapCoreRadius * 0.9);
        pos = pos.add(back).add(0, c.tapCoreRadius * 0.7, 0);
        BlueOrbEntity.tap((ServerWorld) p.getWorld(), p, pos);
        AbilityManager.broadcastStage(p, MoveType.BLUE, CastStage.TAP, 14);
    }

    @Override
    public void chargeStart(ServerPlayerEntity p, CasterState s) {
        BlueOrbEntity orb = BlueOrbEntity.maxOutput((ServerWorld) p.getWorld(), p);
        s.blueOrbId = orb.getId();
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.BLUE_CHARGE, SoundCategory.PLAYERS, 2.0f, 1.0f);
        AbilityManager.broadcastStage(p, MoveType.BLUE, CastStage.CHARGING, 0);
    }

    @Override
    public void chargeTick(ServerPlayerEntity p, CasterState s, int ticks) {
        if (p.getWorld().getEntityById(s.blueOrbId) instanceof BlueOrbEntity orb) {
            double full = ConfigManager.get().blue.maxChargeSeconds * 20.0;
            orb.setCharge((float) Math.min(1.0, ticks / full));
        }
    }

    @Override
    public void chargeRelease(ServerPlayerEntity p, CasterState s, int ticks) {
        if (p.getWorld().getEntityById(s.blueOrbId) instanceof BlueOrbEntity orb) {
            orb.release(p, ConfigManager.get().blue.maxReleaseMode);
        }
        s.blueOrbId = -1;
        AbilityManager.broadcastStage(p, MoveType.BLUE, CastStage.RELEASE,
                ConfigManager.get().blue.maxReleaseMode == com.gojolimitless.config.LimitlessConfig.ReleaseMode.RAISE_AND_DISPERSE
                        ? BlueOrbEntity.RAISE_TICKS + BlueOrbEntity.HOLD_TICKS + BlueOrbEntity.COLLAPSE_TICKS + 20 : 12);
    }

    @Override
    public void cancel(ServerPlayerEntity p, CasterState s) {
        if (p.getWorld().getEntityById(s.blueOrbId) instanceof BlueOrbEntity orb) orb.startCollapse();
        s.blueOrbId = -1;
    }

    /** Where the crosshair points (entity or block), at least a safe distance away from the caster. */
    static Vec3d aimPoint(ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(range));
        HitResult block = p.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double dist = block.getType() == HitResult.Type.MISS ? range : block.getPos().distanceTo(eye);
        Box box = p.getBoundingBox().stretch(look.multiply(dist)).expand(1.0);
        EntityHitResult ent = ProjectileUtil.raycast(p, eye, eye.add(look.multiply(dist)), box,
                e -> !e.isSpectator() && e.canHit() && !(e instanceof BlueOrbEntity), dist * dist);
        Vec3d hit;
        if (ent != null) {
            Entity e = ent.getEntity();
            hit = e.getBoundingBox().getCenter();
        } else {
            hit = eye.add(look.multiply(dist));
        }
        double d = hit.distanceTo(eye);
        if (d < minDist()) hit = eye.add(look.multiply(minDist()));
        return hit;
    }
}
