package com.gojolimitless.client;

import com.gojolimitless.ability.CastStage;
import com.gojolimitless.ability.MoveType;
import com.gojolimitless.client.cam.CameraShake;
import com.gojolimitless.client.fx.FxManager;
import com.gojolimitless.client.fx.effects.BlueCollapseFx;
import com.gojolimitless.client.fx.effects.BlueFormFx;
import com.gojolimitless.client.fx.effects.RippleFx;
import com.gojolimitless.client.hud.HudOverlay;
import com.gojolimitless.client.input.Keybinds;
import com.gojolimitless.client.pose.PoseLibrary;
import com.gojolimitless.client.pose.PoseManager;
import com.gojolimitless.client.render.BlueOrbRenderer;
import com.gojolimitless.client.render.FxHostRenderer;
import com.gojolimitless.client.render.RedOrbRenderer;
import com.gojolimitless.client.fx.effects.RedBlastFx;
import com.gojolimitless.client.fx.effects.RedFireFx;
import com.gojolimitless.client.sound.RedChargeSound;
import com.gojolimitless.entity.RedOrbEntity;
import com.gojolimitless.client.sound.OrbLoopSound;
import com.gojolimitless.entity.BlueOrbEntity;
import com.gojolimitless.net.FxType;
import com.gojolimitless.net.Payloads;
import com.gojolimitless.registry.ModEntities;
import com.gojolimitless.registry.ModSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.math.Vec3d;

