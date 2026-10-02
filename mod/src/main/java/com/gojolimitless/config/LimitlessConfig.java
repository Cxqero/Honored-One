package com.gojolimitless.config;

/**
 * Every tunable value in the mod. Sections become categories in the config screen;
 * fields become options (ranges from {@link Range}, tooltips from {@link Comment}).
 * Stored as config/gojolimitless.json.
 */
public class LimitlessConfig {
    public General general = new General();
    public Blue blue = new Blue();
    public Red red = new Red();
    public Purple purple = new Purple();
    public Nuke nuke = new Nuke();
    public Domain domain = new Domain();
    public Infinity infinity = new Infinity();
    public Client client = new Client();

    @Comment("Rules that apply to every technique.")
    public static class General {
        @Comment("Master switch for terrain destruction. Off = techniques never break blocks.")
        public boolean destruction = true;
        @Comment("Only destroy terrain when the mobGriefing game rule is on.")
        public boolean respectMobGriefing = false;
        @Comment("Maximum blocks removed per server tick across all techniques. Higher = faster craters, heavier ticks.")
        @Range(min = 200, max = 200000, step = 100)
        public int blocksPerTick = 6000;
        @Comment("Erase blocks that hold items (chests, barrels, furnaces...). Their contents are destroyed.")
        public boolean eraseContainers = false;
        @Comment("Maximum flying debris pieces sent to your client per tick.")
        @Range(min = 0, max = 400, step = 5)
        public int debrisPerTick = 70;
        @Comment("Hold a technique key at least this long (ms) to use its charged / held version. Shorter = tap.")
        @Range(min = 100, max = 800, step = 10)
        public int holdThresholdMs = 240;
        @Comment("Techniques never hurt tamed pets that belong to you.")
        public boolean sparePets = true;
    }

    @Comment("Cursed Technique Lapse: Blue (tap) and Maximum Output: Blue (hold).")
    public static class Blue {
        @Group("Tap: Lapse: Blue")
        @Comment("Tap: how far away Blue can be placed (blocks).")
        @Range(min = 4, max = 128, step = 1)
        public double tapRange = 48;
        @Comment("Tap: visual radius of the singularity (blocks).")
        @Range(min = 0.5, max = 8, step = 0.1)
        public double tapCoreRadius = 2.1;
        @Comment("Tap: radius of the attraction field (blocks).")
        @Range(min = 2, max = 64, step = 0.5)
        public double tapPullRadius = 16;
        @Comment("Tap: pull strength (blocks/tick² at the edge; stronger closer in).")
        @Range(min = 0, max = 2, step = 0.01)
        public double tapPullStrength = 0.30;
        @Comment("Tap: how long the singularity lasts (seconds).")
        @Range(min = 0.5, max = 15, step = 0.1)
        public double tapDuration = 3.2;
        @Comment("Tap: damage per second to anything caught in the core.")
        @Range(min = 0, max = 200, step = 0.5)
        public double tapDamagePerSecond = 24;
        @Comment("Tap: damage dealt when the singularity collapses.")
        @Range(min = 0, max = 500, step = 1)
        public double tapCollapseDamage = 30;
        @Comment("Tap: radius of terrain torn out and swallowed (blocks).")
        @Range(min = 0, max = 24, step = 0.5)
        public double tapCarveRadius = 5.5;
        @Comment("Tap: break terrain.")
        public boolean tapDestroy = true;

