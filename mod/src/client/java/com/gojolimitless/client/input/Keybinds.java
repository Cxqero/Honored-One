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

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        for (MoveType m : MoveType.values()) {
            int i = m.ordinal();
            boolean now = MOVES[i].isPressed();
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
