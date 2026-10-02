package com.gojolimitless.client.render;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.config.LimitlessConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Detects an active Iris shaderpack (via reflection, so Iris stays optional) and whether Iris is currently
 * drawing its shadow map — energy effects must not cast shadows.
 */
public final class RenderPath {
    private RenderPath() {}

    private static boolean probed;
    private static Object api;
    private static Method packInUse, shadowPass;

    private static void probe() {
        probed = true;
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            packInUse = c.getMethod("isShaderPackInUse");
            shadowPass = c.getMethod("isRenderingShadowPass");
        } catch (Throwable t) {
            api = null;
        }
    }

    public static boolean shaderPackActive() {
        if (!probed) probe();
        if (api == null) return false;
        try { return (boolean) packInUse.invoke(api); } catch (Throwable t) { return false; }
    }

    public static boolean inShadowPass() {
        if (!probed) probe();
        if (api == null) return false;
        try { return (boolean) shadowPass.invoke(api); } catch (Throwable t) { return false; }
    }

    /** True when the shaderpack-safe path should be used. */
    public static boolean safe() {
        LimitlessConfig.RenderPathMode m = ConfigManager.get().client.renderPath;
        if (m == LimitlessConfig.RenderPathMode.SHADERPACK_SAFE) return true;
        if (m == LimitlessConfig.RenderPathMode.ENHANCED) return false;
        return shaderPackActive();
    }

    /**
     * Shaderpacks add their own bloom on top of emissive geometry; tone our glow down a little there
     * so it doesn't blow out, and push it up without a pack where our baked halos are the only bloom.
     */
    public static float glowScale() {
        float g = (float) ConfigManager.get().client.glow;
        return shaderPackActive() ? g * 0.8f : g;
    }
}