        @Group("Hold: Maximum Output: Blue")
        @Comment("Maximum Output: radius when it first forms (blocks).")
        @Range(min = 0.5, max = 10, step = 0.1)
        public double maxStartRadius = 1.4;
        @Comment("Maximum Output: radius at full charge (blocks).")
        @Range(min = 2, max = 40, step = 0.5)
        public double maxFullRadius = 9.0;
        @Comment("Maximum Output: seconds of holding to reach full size.")
        @Range(min = 0.5, max = 20, step = 0.1)
        public double maxChargeSeconds = 6.0;
        @Comment("Maximum Output: orbit distance from you at full size (blocks). Grows with the orb.")
        @Range(min = 3, max = 60, step = 0.5)
        public double maxOrbitRadius = 15.0;
        @Comment("Maximum Output: orbit speed (revolutions per second).")
        @Range(min = 0.05, max = 3, step = 0.05)
        public double maxOrbitSpeed = 0.55;
        @Comment("Maximum Output: attraction radius as a multiple of the orb's radius.")
        @Range(min = 1, max = 8, step = 0.1)
        public double maxPullRadiusMul = 3.2;
        @Comment("Maximum Output: pull strength.")
        @Range(min = 0, max = 3, step = 0.01)
        public double maxPullStrength = 0.55;
        @Comment("Maximum Output: damage per second inside the orb.")
        @Range(min = 0, max = 400, step = 1)
        public double maxDamagePerSecond = 60;
        @Comment("Maximum Output: terrain torn out, as a multiple of the orb's radius.")
        @Range(min = 0, max = 3, step = 0.05)
        public double maxCarveMul = 1.25;
        @Comment("Maximum Output: break terrain.")
        public boolean maxDestroy = true;
        @Comment("Maximum Output: what happens when you let go - raise it overhead and let it disperse (with its collapse), throw it, or collapse it where it is.")
        public ReleaseMode maxReleaseMode = ReleaseMode.RAISE_AND_DISPERSE;
        @Comment("Maximum Output: throw speed (blocks per second).")
        @Range(min = 2, max = 120, step = 1)
        public double maxThrowSpeed = 26;
        @Comment("Maximum Output: how far the thrown orb travels before collapsing (blocks).")
        @Range(min = 4, max = 400, step = 1)
        public double maxThrowDistance = 70;
        @Comment("Maximum Output: collapse damage.")
        @Range(min = 0, max = 1000, step = 1)
        public double maxCollapseDamage = 80;
    }

    public enum ReleaseMode { RAISE_AND_DISPERSE, THROW, COLLAPSE_IN_PLACE }

    @Comment("Cursed Technique Reversal: Red (tap) and the incantation Red (hold).")
    public static class Red {
        @Group("Tap: Reversal: Red")
        @Comment("Tap: maximum flight distance (blocks).")
        @Range(min = 8, max = 256, step = 1)
        public double tapRange = 110;
        @Comment("Tap: flight speed (blocks per second).")
        @Range(min = 10, max = 200, step = 1)
        public double tapSpeed = 75;
        @Comment("Tap: radius of the repulsion blast (blocks).")
        @Range(min = 1, max = 32, step = 0.5)
        public double tapBlastRadius = 6.5;
        @Comment("Tap: knockback strength.")
        @Range(min = 0, max = 12, step = 0.1)
        public double tapKnockback = 3.4;
        @Comment("Tap: damage at the centre of the blast.")
        @Range(min = 0, max = 500, step = 1)
        public double tapDamage = 34;
        @Comment("Tap: blast terrain.")
        public boolean tapDestroy = true;

        @Group("Hold: incantation Red")
        @Comment("Hold: seconds to chant the full incantation (Phase, Paramita, Pillars of Light).")
        @Range(min = 0.6, max = 10, step = 0.1)
        public double chargeSeconds = 3.0;
        @Comment("Hold: blast radius at full incantation (blocks).")
        @Range(min = 4, max = 64, step = 0.5)
        public double fullBlastRadius = 17;
        @Comment("Hold: radius of the tunnel it drills while flying (blocks).")
        @Range(min = 0, max = 16, step = 0.25)
        public double fullTunnelRadius = 3.5;
        @Comment("Hold: how many blocks of terrain it can drill through before detonating.")
        @Range(min = 0, max = 400, step = 1)
        public double pierceBlocks = 70;
        @Comment("Hold: flight speed at full incantation (blocks per second).")
        @Range(min = 10, max = 300, step = 1)
        public double fullSpeed = 120;
        @Comment("Hold: maximum flight distance (blocks).")
        @Range(min = 16, max = 512, step = 1)
        public double fullRange = 240;
        @Comment("Hold: knockback strength at full incantation.")
        @Range(min = 0, max = 30, step = 0.1)
        public double fullKnockback = 7.5;
        @Comment("Hold: damage at the centre of the blast at full incantation.")
        @Range(min = 0, max = 2000, step = 1)
        public double fullDamage = 130;
        @Comment("Hold: blast terrain.")
        public boolean fullDestroy = true;
    }

