package com.gojolimitless.net;

/** One-shot effect ids carried by {@link Payloads.Fx}. */
public final class FxType {
    private FxType() {}
    /** a = radius. Blue singularity collapses into itself. */
    public static final int BLUE_COLLAPSE = 1;
    /** a = radius. Blue forms (burst of inward streaks). */
    public static final int BLUE_FORM = 2;
    /** Infinity stopped something here. a = size. */
    public static final int INFINITY_RIPPLE = 3;
    /** Blue thrown: a = radius. */
    public static final int BLUE_THROW = 4;
    /** a = radius, b = power (0..1). */
    public static final int RED_DETONATE = 5;
    /** entityId = orb, a = stage (1..3). */
    public static final int RED_STAGE = 6;
    /** a = power; muzzle flash when Red is fired. */
    public static final int RED_FIRE = 7;
    /** Blue and Red collide into Purple. a = radius, b = power. */
    public static final int PURPLE_COLLIDE = 8;
    /** Purple launched. a = radius, b = power. */
    public static final int PURPLE_LAUNCH = 9;
    /** entityId = purple, a = stage (1..4). */
    public static final int PURPLE_STAGE = 10;
    /** Remote Purple: Blue and Red meet at the target (flash, then silence). */
    public static final int NUKE_COLLIDE = 11;
    /** Remote Purple: the imaginary mass blooms. a = full radius. */
    public static final int NUKE_BLOOM = 12;
    /** The nuke detonates: a = blast radius. */
    public static final int NUKE_EXPLODE = 13;
    /** Hollow Purple strikes something (and flies on): a = radius, b = power. */
    public static final int PURPLE_IMPACT = 14;
}
