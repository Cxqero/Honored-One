package com.gojolimitless.client.domain;

import com.gojolimitless.client.cutscene.CutsceneDirector;
import com.gojolimitless.client.cutscene.InsertPlayer;
import com.gojolimitless.client.hud.HudOverlay;
import com.gojolimitless.client.render.LoopTextures;
import com.gojolimitless.client.sound.TrackedLoopSound;
import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.DomainEntity;
import com.gojolimitless.registry.ModEffects;
import com.gojolimitless.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static com.gojolimitless.entity.DomainEntity.*;

/**
 * Client half of Unlimited Void: which phase the camera is in (the white, the ink, the tunnel, the void), whether
 * the world is hidden, the flash and the ink wipe, the caster's pose and Six Eyes, the ambience of the void, and
 * the flood of information across a victim's screen.
 */
public final class DomainClient {
    private DomainClient() {}

    private static final List<DomainEntity> ACTIVE = new ArrayList<>();
    private static final java.util.Map<Integer, Integer> CUED = new java.util.HashMap<>();
    private static final java.util.Set<Integer> LOOPING = new java.util.HashSet<>();
    private static final java.util.Set<Integer> WIPED = new java.util.HashSet<>();

    public enum Phase { NONE, SEAL, WHITE, INK, TUNNEL, VOID }

    // ------------------------------------------------------------------ discovery
    public static void tick(MinecraftClient mc) {
        ACTIVE.clear();
        if (mc.world == null || mc.player == null) { CUED.clear(); LOOPING.clear(); return; }
        for (Entity e : mc.world.getEntities()) if (e instanceof DomainEntity d && !d.isRemoved()) ACTIVE.add(d);
        CUED.keySet().removeIf(id -> ACTIVE.stream().noneMatch(d -> d.getId() == id));
        LOOPING.removeIf(id -> ACTIVE.stream().noneMatch(d -> d.getId() == id));
        WIPED.removeIf(id -> ACTIVE.stream().noneMatch(d -> d.getId() == id));
        for (DomainEntity d : ACTIVE) {
            int last = CUED.getOrDefault(d.getId(), -1);
            for (int k = last + 1; k <= d.age; k++) cue(mc, d, k);
            CUED.put(d.getId(), d.age);
        }
        LoopTextures.tick();
    }

    private static float t(DomainEntity d) {
        return d.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    /** The domain whose inside the camera is in (or will be: it is within the radius), if any. */
    public static DomainEntity around(Vec3d cam) {
        DomainEntity best = null;
        double bd = Double.MAX_VALUE;
        for (DomainEntity d : ACTIVE) {
            double dist = d.getPos().distanceTo(cam);
            if (dist <= d.getRadius() + 0.5 && dist < bd) { bd = dist; best = d; }
        }
        return best;
    }

    private static boolean ownedByMe(DomainEntity d) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && d.getOwnerId() == mc.player.getId();
    }

    /** What the camera sees of domain d now (the expansion phases are for its caster, and for victims if enabled). */
    public static Phase phase(DomainEntity d, float t) {
        boolean full = ownedByMe(d) || ConfigManager.get().client.domainExpansionForVictims;
        if (d.isInstant()) {
            if (t < I_WHITE) return Phase.SEAL;
            if (t < I_INK) return full ? Phase.WHITE : Phase.SEAL;
            if (t < I_VOID) return full ? Phase.INK : Phase.SEAL;
            if (d.interior(t)) return Phase.VOID;
            return Phase.NONE;
        }
        if (t < D_WHITE) return Phase.SEAL;
        if (t < D_INK) return full ? Phase.WHITE : Phase.SEAL;
        if (t < D_TUNNEL) return full ? Phase.INK : Phase.SEAL;
        if (t < D_OPEN) return full ? Phase.TUNNEL : Phase.SEAL;
        if (d.interior(t)) return Phase.VOID;
        return Phase.NONE;
    }