    @Comment("Hollow Technique: Purple (tap) and the full-incantation 200% Purple (hold).")
    public static class Purple {
        @Group("Tap: Hollow Purple")
        @Comment("Tap: radius of the imaginary mass (blocks). Everything it touches is erased.")
        @Range(min = 1, max = 24, step = 0.25)
        public double tapRadius = 4.5;
        @Comment("Tap: how far it travels (blocks).")
        @Range(min = 16, max = 600, step = 1)
        public double tapRange = 140;
        @Comment("Tap: travel speed (blocks per second).")
        @Range(min = 10, max = 300, step = 1)
        public double tapSpeed = 70;
        @Comment("Tap: damage to anything it passes through.")
        @Range(min = 0, max = 5000, step = 1)
        public double tapDamage = 120;
        @Comment("Tap: erase terrain.")
        public boolean tapDestroy = true;

        @Group("Hold: 200% Hollow Purple")
        @Comment("Hold: seconds to chant the full incantation (Nine Ropes, Polarized Light, Crow and Declaration, Between Front and Back).")
        @Range(min = 1, max = 12, step = 0.1)
        public double chargeSeconds = 4.0;
        @Comment("Hold: radius of the imaginary mass at 200% (blocks).")
        @Range(min = 3, max = 64, step = 0.5)
        public double fullRadius = 11;
        @Comment("Hold: how far the 200% Purple travels (blocks).")
        @Range(min = 32, max = 2000, step = 1)
        public double fullRange = 420;
        @Comment("Hold: travel speed (blocks per second).")
        @Range(min = 10, max = 400, step = 1)
        public double fullSpeed = 110;
        @Comment("Hold: damage to anything it passes through.")
        @Range(min = 0, max = 100000, step = 10)
        public double fullDamage = 1000;
        @Comment("Hold: erase terrain.")
        public boolean fullDestroy = true;
    }

    @Comment("Remote Hollow Purple (ch. 234–235): Red is sent into the sky, Blue is boosted by its incantation, you and your target are lifted, and the two converge on the target — the imaginary mass erases everything around it.")
    public static class Nuke {
        @Group("Target")
        @Comment("Furthest the technique can be sent (blocks). Aim at a creature to target it, or at the ground.")
        @Range(min = 24, max = 256, step = 1)
        public double maxDistance = 96;
        @Comment("Closest the convergence point can be to you (blocks).")
        @Range(min = 12, max = 64, step = 1)
        public double minDistance = 24;
        @Comment("How high above the ground the target is held when Blue and Red converge on it (blocks).")
        @Range(min = 4, max = 64, step = 1)
        public double liftHeight = 18;
        @Comment("Creatures within this distance of the target are caught and lifted with it (blocks).")
        @Range(min = 0, max = 32, step = 0.5)
        public double liftRadius = 9;
        @Comment("Maximum creatures lifted.")
        @Range(min = 0, max = 64, step = 1)
        public int maxLifted = 12;

        @Group("Erasure")
        @Comment("Radius of the erasure (blocks). Everything inside simply stops existing.")
        @Range(min = 8, max = 160, step = 1)
        public double radius = 64;
        @Comment("Seconds for the erasure to reach full size.")
        @Range(min = 0.5, max = 10, step = 0.1)
        public double expandSeconds = 3.0;
        @Comment("Damage to everything inside.")
        @Range(min = 0, max = 100000, step = 10)
        public double damage = 5000;
        @Comment("Erase terrain.")
        public boolean destroy = true;
        @Comment("Blocks the erasure may remove per tick (its own budget). Higher = faster crater, heavier ticks.")
        @Range(min = 1000, max = 200000, step = 500)
        public int blocksPerTick = 24000;

