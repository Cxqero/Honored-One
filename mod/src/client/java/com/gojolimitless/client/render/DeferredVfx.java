package com.gojolimitless.client.render;

import com.gojolimitless.GojoLimitless;
import com.gojolimitless.config.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Energy effects under a shaderpack. Packs re-shade whatever the world draws: Bliss alpha-tests and re-lights glow
 * layers as surfaces, others fog them, force blend modes or drop them from their deferred buffers, and every pack does
 * it differently. So while a pack is active our effects are collected here during the world pass and drawn once the
 * pack has finished its frame (right after WorldRenderer.render), with vanilla shaders, depth-tested against the
 * scene. They look exactly as they do without shaders on every pack, and the pack still shades terrain, entities and
 * your skin. The shadow pass never reaches here (the renderers return early in it).
 */
public final class DeferredVfx implements VertexConsumerProvider {
    private DeferredVfx() {}

    private static final DeferredVfx INSTANCE = new DeferredVfx();
    private static final Map<RenderLayer, BufferAllocator> ALLOCATORS = new HashMap<>();
    private static final LinkedHashMap<RenderLayer, BufferBuilder> BUILDING = new LinkedHashMap<>();
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static boolean broken;

    /** The provider an effect renderer should draw into this frame. */
    public static VertexConsumerProvider route(VertexConsumerProvider vcp) {
        if (broken || !ConfigManager.get().client.effectsAfterShaderpack || !RenderPath.shaderPackActive()) return vcp;
        return INSTANCE;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        BufferBuilder b = BUILDING.get(layer);
        if (b == null) {
            BufferAllocator a = ALLOCATORS.computeIfAbsent(layer, l -> new BufferAllocator(Math.max(l.getExpectedBufferSize(), 1 << 16)));
            b = new BufferBuilder(a, layer.getDrawMode(), layer.getVertexFormat());
            BUILDING.put(layer, b);
        }
        return b;
    }

    /** WorldRenderer.render, HEAD: the camera's matrices for this frame. */
    public static void begin(Matrix4f view, Matrix4f proj) {
        VIEW.set(view);
        PROJ.set(proj);
        if (!BUILDING.isEmpty()) {
            // last frame's effects were never drawn: the flush hook isn't running (another mod changed the frame), so
            // fall back to drawing in the world pass rather than losing the effects
            GojoLimitless.LOG.warn("Deferred effects were not drawn last frame; drawing effects in the world pass instead");
            discard();
            broken = true;
        }
    }

    /** GameRenderer.renderWorld, right after WorldRenderer.render: the pack's frame is done. Draw the effects. */
    public static void flush() {
        if (BUILDING.isEmpty()) return;
        Matrix4f prevProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter prevSort = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(PROJ, VertexSorter.BY_DISTANCE);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(VIEW);
        RenderSystem.applyModelViewMatrix();
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
        for (Map.Entry<RenderLayer, BufferBuilder> e : BUILDING.entrySet()) {
            BuiltBuffer bb = e.getValue().endNullable();
            if (bb == null) continue;
            if (e.getKey().isTranslucent()) bb.sortQuads(ALLOCATORS.get(e.getKey()), RenderSystem.getVertexSorting());
            e.getKey().draw(bb);
        }
        BUILDING.clear();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(prevProj, prevSort);
    }

    private static void discard() {
        for (BufferBuilder b : BUILDING.values()) {
            BuiltBuffer bb = b.endNullable();
            if (bb != null) bb.close();
        }
        BUILDING.clear();
    }

    /** Leaving a world: free what was collected (the allocators are kept for the next one). */
    public static void clear() { discard(); }
}
