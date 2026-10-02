package com.gojolimitless.effect;

import com.gojolimitless.GojoLimitless;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Unlimited Void's sure-hit: the victim's mind is flooded with infinite information, so it can perceive everything and
 * do nothing — no walking, jumping, attacking or using items (players), no goals or targets (mobs). Health is untouched.
 */
public class VoidParalysisEffect extends StatusEffect {
    public VoidParalysisEffect() {
        super(StatusEffectCategory.HARMFUL, 0x3A1466);
        addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, id("void_speed"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_FLYING_SPEED, id("void_flying"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_JUMP_STRENGTH, id("void_jump"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, id("void_attack"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, id("void_knockback"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static Identifier id(String path) { return Identifier.of(GojoLimitless.MOD_ID, path); }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) { return true; }

    @Override
    public boolean applyUpdateEffect(LivingEntity e, int amplifier) {
        if (e instanceof MobEntity m) {
            m.getNavigation().stop();
            m.setTarget(null);
            m.setAttacking(false);
            m.setJumping(false);
            m.forwardSpeed = 0; m.sidewaysSpeed = 0; m.upwardSpeed = 0;
        }
        Vec3d v = e.getVelocity();
        // frozen where they stand: keep only a fall (flying things hang in the air)
        e.setVelocity(0, e.hasNoGravity() ? 0 : Math.min(0, v.y), 0);
        return true;
    }
}
