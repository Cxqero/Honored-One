package com.gojolimitless.ability;

/** Per-player server-side casting state. */
public class CasterState {
    public MoveType held;          // key currently held (null = none)
    public int heldTicks;
    public boolean charging;       // passed the hold threshold
    public int autoReleaseAt = -1; // debug/autotest: release after this many held ticks
    public int blueOrbId = -1;     // active Maximum Output: Blue
    public int redOrbId = -1;      // Red being charged
    public int purpleId = -1;      // Purple being charged (200%)
    public int nukeId = -1;        // remote Hollow Purple in progress (locks out other techniques)
    public int domainId = -1;      // Unlimited Void currently expanded
    public long domainReadyAt;     // world time the next domain may be expanded (cooldown)
    public boolean infinity = true;
}
