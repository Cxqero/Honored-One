package com.gojolimitless.ability;

/** Stages broadcast to clients for poses / HUD. */
public final class CastStage {
    private CastStage() {}
    public static final int NONE = 0;
    public static final int TAP = 1;       // quick cast gesture
    public static final int CHARGING = 2;  // key held, technique building
    public static final int RELEASE = 3;   // charged version released
    public static final int END = 4;       // back to idle
}
