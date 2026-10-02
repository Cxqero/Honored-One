package com.gojolimitless.client.input;

import com.gojolimitless.ability.MoveType;
import com.gojolimitless.net.Payloads;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Five technique keys (tap / hold) plus the Infinity toggle. Press and release are both sent to the server. */
public final class Keybinds {
    private Keybinds() {}

    private static final String CAT = "category.gojolimitless";
    private static final KeyBinding[] MOVES = new KeyBinding[MoveType.values().length];
    private static final boolean[] DOWN = new boolean[MoveType.values().length];
    // a press seen since the last tick: a tap shorter than one tick (press and release between two ticks) still counts
    private static final boolean[] LATCH = new boolean[MoveType.values().length];
    private static KeyBinding infinity, skip;

    public static void register() {
        int[] defaults = {GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_Z};
        for (MoveType m : MoveType.values()) {
            MOVES[m.ordinal()] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.gojolimitless." + m.key, InputUtil.Type.KEYSYM, defaults[m.ordinal()], CAT));
        }
        infinity = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gojolimitless.infinity", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CAT));
        skip = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gojolimitless.skip", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CAT));
    }

    /** Is this key bound to a technique, and are we playing (in a world, no screen open)? */
    public static boolean owns(InputUtil.Key key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || key.equals(InputUtil.UNKNOWN_KEY)) return false;
        String id = key.getTranslationKey();
        for (KeyBinding k : MOVES) if (k != null && k.getBoundKeyTranslationKey().equals(id)) return true;
        return false;
    }

    /** While playing, a technique key drives its technique even if another binding shares the key. */
    public static boolean claim(InputUtil.Key key, boolean pressed) {
        if (!owns(key)) return false;
        String id = key.getTranslationKey();
        for (int i = 0; i < MOVES.length; i++) {
            if (!MOVES[i].getBoundKeyTranslationKey().equals(id)) continue;
            MOVES[i].setPressed(pressed);
            if (pressed) LATCH[i] = true;
        }
        return true;
    }

    private static int reportIn = -1;

    /** Joined a world: check for shared keys once the player is in (two seconds later). */
    public static void onJoin() { reportIn = 40; }

    /** Say once if a technique key is shared, and with what. */
    private static void reportConflicts(MinecraftClient mc) {
        if (mc.player == null) return;
        for (KeyBinding k : MOVES) {
            if (k.isUnbound()) continue;
            for (KeyBinding other : mc.options.allKeys) {
                if (other == k || other.isUnbound() || java.util.Arrays.asList(MOVES).contains(other)) continue;
                if (!other.getBoundKeyTranslationKey().equals(k.getBoundKeyTranslationKey())) continue;
                mc.player.sendMessage(net.minecraft.text.Text.translatable("message.gojolimitless.key_shared",
                        k.getBoundKeyLocalizedText(), net.minecraft.text.Text.translatable(k.getTranslationKey()),
                        net.minecraft.text.Text.translatable(other.getTranslationKey())).formatted(net.minecraft.util.Formatting.GRAY), false);
            }
        }
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (reportIn > 0 && --reportIn == 0) reportConflicts(mc);
        for (MoveType m : MoveType.values()) {
            int i = m.ordinal();
            boolean now = MOVES[i].isPressed() || (LATCH[i] && !DOWN[i]);
            LATCH[i] = false;
            if (now != DOWN[i]) {
                DOWN[i] = now;
                ClientPlayNetworking.send(new Payloads.Input(i, now));
            }
        }
        while (infinity.wasPressed()) ClientPlayNetworking.send(new Payloads.InfinityToggle());
        while (skip.wasPressed()) {
            com.gojolimitless.client.cutscene.CutsceneDirector.skip();
            com.gojolimitless.client.cutscene.InsertPlayer.clearAll();
        }
    }
}