        @Group("Caster")
        @Comment("Damage you take from your own technique. Canon: Gojo was caught in the blast without Infinity and took only minimal damage. Never lethal.")
        @Range(min = 0, max = 19, step = 0.5)
        public double selfDamage = 4;
        @Comment("Seconds of slow falling afterwards, so you drift down into the crater.")
        @Range(min = 0, max = 30, step = 0.5)
        public double slowFallSeconds = 14;
    }

    @Comment("Domain Expansion: Unlimited Void (無量空処). Everyone inside the barrier is flooded with infinite information and can't act. Anything touching you when it expands is left out. Hold for the full domain, tap for the 0.2-second domain (Shibuya). Press again while it is up to collapse it.")
    public static class Domain {
        @Group("Hold: Unlimited Void")
        @Comment("Radius of the barrier (blocks).")
        @Range(min = 8, max = 96, step = 1)
        public double radius = 32;
        @Comment("How long the domain stays up if you don't collapse it yourself (seconds).")
        @Range(min = 3, max = 600, step = 1)
        public double seconds = 30;
        @Comment("Press the domain key again while it is up to collapse it (smoothly, with the ink wipe).")
        public boolean collapseOnRecast = true;
        @Comment("Victims stay paralysed this long after the domain collapses (seconds).")
        @Range(min = 0, max = 60, step = 0.5)
        public double afterSeconds = 3;

        @Group("Tap: 0.2-second Unlimited Void")
        @Comment("Radius of the 0.2-second domain (blocks).")
        @Range(min = 8, max = 96, step = 1)
        public double instantRadius = 24;
        @Comment("How long its victims are paralysed (seconds). Canon: long enough to finish every curse in Shibuya — about five minutes.")
        @Range(min = 1, max = 300, step = 1)
        public double instantParalysisSeconds = 10;

        @Group("Sure-hit")
        @Comment("Anything touching you when the domain expands is left out (canon: Gojo held Itadori).")
        public boolean excludeTouching = true;
        @Comment("Other players are caught.")
        public boolean affectPlayers = true;
        @Comment("Animals and other passive creatures are caught.")
        public boolean affectPassive = true;
        @Comment("Hostile creatures are caught.")
        public boolean affectHostile = true;
        @Comment("Bosses (the Wither, the Ender Dragon, Wardens) recover this much faster (1 = same as everyone, 4 = a quarter of the time).")
        @Range(min = 1, max = 10, step = 0.5)
        public double bossRecovery = 3;

        @Group("Caster")
        @Comment("Strength level while your domain is up (0 = none).")
        @Range(min = 0, max = 5, step = 1)
        public int strength = 2;
        @Comment("Speed level while your domain is up (0 = none).")
        @Range(min = 0, max = 5, step = 1)
        public int speed = 1;
        @Comment("Resistance level while your domain is up (0 = none).")
        @Range(min = 0, max = 4, step = 1)
        public int resistance = 1;
        @Comment("Regeneration level while your domain is up (0 = none).")
        @Range(min = 0, max = 4, step = 1)
        public int regeneration = 0;
        @Comment("How long the 0.2-second domain's buffs last on you (seconds).")
        @Range(min = 0, max = 120, step = 1)
        public double instantBuffSeconds = 15;
        @Comment("You can't be moved while the domain expands (the seal is held).")
        public boolean rootWhileExpanding = true;
        @Comment("Seconds before you can expand a domain again (canon: none — Gojo repairs his technique with reverse cursed technique).")
        @Range(min = 0, max = 600, step = 1)
        public double cooldownSeconds = 0;
    }

