package com.gojolimitless.client;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.client.fx.FxManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development-only visual test harness. Enabled with the environment variable GOJO_AUTOTEST=<scenario>;
 * runs a scripted sequence (commands, casts, camera changes) in a singleplayer world and saves screenshots
 * to run/screenshots/, then quits. Does nothing in normal play.
 */
final class AutoTest {
    private AutoTest() {}

    private static final String SCENARIO = System.getenv("GOJO_AUTOTEST");
    private static final List<Step> STEPS = new ArrayList<>();
    private static int tick = -60;     // negative: let chunks load first
    private static int idx;
    private static boolean built;

    private record Step(int at, Consumer<MinecraftClient> action) {}

    private static void at(int t, Consumer<MinecraftClient> a) { STEPS.add(new Step(t, a)); }

    private static void cmd(int t, String c) { at(t, mc -> run(mc, c)); }

    private static void shot(int t, String name) {
        at(t, mc -> {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, SCENARIO + "_" + name + ".png", mc.getFramebuffer(), msg -> {});
            StringBuilder ents = new StringBuilder();
            if (mc.world != null) for (var en : mc.world.getEntities()) {
                if (en instanceof com.gojolimitless.entity.PurpleEntity p)
                    ents.append(String.format(" purple[phase=%d pos=%.1f,%.1f,%.1f]", p.getPhase(), p.getX(), p.getY(), p.getZ()));
                if (en instanceof com.gojolimitless.entity.DomainEntity d)
                    ents.append(String.format(" domain[age=%d instant=%s r=%.0f close=%d phase=%s]", d.age, d.isInstant(), d.getRadius(), d.getCloseAt(),
                            com.gojolimitless.client.domain.DomainClient.phase(d, d.age)));
                if (en instanceof com.gojolimitless.entity.NukeEntity n)
                    ents.append(String.format(" nuke[age=%d C=%.1f,%.1f,%.1f r=%.1f]", n.age, n.getX(), n.getY(), n.getZ(), n.blastRadiusAt(n.age)));
            }
            var cam = mc.gameRenderer.getCamera().getPos();
            GojoLimitless.LOG.info("[autotest] shot {} (fx={}, debris={}, fps={}) player={} cam={},{},{}{}", name, FxManager.effectCount(), FxManager.DEBRIS.size(), mc.getCurrentFps(), mc.player.getPos(),
                    String.format("%.1f", cam.x), String.format("%.1f", cam.y), String.format("%.1f", cam.z), ents);
        });
    }

    private static int bb(int b) { return b; }

    private static void view(int t, Perspective p) { at(t, mc -> mc.options.setPerspective(p)); }

    /** Who is paralysed by Unlimited Void right now (read from the integrated server). */
    private static void logVoid(MinecraftClient mc, String when) {
        var server = mc.getServer();
        if (server == null) return;
        StringBuilder sb = new StringBuilder();
        for (var e : server.getOverworld().iterateEntities()) {
            if (e instanceof net.minecraft.entity.LivingEntity le) {
                var fx = le.getStatusEffect(com.gojolimitless.registry.ModEffects.UNLIMITED_VOID);
                sb.append(String.format(" %s[%s d=%.1f v=%.2f]", e.getType().getUntranslatedName(), fx == null ? "free" : "void " + fx.getDuration(),
                        e.distanceTo(mc.player), e.getVelocity().horizontalLength()));
            }
        }
        GojoLimitless.LOG.info("[autotest] {}:{}", when, sb);
    }

    /** Fixed test cameras around the player (who faces north, -z): front34, side, back34, low, face, fp. */
    private static void camFor(MinecraftClient mc, String v) {
        var p = mc.player.getPos();
        var chest = p.add(0, 1.1, 0);
        var cd = com.gojolimitless.client.cutscene.CutsceneDirector.class;
        switch (v) {
            case "fp" -> {
                com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(null, null);
                mc.options.setPerspective(Perspective.FIRST_PERSON);
            }
            case "front34" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(1.5, 1.5, -2.9), chest);
            case "front" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(0, 1.4, -3.3), chest);
            case "side" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(3.3, 1.3, -0.2), chest);
            case "back34" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(-1.6, 1.8, 2.7), chest);
            case "low" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(1.0, 0.35, -2.4), p.add(0, 1.3, 0));
            case "face" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(0.5, 1.75, -1.3), p.add(0, 1.55, 0));
            // Blender preview cameras (blender = (-x, z, y)): hero, side2
            case "hero" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(1.25, 1.35, -1.85), p.add(0, 1.15, 0));
            case "side2" -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(p.add(2.2, 1.25, -0.35), p.add(0, 1.15, 0));
            default -> {}
        }
    }

    private static void run(MinecraftClient mc, String c) {
        IntegratedServer s = mc.getServer();
        if (s == null || mc.player == null) return;
        s.execute(() -> {
            ServerPlayerEntity sp = s.getPlayerManager().getPlayer(mc.player.getUuid());
            if (sp != null) s.getCommandManager().executeWithPrefix(sp.getCommandSource().withLevel(4), c);
        });
    }

    private static void build() {
        built = true;
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tc) -> frames++);
        String[] setup = {"gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear",
                "gamerule doMobSpawning false", "gamemode creative", "difficulty peaceful"};
        for (String c : setup) cmd(1, c);
        cmd(1, "tp @s " + System.getenv().getOrDefault("GOJO_X", "0") + " 180 " + System.getenv().getOrDefault("GOJO_Z", "0"));
        cmd(1, "effect give @s minecraft:slow_falling 30 0 true");
        cmd(2, "recipe give @s *");
        String x = System.getenv().getOrDefault("GOJO_X", "0"), z = System.getenv().getOrDefault("GOJO_Z", "0");
        String ground = "execute positioned " + x + " 0 " + z + " positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ ";
        String pos = x + " ~ " + z;
        switch (SCENARIO) {
            case "scout" -> {
                cmd(2, "locate biome minecraft:plains");
                cmd(3, "locate biome minecraft:meadow");
                cmd(4, "locate biome minecraft:savanna");
                cmd(5, "locate biome minecraft:windswept_hills");
                cmd(6, "locate biome minecraft:badlands");
                at(60, MinecraftClient::scheduleStop);
            }
            case "look" -> {
                cmd(2, "time set 6000");
                cmd(35, ground + "0 10");
                for (int i = 0; i < 4; i++) {
                    int yaw = i * 90;
                    cmd(40 + i * 40, ground + yaw + " 10");
                    shot(70 + i * 40, "yaw" + yaw);
                }
                at(240, MinecraftClient::scheduleStop);
            }
            case "config" -> {
                at(20, mc -> mc.setScreen(com.gojolimitless.client.config.YaclScreen.create(null)));
                shot(40, "screen_general");
                at(45, mc -> { if (mc.currentScreen != null) mc.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0); });
                at(50, MinecraftClient::scheduleStop);
            }
            case "red" -> {
                String time = System.getenv().getOrDefault("GOJO_TIME", "6000");
                String yaw = System.getenv().getOrDefault("GOJO_YAW", "180");
                cmd(2, "time set " + time);
                cmd(95, ground + yaw + " 6");
                cmd(110, "execute at @s run summon cow ^1 ^ ^16");
                cmd(110, "execute at @s run summon sheep ^-2 ^ ^18");
                cmd(128, "tick rate 5");
                cmd(130, "limitless cast red");
                shot(133, "tap_form");
                shot(136, "tap_fly");
                shot(139, "tap_blast_1");
                shot(143, "tap_blast_2");
                shot(152, "tap_blast_3");
                shot(175, "tap_after");
                cmd(180, "tick rate 20");
                view(185, Perspective.THIRD_PERSON_BACK);
                cmd(190, ground + yaw + " 3");
                cmd(200, "limitless cast red 75");
                cmd(270, "tick rate 5");
                shot(215, "charge_10");
                shot(240, "charge_35");
                shot(265, "charge_60");
                shot(276, "charge_full");
                shot(278, "release");
                shot(281, "fly_1");
                shot(284, "fly_2");
                shot(290, "blast_1");
                shot(296, "blast_2");
                shot(310, "blast_3");
                cmd(325, "tick rate 20");
                view(330, Perspective.FIRST_PERSON);
                shot(420, "aftermath");
                at(430, MinecraftClient::scheduleStop);
            }
            case "purple" -> {
                String time = System.getenv().getOrDefault("GOJO_TIME", "6000");
                String yaw = System.getenv().getOrDefault("GOJO_YAW", "180");
                cmd(2, "time set " + time);
                cmd(95, ground + yaw + " 2");
                cmd(110, "execute at @s run summon cow ^1 ^ ^20");
                cmd(110, "execute at @s run summon sheep ^-2 ^ ^30");
                int t0 = 130;
                cmd(t0 - 2, "tick rate 4");
                cmd(t0, "limitless cast purple");
                shot(t0 + 8, "tap_08_hands");
                shot(t0 + 17, "tap_17_closing");
                shot(t0 + 21, "tap_21_collide");
                shot(t0 + 28, "tap_28_mass");
                shot(t0 + 36, "tap_36_launch");
                shot(t0 + 44, "tap_44_chase");
                cmd(t0 + 50, "tick rate 20");
                shot(t0 + 75, "tap_after");
                int t1 = 240;
                view(t1 - 5, Perspective.THIRD_PERSON_BACK);
                cmd(t1 - 4, ground + yaw + " 2");
                cmd(t1, "limitless cast purple 90");
                cmd(t1 + 80, "tick rate 4");
                shot(t1 + 18, "c_sign1");
                shot(t1 + 38, "c_sign2");
                shot(t1 + 58, "c_sign3");
                shot(t1 + 84, "c_sign4");
                int r = t1 + 90;                    // release
                shot(r + 8, "r_08_wide");
                shot(r + 17, "r_17_collide");
                shot(r + 30, "r_30_swell");
                shot(r + 60, "r_60_behind");
                shot(r + 46, "r_46_launch");
                shot(r + 70, "r_70_carve");
                shot(r + 100, "r_100_far");
                cmd(r + 105, "tick rate 20");
                view(r + 120, Perspective.FIRST_PERSON);
                shot(r + 200, "aftermath");
                at(r + 210, MinecraftClient::scheduleStop);
            }
            case "nuke" -> {
                String time = System.getenv().getOrDefault("GOJO_TIME", "12800");
                String yaw = System.getenv().getOrDefault("GOJO_YAW", "180");
                int slow = Integer.parseInt(System.getenv().getOrDefault("GOJO_SLOW", "5"));
                cmd(2, "time set " + time);
                cmd(90, "gamemode survival");
                cmd(90, "difficulty easy");
                cmd(95, ground + yaw + " 4");
                // the "target" and the creatures around it, ~40 blocks ahead
                if ("1".equals(System.getenv("GOJO_MOUNTAIN"))) {       // a 19-block mound, the way it plays out in hills
                    cmd(100, "execute at @s run fill ^-20 ^ ^28 ^20 ^18 ^56 stone");
                    cmd(110, "execute at @s run summon iron_golem ^ ^19 ^42");
                } else
                cmd(110, "execute at @s run summon iron_golem ^ ^ ^40");
                cmd(110, "execute at @s run summon cow ^3 ^ ^42");
                cmd(110, "execute at @s run summon sheep ^-3 ^ ^38");
                cmd(110, "execute at @s run summon pig ^2 ^ ^45");
                cmd(110, "execute at @s run summon horse ^-4 ^ ^44");
                at(128, mc -> GojoLimitless.LOG.info("[autotest] health before nuke {}", mc.player.getHealth()));
                int t0 = 135;
                cmd(t0 - 3, "tick rate " + slow);
                cmd(t0, "limitless cast nuke");
                int[] beats = {12, 30, 50, 56, 62, 70, 80, 88, 100, 114, 132, 145, 165, 172, 178, 190, 205, 222, 240, 256, 263, 275,
                        292, 310, 330, 346, 354, 366, 384, 392, 400, 410, 425, 445, 458, 464, 474, 500, 530};
                for (int b : beats) shot(t0 + b, String.format("t%03d", b));
                at(t0 + 415, mc -> GojoLimitless.LOG.info("[autotest] health right after the bloom {}", mc.player.getHealth()));
                cmd(t0 + 560, "tick rate 20");
                at(t0 + 600, mc -> GojoLimitless.LOG.info("[autotest] health after nuke {} (slow falling: {})", mc.player.getHealth(),
                        mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING)));
                shot(t0 + 610, "after_first_person");
                view(t0 + 620, Perspective.THIRD_PERSON_BACK);
                shot(t0 + 700, "falling_third_person");
                at(t0 + 860, mc -> GojoLimitless.LOG.info("[autotest] health on landing {} y={}", mc.player.getHealth(), mc.player.getY()));
                // look at the crater from above
                cmd(t0 + 870, "gamemode spectator");
                cmd(t0 + 872, "execute at @s run tp @s ^ ^60 ^-50 " + yaw + " 42");
                shot(t0 + 930, "crater_above");
                cmd(t0 + 935, "execute at @s run tp @s ^ ^-30 ^ " + yaw + " 20");
                shot(t0 + 990, "crater_low");
                at(t0 + 1000, MinecraftClient::scheduleStop);
            }
            case "domain" -> {
                String time = System.getenv().getOrDefault("GOJO_TIME", "6000");
                cmd(2, "time set " + time);
                cmd(90, "difficulty easy");
                cmd(95, ground + "180 0");
                cmd(110, "execute at @s run summon zombie ^ ^ ^8 {NoAI:0b}");
                cmd(110, "execute at @s run summon cow ^3 ^ ^6");
                cmd(110, "execute at @s run summon villager ^-4 ^ ^10");
                cmd(110, "execute at @s run summon skeleton ^6 ^ ^14");
                cmd(110, "execute at @s run summon pig ^ ^ ^40");
                int t0 = 135, d0 = t0 + 5;                 // the hold registers after ~5 ticks: the domain's tick 0
                cmd(t0 - 3, "tick rate 8");
                cmd(t0, "limitless cast domain 20");
                int[] beats = {6, 28, 50, 70, 90, 110, 130, 150, 175, 199};
                for (int b : beats) shot(d0 + b, String.format("full_%03d", b));
                at(d0 + 204, mc -> logVoid(mc, "during the domain"));
                cmd(d0 + 206, "tick rate 20");
                view(d0 + 212, Perspective.THIRD_PERSON_BACK);
                shot(d0 + 229, "void_back");
                view(d0 + 234, Perspective.THIRD_PERSON_FRONT);
                shot(d0 + 249, "void_front");
                view(d0 + 252, Perspective.FIRST_PERSON);
                shot(d0 + 259, "void_fp");
                cmd(d0 + 262, "tick rate 8");
                cmd(d0 + 264, "limitless cast domain");      // pressed again: collapse
                shot(d0 + 267, "close_03");
                shot(d0 + 272, "close_08");
                shot(d0 + 280, "close_16");
                shot(d0 + 299, "after");
                at(d0 + 304, mc -> logVoid(mc, "after the collapse"));
                int t1 = d0 + 354;
                cmd(t1 - 4, "tick rate 4");
                cmd(t1, "limitless cast domain");
                int[] ib = {3, 9, 13, 16, 18, 22, 28, 45};
                for (int b : ib) shot(t1 + b, String.format("instant_%03d", b));
                at(t1 + 50, mc -> logVoid(mc, "after the 0.2-second domain"));
                cmd(t1 + 52, "tick rate 20");
                at(t1 + 90, MinecraftClient::scheduleStop);
            }
            case "round3" -> {
                // Maximum Output: Blue (conducted overhead, raised and dispersed), a domain cast in mid-air, the nuke's shockwave
                var cd = com.gojolimitless.client.cutscene.CutsceneDirector.class;
                cmd(2, "time set 6000");
                cmd(95, ground + "180 2");
                cmd(100, "execute at @s run fill ~-10 ~ ~-10 ~10 ~6 ~10 air");
                cmd(126, "tick rate 5");
                cmd(130, "limitless cast blue 70");
                for (int b : new int[]{20, 32, 44, 56, 66}) {
                    int bb = b;
                    at(130 + b - 1, mc -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(mc.player.getPos().add(bb % 2 == 0 ? 3.0 : -3.0, 1.9, -3.6), mc.player.getPos().add(0, 1.6, 0)));
                    shot(130 + b, String.format("blue_orbit_%02d", b));
                }
                int r = 130 + 75;                                  // released (hold + a few ticks)
                for (int b : new int[]{4, 10, 16, 22, 27, 32, 40}) {
                    at(r + b - 1, mc -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(mc.player.getPos().add(7.5, 3.5, -9.0), mc.player.getPos().add(0, 5.0, 0)));
                    shot(r + b, String.format("blue_raise_%02d", b));
                }
                at(r + 45, mc -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(null, null));
                cmd(r + 50, "tick rate 20");
                int n0 = 300;
                cmd(n0 - 6, ground + "180 4");
                cmd(n0 - 4, "tick rate 5");
                cmd(n0, "limitless cast nuke");
                for (int b : new int[]{474, 486, 500, 515, 530, 550, 575}) shot(n0 + b, String.format("nuke_%03d", b));
                cmd(n0 + 580, "tick rate 20");
                shot(n0 + 640, "nuke_after");
                at(n0 + 650, MinecraftClient::scheduleStop);
            }
            case "shaders" -> {
                // one pass over every technique (also the shaderpack check: GOJO_SHADERS=stable|newest).
                // GOJO_MOUNTAIN=1 casts the nuke at a 19-block mound, the way it plays out in hills.
                String time = System.getenv().getOrDefault("GOJO_TIME", "6000");
                String slow = System.getenv().getOrDefault("GOJO_SLOW", "2");
                cmd(2, "time set " + time);
                cmd(3, "tick rate " + slow);             // the whole pass in slow motion: shaderpacks render slowly here
                cmd(95, ground + "180 2");
                cmd(110, "execute at @s run summon cow ^1 ^ ^14");
                cmd(110, "execute at @s run summon sheep ^-2 ^ ^18");
                cmd(130, "limitless cast blue");
                shot(137, "blue");
                cmd(150, "limitless cast red");
                shot(156, "red_blast");
                view(160, Perspective.THIRD_PERSON_BACK);
                cmd(172, ground + "180 12");
                cmd(175, "limitless cast purple");
                for (int b : new int[]{36, 42, 48, 58}) shot(175 + b, String.format("ptap_%02d", b));
                cmd(236, ground + "180 2");
                cmd(240, "limitless cast purple 90");
                int r = 330;
                for (int b : new int[]{17, 30, 48, 56, 64, 74}) shot(r + b, String.format("p200_%02d", b));
                int d0 = 470;
                cmd(d0 - 5, "limitless cast domain 20");
                for (int b : new int[]{50, 75, 95, 120, 150, 165}) shot(d0 + b, String.format("domain_%03d", b));
                view(d0 + 168, Perspective.THIRD_PERSON_FRONT);
                shot(d0 + 185, "domain_void_front");
                view(d0 + 188, Perspective.FIRST_PERSON);
                cmd(d0 + 190, "limitless cast domain");
                shot(d0 + 197, "domain_close");
                shot(d0 + 225, "domain_after");
                int n0 = 760;
                cmd(n0 - 8, ground + "180 4");
                if ("1".equals(System.getenv("GOJO_MOUNTAIN"))) {
                    cmd(n0 - 6, "execute at @s run fill ^-20 ^ ^28 ^20 ^18 ^56 stone");
                    cmd(n0 - 5, "execute at @s run summon cow ^ ^19 ^42");
                }
                cmd(n0, "limitless cast nuke");
                for (int b : new int[]{62, 132, 200, 222, 263, 310, 400, 445, 474, 490, 510, 540, 575}) shot(n0 + b, String.format("nuke_%03d", b));
                shot(n0 + 640, "nuke_after");
                at(n0 + 650, MinecraftClient::scheduleStop);
            }
            case "anim" -> {
                // plays each animation in GOJO_ANIMS and photographs it at the GOJO_SHOTS times (ticks) from fixed cameras
                String time = System.getenv().getOrDefault("GOJO_TIME", "6000");
                cmd(2, "time set " + time);
                cmd(95, ground + "180 0");
                cmd(100, "execute at @s run fill ~-12 ~ ~-12 ~12 ~8 ~12 air");
                cmd(101, "execute at @s run fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 grass_block");
                cmd(102, ground + "180 0");
                at(104, mc -> mc.options.hudHidden = true);
                at(105, mc -> lockYaw = 180f);
                String[] names = System.getenv().getOrDefault("GOJO_ANIMS", "calib").split(",");
                String[] shots = System.getenv().getOrDefault("GOJO_SHOTS", "30").split(",");
                String[] views = System.getenv().getOrDefault("GOJO_VIEWS", "front34,side,back34,fp").split(",");
                int t = 120;
                for (String n : names) {
                    final String name = n;
                    at(t, mc -> com.gojolimitless.client.pose.PoseManager.debugPlay(mc.player, name));
                    int prev = 0;
                    for (String sh : shots) {
                        int dt = Integer.parseInt(sh.trim());
                        // the animation is replayed from the start for every shot time, so each view sees the same frame
                        for (String v : views) {
                            final String view = v;
                            at(t, mc -> com.gojolimitless.client.pose.PoseManager.debugPlay(mc.player, name));
                            at(t, mc -> camFor(mc, view));
                            shot(t + dt, name + "_" + dt + "_" + view);
                            t += dt + 6;
                        }
                    }
                    t += 4;
                }
                at(t, mc -> com.gojolimitless.client.cutscene.CutsceneDirector.debugCam(null, null));
                at(t + 5, MinecraftClient::scheduleStop);
            }
            case "infinity" -> {
                cmd(2, "time set 6000");
                cmd(95, ground + "180 0");
                cmd(100, "gamemode survival");
                cmd(100, "difficulty easy");
                for (int i = 0; i < 6; i++) {
                    double ox = (i - 2.5) * 0.6, oy = 1.2 + (i % 3) * 0.35;
                    cmd(130 + i * 2, String.format(java.util.Locale.ROOT,
                            "execute at @s run summon arrow ~%.2f ~%.2f ~-14 {Motion:[0.0,0.02,2.2d],damage:4.0d}", ox, oy));
                }
                cmd(132, "execute at @s run summon zombie ~1.5 ~ ~-3");
                at(133, mc -> GojoLimitless.LOG.info("[autotest] health before {}", mc.player.getHealth()));
                shot(136, "arrows_incoming");
                shot(141, "arrows_slowing");
                shot(160, "arrows_stopped");
                at(185, mc -> GojoLimitless.LOG.info("[autotest] health after arrows+zombie {}", mc.player.getHealth()));
                view(186, Perspective.THIRD_PERSON_BACK);
                shot(192, "arrows_third_person");
                cmd(200, "limitless infinity");
                shot(212, "infinity_off_drop");
                at(240, mc -> GojoLimitless.LOG.info("[autotest] health with infinity off {}", mc.player.getHealth()));
                at(245, MinecraftClient::scheduleStop);
            }
            case "blue" -> {
                String time = System.getenv().getOrDefault("GOJO_TIME", "6000");
                String yaw = System.getenv().getOrDefault("GOJO_YAW", "0");
                cmd(2, "time set " + time);
                cmd(95, ground + yaw + " 18");
                cmd(120, "summon cow ^3 ^ ^14");
                cmd(120, "summon pig ^-3 ^ ^16");
                cmd(120, "summon sheep ^ ^ ^20");
                // --- tap: Lapse: Blue
                cmd(140, "limitless cast blue");
                shot(143, "tap_02");
                shot(150, "tap_10");
                shot(165, "tap_25");
                shot(185, "tap_45");
                shot(203, "tap_63");
                shot(207, "tap_collapse");
                shot(212, "tap_after");
                // --- hold: Maximum Output: Blue (third person to see the pose)
                view(250, Perspective.THIRD_PERSON_BACK);
                cmd(260, ground + yaw + " 12");
                cmd(265, "limitless cast blue 150");
                shot(285, "max_020");
                shot(315, "max_050");
                shot(350, "max_085");
                shot(390, "max_125");
                shot(414, "max_release");
                shot(425, "max_thrown_1");
                shot(440, "max_thrown_2");
                view(455, Perspective.FIRST_PERSON);
                shot(460, "max_collapse");
                shot(472, "max_after");
                shot(520, "crater");
                shot(700, "crater_late");
                // pose + title check: tap seen from the front
                cmd(710, ground + yaw + " 5");
                view(712, Perspective.THIRD_PERSON_FRONT);
                cmd(740, "limitless cast blue");
                shot(744, "pose_tap_4");
                shot(749, "pose_tap_9");
                shot(756, "pose_tap_16");
                at(770, MinecraftClient::scheduleStop);
            }
            default -> at(5, MinecraftClient::scheduleStop);
        }
        STEPS.sort(java.util.Comparator.comparingInt(Step::at));
    }

    private static int serverStart = -1;
    private static Float lockYaw;
    private static int pausedTicks;
    // steps on different ticks get at least one rendered frame between them (shaderpacks on software GL: < 1 fps)
    private static int frames, framesAtStep = -1, lastStepAt = Integer.MIN_VALUE;

    private static boolean opened;

    /**
     * From the title screen, open the "autotest" world, or create it first (fixed seed GOJO_SEED, creative, cheats on).
     * tools/mcauto.ps1 deletes it before each run, so every run starts from the same freshly generated terrain.
     */
    private static void openWorld(MinecraftClient mc) {
        opened = true;
        String name = "autotest";
        if (mc.getLevelStorage().levelExists(name)) {
            mc.createIntegratedServerLoader().start(name, () -> mc.setScreen(new net.minecraft.client.gui.screen.TitleScreen()));
            return;
        }
        long seed = Long.parseLong(System.getenv().getOrDefault("GOJO_SEED", "20260929"));
        var info = new net.minecraft.world.level.LevelInfo(name, net.minecraft.world.GameMode.CREATIVE, false,
                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.GameRules(),
                net.minecraft.resource.DataConfiguration.SAFE_MODE);
        mc.createIntegratedServerLoader().createAndStart(name, info, new net.minecraft.world.gen.GeneratorOptions(seed, true, false),
                net.minecraft.world.gen.WorldPresets::createDemoOptions, new net.minecraft.client.gui.screen.TitleScreen());
    }

    static void tick(MinecraftClient mc) {
        if (SCENARIO == null) return;
        if (!opened && mc.world == null && mc.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) openWorld(mc);
        if (mc.player == null || mc.world == null || mc.getServer() == null) return;
        if (!built) build();
        // clock = integrated-server ticks, so screenshots stay in sync with the simulation even when rendering is slow
        int st = mc.getServer().getTicks();
        if (serverStart < 0) serverStart = st;
        if (mc.isPaused()) pausedTicks++;          // a screen paused singleplayer: keep time moving on the client clock
        tick = st - serverStart - 60 + pausedTicks;
        if (lockYaw != null) {
            // keep the body square to the test cameras (the body otherwise lags the head by up to 50°)
            mc.player.setYaw(lockYaw); mc.player.prevYaw = lockYaw;
            mc.player.setBodyYaw(lockYaw); mc.player.prevBodyYaw = lockYaw;
            mc.player.setHeadYaw(lockYaw); mc.player.prevHeadYaw = lockYaw;
            mc.player.setPitch(0f); mc.player.prevPitch = 0f;
        }
        while (idx < STEPS.size() && STEPS.get(idx).at <= tick) {
            Step s = STEPS.get(idx);
            if (s.at > lastStepAt && frames == framesAtStep) break;
            s.action.accept(mc);
            if (s.at > lastStepAt) { lastStepAt = s.at; framesAtStep = frames; }
            idx++;
        }
    }
}