public class GojoLimitlessClient implements ClientModInitializer {
    public static boolean infinityOn = true;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.BLUE_ORB, BlueOrbRenderer::new);
        EntityRendererRegistry.register(ModEntities.FX_HOST, FxHostRenderer::new);
        EntityRendererRegistry.register(ModEntities.RED_ORB, RedOrbRenderer::new);
        EntityRendererRegistry.register(ModEntities.PURPLE, com.gojolimitless.client.render.PurpleRenderer::new);
        EntityRendererRegistry.register(ModEntities.NUKE, com.gojolimitless.client.render.NukeRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOMAIN, com.gojolimitless.client.render.DomainRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer pr) {
                helper.register(new com.gojolimitless.client.render.EyeGlowFeature(pr));
                helper.register(new com.gojolimitless.client.render.FingerFeature(pr, ctx.getHeldItemRenderer()));
            }
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new PoseLibrary());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new com.gojolimitless.client.anim.HandShapes());
        com.gojolimitless.client.anim.CastAnimator.init();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new com.gojolimitless.client.cutscene.CutsceneLibrary());
        Keybinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Keybinds.tick(mc);
            if (!mc.isPaused()) com.gojolimitless.client.cutscene.CutsceneDirector.clientTick();
            FxManager.tick(mc);
            com.gojolimitless.client.nuke.NukeClient.tick(mc);
            com.gojolimitless.client.domain.DomainClient.tick(mc);
            CameraShake.tick();
            HudOverlay.tick();
            updateChargeRing(mc);
            updateRedCharge(mc);
            AutoTest.tick(mc);
        });
        HudRenderCallback.EVENT.register(HudOverlay::render);

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof com.gojolimitless.entity.NukeEntity ne) com.gojolimitless.client.nuke.NukeClient.onLoad(ne);
            if (entity instanceof com.gojolimitless.entity.PurpleEntity pe) {
                var sm = MinecraftClient.getInstance().getSoundManager();
                if (pe.isFull()) sm.play(new com.gojolimitless.client.sound.FollowLoopSound<>(pe, ModSounds.PURPLE_CHARGE,
                        x -> x.getPhase() == com.gojolimitless.entity.PurpleEntity.PHASE_CHARGING, x -> 0.6 + 1.2 * x.getPower(), x -> 0.8 + 0.3 * x.getPower()));
                sm.play(new com.gojolimitless.client.sound.FollowLoopSound<>(pe, ModSounds.PURPLE_HUM,
                        x -> x.getPhase() != com.gojolimitless.entity.PurpleEntity.PHASE_FADING,
                        x -> x.getPhase() == com.gojolimitless.entity.PurpleEntity.PHASE_CHARGING ? 0.0 : (x.isFull() ? 3.0 : 1.6),
                        x -> x.isFull() ? 0.75 : 1.0));
            }
            if (entity instanceof RedOrbEntity red && red.isCharged()) {
                MinecraftClient.getInstance().getSoundManager().play(new RedChargeSound(red));
            }
            if (entity instanceof BlueOrbEntity orb) {
                boolean max = orb.getMode() != BlueOrbEntity.MODE_TAP;
                MinecraftClient.getInstance().getSoundManager().play(new OrbLoopSound(orb, max ? ModSounds.BLUE_MAX_LOOP : ModSounds.BLUE_LOOP, max));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> Keybinds.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            PoseManager.clear(); FxManager.DEBRIS.clear();
            com.gojolimitless.client.nuke.NukeClient.clear();
            com.gojolimitless.client.domain.DomainClient.clear();
            com.gojolimitless.client.render.LoopTextures.clear();
            com.gojolimitless.client.render.DeferredVfx.clear();
            com.gojolimitless.client.cutscene.CutsceneDirector.stop();
            com.gojolimitless.client.cutscene.InsertPlayer.clearAll();
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Cutscene.ID, (p, ctx) ->
                com.gojolimitless.client.cutscene.CutsceneDirector.play(p.name(), new Vec3d(p.x(), p.y(), p.z()), p.yaw()));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Debris.ID, (p, ctx) ->
                FxManager.DEBRIS.spawn(ctx.client().world, p.ownerId(), p.mode(), p.ox(), p.oy(), p.oz(), p.data()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Fx.ID, (p, ctx) -> onFx(ctx.client(), p));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.CastState.ID, (p, ctx) -> onCast(ctx.client(), p));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.InfinityState.ID, (p, ctx) -> infinityOn = p.on());
    }

    private static void onFx(MinecraftClient mc, Payloads.Fx p) {
        Vec3d pos = new Vec3d(p.x(), p.y(), p.z());
        switch (p.type()) {
            case FxType.BLUE_FORM -> FxManager.add(new BlueFormFx(pos, p.a()));
            case FxType.BLUE_THROW -> {
                FxManager.add(new BlueFormFx(pos, p.a() * 0.7f));
                CameraShake.add(0.25f, pos, 40);
            }
            case FxType.BLUE_COLLAPSE -> {
                FxManager.add(new BlueCollapseFx(pos, p.a()));
                if (p.b() > 0.5f) FxManager.add(new com.gojolimitless.client.fx.effects.BlueDisperseFx(pos, p.a()));
                CameraShake.add(Math.min(1f, 0.25f + p.a() * 0.07f), pos, 30 + p.a() * 8);
                if (mc.player != null) {
                    double d = mc.player.getPos().distanceTo(pos);
                    float f = (float) Math.max(0, 1 - d / (20 + p.a() * 6)) * 0.35f;
                    if (f > 0) HudOverlay.flash(f, 0.75f, 0.88f, 1f);
                }
            }
            case FxType.INFINITY_RIPPLE -> FxManager.add(new RippleFx(pos, p.a()));
            case FxType.RED_FIRE -> {
                FxManager.add(new RedFireFx(pos, p.a()));
                CameraShake.add(0.12f + 0.3f * p.a(), pos, 12);
            }
            case FxType.RED_DETONATE -> {
                com.gojolimitless.GojoLimitless.LOG.info("[fx] red detonate at {} R={} pw={}", pos, p.a(), p.b());
                FxManager.add(new RedBlastFx(pos, p.a(), p.b()));
                CameraShake.add(Math.min(1f, 0.3f + p.a() * 0.05f), pos, 40 + p.a() * 6);
                if (mc.player != null) {
                    double d = mc.player.getPos().distanceTo(pos);
                    float f = (float) Math.max(0, 1 - d / (25 + p.a() * 5)) * 0.45f;
                    if (f > 0) HudOverlay.flash(f, 1f, 0.55f, 0.45f);
                }
            }
            case FxType.NUKE_EXPLODE -> {
                double gy = mc.world != null ? mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(pos.x), (int) Math.floor(pos.z)) : pos.y - 20;
                FxManager.add(new com.gojolimitless.client.fx.effects.PurpleBlastFx(pos, p.a(), gy, true));
                CameraShake.add(1.6f, pos, p.a() * 8);
                if (mc.player != null && mc.player.getPos().distanceTo(pos) < p.a() * 6) HudOverlay.flash(0.45f, 1f, 0.85f, 1f);
            }
            case FxType.PURPLE_IMPACT -> {
                double gy = mc.world != null ? mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(pos.x), (int) Math.floor(pos.z)) : pos.y - 2;
                FxManager.add(new com.gojolimitless.client.fx.effects.PurpleBlastFx(pos, p.a() * (1.6f + 0.4f * p.b()), gy, false));
                CameraShake.add(0.55f + 0.5f * p.b(), pos, 30 + p.a() * 6);
                if (mc.player != null && mc.player.getPos().distanceTo(pos) < 25 + p.a() * 4) HudOverlay.flash(0.35f + 0.25f * p.b(), 0.95f, 0.8f, 1f);
            }
            case FxType.PURPLE_COLLIDE -> {
                FxManager.add(new com.gojolimitless.client.fx.effects.PurpleFx(com.gojolimitless.client.fx.effects.PurpleFx.Kind.COLLIDE, pos, p.a(), p.b()));
                CameraShake.add(0.35f + 0.4f * p.b(), pos, 40 + p.a() * 4);
                if (mc.player != null && mc.player.getPos().distanceTo(pos) < 30 + p.a() * 3) HudOverlay.flash(0.35f + 0.3f * p.b(), 0.9f, 0.75f, 1f);
            }
            case FxType.PURPLE_LAUNCH -> {
                FxManager.add(new com.gojolimitless.client.fx.effects.PurpleFx(com.gojolimitless.client.fx.effects.PurpleFx.Kind.LAUNCH, pos, p.a(), p.b()));
                CameraShake.add(0.45f + 0.5f * p.b(), pos, 60 + p.a() * 5);
            }
            case FxType.PURPLE_STAGE -> {
                if (mc.player != null && mc.world != null && mc.world.getEntityById(p.entityId()) instanceof com.gojolimitless.entity.PurpleEntity pe
                        && pe.getOwnerId() == mc.player.getId()) {
                    HudOverlay.title("purple_" + (int) p.a(), 36);
                    CameraShake.add(0.1f + 0.05f * p.a(), pos, 10);
                }
            }
            case FxType.NUKE_COLLIDE -> {
                // with the cutscene on, the full-screen insert covers this beat
                if (!com.gojolimitless.client.cutscene.CutsceneDirector.active()) HudOverlay.flash(0.9f, 1f, 0.92f, 1f);
                CameraShake.add(0.5f, pos, 200);
            }
            case FxType.NUKE_BLOOM -> {
                // in the cinematic the eruption lights his face first and the white swallows the frame a beat later
                if (com.gojolimitless.client.cutscene.CutsceneDirector.active()) HudOverlay.flash(0.35f, 1f, 0.6f, 1f);
                else HudOverlay.flash(1.0f, 0.95f, 0.75f, 1f);
                CameraShake.add(1.0f, pos, 120 + p.a() * 4);
            }
            case FxType.RED_STAGE -> {
                if (mc.player != null && mc.world != null && mc.world.getEntityById(p.entityId()) instanceof RedOrbEntity orb
                        && orb.getOwnerId() == mc.player.getId()) {
                    HudOverlay.title("red_" + (int) p.a(), 34);
                    CameraShake.add(0.08f + 0.05f * p.a(), pos, 10);
                }
                FxManager.add(new RedFireFx(pos, 0.15f * p.a()));
            }
            default -> {}
        }
    }

    private static void onCast(MinecraftClient mc, Payloads.CastState p) {
        PoseManager.onCastState(p.casterId(), p.move(), p.stage(), p.durationTicks());
        if (mc.player != null && p.casterId() == mc.player.getId()) {
            MoveType m = MoveType.byId(p.move());
            if (m == MoveType.RED && (p.stage() == CastStage.TAP || p.stage() == CastStage.RELEASE)) HudOverlay.title("red", 45);
            // with cutscenes on, the title card is timed by the cutscene's own marker at the launch
            var cc = com.gojolimitless.config.ConfigManager.get().client;
            if (m == MoveType.PURPLE && p.stage() == CastStage.TAP && !(cc.cutscenes && cc.cutsceneTapPurple)) HudOverlay.title("purple", 60);
            if (m == MoveType.PURPLE && p.stage() == CastStage.RELEASE && !cc.cutscenes) HudOverlay.title("purple_200", 80);
            if (m == MoveType.BLUE) {
                if (p.stage() == CastStage.TAP) HudOverlay.title("blue", 50);
                if (p.stage() == CastStage.CHARGING) HudOverlay.title("blue_max", 70);
            }
        }
    }

    /** While our incantation Red charges, the edges of the screen bleed red. */
    private static void updateRedCharge(MinecraftClient mc) {
        float v = 0;
        if (mc.player != null && mc.world != null) {
            for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(8), x -> x instanceof RedOrbEntity)) {
                RedOrbEntity r = (RedOrbEntity) e;
                if (r.getOwnerId() == mc.player.getId() && r.getMode() == RedOrbEntity.MODE_CHARGING && r.isCharged())
                    v = Math.max(v, 0.25f + 0.55f * r.getPower());
            }
        }
        float pv = 0;
        if (mc.player != null && mc.world != null) {
            for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(8), x -> x instanceof com.gojolimitless.entity.PurpleEntity)) {
                var pe = (com.gojolimitless.entity.PurpleEntity) e;
                if (pe.getOwnerId() == mc.player.getId() && pe.getPhase() == com.gojolimitless.entity.PurpleEntity.PHASE_CHARGING)
                    pv = Math.max(pv, 0.3f + 0.6f * pe.getPower());
            }
        }
        v = Math.max(v, com.gojolimitless.client.nuke.NukeClient.redVignette());
        if (pv > 0) HudOverlay.vignette("violet", pv);
        else HudOverlay.vignette(v > 0 ? "red" : null, v);
    }

    /** While our Maximum Output: Blue charges, show its growth on the crosshair ring. */
    private static void updateChargeRing(MinecraftClient mc) {
        float c = -1;
        if (mc.player != null && mc.world != null) {
            for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(64), x -> x instanceof BlueOrbEntity)) {
                BlueOrbEntity b = (BlueOrbEntity) e;
                if (b.getOwnerId() == mc.player.getId() && b.getMode() == BlueOrbEntity.MODE_ORBIT) c = Math.max(c, b.getCharge());
            }
        }
        HudOverlay.setCharge(c);
    }
}