    /** The domain (and phase) the camera is inside right now, for renderers and mixins. */
    public static DomainEntity current() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return null;
        return around(mc.gameRenderer.getCamera().getPos());
    }

    /** Hide the world's terrain this frame (inside the void, or in the white / ink / tunnel). */
    public static boolean hideTerrain() {
        if (!ConfigManager.get().client.domainHideTerrain) return false;
        DomainEntity d = current();
        if (d == null) return false;
        Phase p = phase(d, t(d));
        return p == Phase.VOID || p == Phase.TUNNEL || p == Phase.WHITE || p == Phase.INK;
    }

    /** While the camera is inside a void, things outside its barrier are not drawn. */
    public static boolean hiddenByBarrier(Entity e) {
        if (e instanceof DomainEntity) return false;
        DomainEntity d = current();
        if (d == null || phase(d, t(d)) != Phase.VOID) return false;
        return e.getPos().squaredDistanceTo(d.getPos()) > (d.getRadius() + 1) * (d.getRadius() + 1);
    }

    /** The caster can't walk while the seal is held (and the cutscene runs). */
    public static boolean locked() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !ConfigManager.get().domain.rootWhileExpanding) return false;
        for (DomainEntity d : ACTIVE) if (ownedByMe(d) && d.age < d.expandTick() + (d.isInstant() ? 2 : D_OPEN - D_INK)) return true;
        return false;
    }

    // ------------------------------------------------------------------ timeline cues
    private static void cue(MinecraftClient mc, DomainEntity d, int k) {
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        boolean inside = d.getPos().distanceTo(cam) <= d.getRadius() + 0.5;
        boolean mine = ownedByMe(d);
        if (d.isInstant()) {
            if (k == I_VOID && inside) HudOverlay.flash(0.5f, 0.85f, 0.9f, 1f);
            if (k == I_WIPE - 1 && inside) InsertPlayer.play("domain_ink_wipe");
            if (k == I_WIPE + 11 && inside) HudOverlay.flash(0.9f, 1f, 1f, 1f);
            if (k == 0 && mine) HudOverlay.title("domain", 26);
            return;
        }
        if (k == 4 && mine) LoopTextures.preload("void_pano", 0);
        if (k == 20 && mine) HudOverlay.title("domain", 40);
        if (k == D_OPEN - 30) { LoopTextures.preload("void_pano", 0); LoopTextures.preload("void_hole", 0); }
        if (k == D_FLASH && inside) HudOverlay.flash(1.0f, 1f, 1f, 1f);
        if (k == D_OPEN && inside && !LOOPING.contains(d.getId())) {
            LOOPING.add(d.getId());
            mc.getSoundManager().play(new TrackedLoopSound(ModSounds.DOMAIN_VOID, () -> mc.gameRenderer.getCamera().getPos(),
                    () -> d.interior(d.age) && around(mc.gameRenderer.getCamera().getPos()) == d ? 1.0 : 0.0, () -> 1.0,
                    () -> !d.isRemoved() && d.age < d.getCloseAt() + 12));
        }
        // the collapse (on time, or early when the caster pressed again): the white ink wipes the void away
        if (k >= d.getCloseAt() - 5 && inside && WIPED.add(d.getId())) InsertPlayer.play("domain_ink_wipe");
        if (k == d.getCloseAt() + 7 && inside) HudOverlay.flash(1.0f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ HUD
    /** Pink-violet cast over the characters while the world is white (S1E7 3.25–4.0 s): 0..1. */
    public static float whiteTint() {
        DomainEntity d = current();
        if (d == null) return 0f;
        float t = t(d);
        return phase(d, t) == Phase.WHITE || phase(d, t) == Phase.INK ? 1f : 0f;
    }

    /** Strength of the information flood on a paralysed viewer's screen. */
    public static float victimOverlay() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !ConfigManager.get().client.domainVictimOverlay) return 0f;
        var fx = mc.player.getStatusEffect(ModEffects.UNLIMITED_VOID);
        if (fx == null) return 0f;
        return MathHelper.clamp(fx.getDuration() / 20f, 0f, 1f);
    }

    // ------------------------------------------------------------------ poses / eyes
    public static float eyeGlow(LivingEntity caster) {
        for (DomainEntity d : ACTIVE) {
            if (d.getOwnerId() != caster.getId()) continue;
            float t = t(d);
            if (d.isInstant()) return t < I_END ? 1f : 0f;
            // the Six Eyes open wide with the domain (S1E7 0.0–0.75 s), and stay lit inside it
            return (float) MathHelper.clamp(Math.min(t / 6f, 1f) * (t < d.getCloseAt() ? 1f : 1f - (t - d.getCloseAt()) / 10f), 0f, 1f);
        }
        return 0f;
    }

    public static void clear() { ACTIVE.clear(); CUED.clear(); LOOPING.clear(); WIPED.clear(); }
}