    @Comment("Infinity: the neutral Limitless. Anything approaching you slows endlessly and never arrives.")
    public static class Infinity {
        @Comment("Infinity is on when you join a world (toggle in-game with its key).")
        public boolean enabledByDefault = true;
        @Comment("Distance at which projectiles start slowing down (blocks).")
        @Range(min = 1, max = 16, step = 0.1)
        public double slowRadius = 4.5;
        @Comment("Distance at which projectiles stop completely (blocks).")
        @Range(min = 0.3, max = 6, step = 0.05)
        public double stopRadius = 1.2;
        @Comment("Block melee hits and mob contact.")
        public boolean blockMelee = true;
        @Comment("Block projectile damage.")
        public boolean blockProjectiles = true;
        @Comment("Block explosion damage.")
        public boolean blockExplosions = true;
        @Comment("Block fall damage (off by default: canon Infinity doesn't stop the ground).")
        public boolean blockFall = false;
        @Comment("Show a faint ripple where things are stopped.")
        public boolean ripples = true;
    }

    @Comment("Visual and audio settings (your client only).")
    public static class Client {
        @Comment("Overall VFX detail.")
        public Quality quality = Quality.ULTRA;
        @Comment("Rendering path. AUTO switches to the shaderpack-safe path when an Iris shaderpack is active.")
        public RenderPathMode renderPath = RenderPathMode.AUTO;
        @Comment("With an Iris shaderpack: draw the energy effects after the pack has finished the frame, so every pack shows them as intended (off = let the pack shade them).")
        public boolean effectsAfterShaderpack = true;
        @Comment("Maximum debris pieces alive at once.")
        @Range(min = 0, max = 6000, step = 50)
        public int maxDebris = 2500;
        @Comment("Screen flash strength (lower this if flashes bother you).")
        @Range(min = 0, max = 1, step = 0.05)
        public double flashIntensity = 0.85;
        @Comment("Camera shake strength.")
        @Range(min = 0, max = 2, step = 0.05)
        public double cameraShake = 1.0;
        @Comment("Show technique titles and incantation subtitles.")
        public boolean subtitles = true;
        @Comment("Cinematic cutscenes for the big techniques.")
        public boolean cutscenes = true;
        @Comment("Also play the short cutscene for the tapped Hollow Purple (off = only the charged / big techniques).")
        public boolean cutsceneTapPurple = true;
        @Comment("Casting poses on your player model.")
        public boolean poses = true;
        @Comment("In first person, show your arms doing the hand signs while casting.")
        public boolean firstPersonArms = true;
        @Comment("Body physics on the casting animations: arms and torso follow through, overshoot a little and settle, and big casts kick the body back. 0 = off (raw keyframes), 1 = default, 2 = loose.")
        @Range(min = 0, max = 2, step = 0.1)
        public double animationPhysics = 1.0;
        @Comment("Anime impact frames: a few inverted black/white frames on the biggest hits (strobes; scaled by the flash strength, off at 0).")
        public boolean impactFrames = true;
        @Comment("Anime speed lines at the edge of the screen during the fastest moments (the nuke's leap and rush, Purple's launch).")
        public boolean speedLines = true;
        @Comment("Inside Unlimited Void the world is replaced by the void (terrain hidden, everyone floats in space).")
        public boolean domainHideTerrain = true;
        @Comment("Show the giant black hole inside Unlimited Void.")
        public boolean domainBlackHole = true;
        @Comment("Smoke wisps drifting around you inside Unlimited Void.")
        public boolean domainWisps = true;
        @Comment("Brightness of the void inside the domain.")
        @Range(min = 0.3, max = 1.6, step = 0.05)
        public double domainBrightness = 1.0;
        @Comment("The expansion (white, ink, the tunnel of information) for anyone caught inside, not just the caster.")
        public boolean domainExpansionForVictims = true;
        @Comment("When you are caught in a domain: the flood of information across your screen.")
        public boolean domainVictimOverlay = true;
        @Comment("Show the barrier (a black dome) when you are outside someone's domain.")
        public boolean domainBarrier = true;
        @Comment("Glow intensity multiplier for energy effects.")
        @Range(min = 0.2, max = 2, step = 0.05)
        public double glow = 1.0;
    }

    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    public enum RenderPathMode { AUTO, SHADERPACK_SAFE, ENHANCED }
}
