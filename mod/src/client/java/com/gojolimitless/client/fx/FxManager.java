package com.gojolimitless.client.fx;

import com.gojolimitless.entity.FxHostEntity;
import com.gojolimitless.registry.ModEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the client-only {@link FxHostEntity} that rides along with the camera, plus every transient effect
 * and all debris. The host's renderer calls {@link #render}.
 */
public final class FxManager {
    private FxManager() {}

    private static FxHostEntity host;
    private static final List<TransientFx> EFFECTS = new ArrayList<>();
    private static final List<TransientFx> PENDING = new ArrayList<>();
    public static final DebrisSystem DEBRIS = new DebrisSystem();
    private static int nextId = -7_000_000;

    public static void add(TransientFx fx) { PENDING.add(fx); }

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null) {
            host = null;
            EFFECTS.clear(); PENDING.clear(); DEBRIS.clear();
            return;
        }
        if (host == null || host.getWorld() != world || host.isRemoved()) {
            host = new FxHostEntity(ModEntities.FX_HOST, world);
            host.setId(nextId--);
            Vec3d cam = mc.gameRenderer.getCamera().getPos();
            host.setPosition(cam);
            world.addEntity(host);
        }
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        host.setPosition(cam);
        EFFECTS.addAll(PENDING);
        PENDING.clear();
        EFFECTS.removeIf(fx -> !fx.tick());
        DEBRIS.tick(world);
    }

    public static void render(FxHostEntity e, float td, MatrixStack ms, VertexConsumerProvider vcp, boolean shadowPass) {
        Vec3d origin = new Vec3d(MathHelper.lerp(td, e.lastRenderX, e.getX()),
                MathHelper.lerp(td, e.lastRenderY, e.getY()),
                MathHelper.lerp(td, e.lastRenderZ, e.getZ()));
        FxContext ctx = new FxContext(ms, vcp, origin, MinecraftClient.getInstance().gameRenderer.getCamera().getPos(), td);
        DEBRIS.render(ctx);                      // real blocks: they may cast shadows
        if (shadowPass) return;                  // glowing energy must not
        DEBRIS.renderGlints(ctx);
        for (TransientFx fx : EFFECTS) fx.render(ctx);
    }

    public static int effectCount() { return EFFECTS.size(); }
}
