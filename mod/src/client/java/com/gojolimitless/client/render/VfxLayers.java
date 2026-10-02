package com.gojolimitless.client.render;

import com.gojolimitless.GojoLimitless;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Render layers for energy effects. They only use vanilla shader programs, so an Iris shaderpack
 * maps them onto its own gbuffer programs instead of ignoring them (custom shaders are ignored by Iris).
 * <ul>
 *   <li>{@link #additive} — the "eyes" program (spider-eye glow): full-bright, additive. Packs bloom it.</li>
 *   <li>{@link #alpha} — the translucent-emissive entity program: full-bright, alpha-blended (dark layers, ink).</li>
 * </ul>
 */
public final class VfxLayers {
    private VfxLayers() {}

    public static Identifier tex(String name) {
        return Identifier.of(GojoLimitless.MOD_ID, "textures/vfx/" + name + ".png");
    }

    /** Energy doesn't fade into the distance fog: Blue far across the sky, the nuke seen from a wide shot. */
    private static float fogStart;
    private static final RenderPhase.Texturing NO_FOG = new RenderPhase.Texturing("gojolimitless_no_fog",
            () -> { fogStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart(); com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE); },
            () -> com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart));

    private static final Function<Identifier, RenderLayer> ADDITIVE = Util.memoize(t -> RenderLayer.of(
            "gojolimitless_additive",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.EYES_PROGRAM)
                    .texture(new RenderPhase.Texture(t, false, false))
                    .transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .texturing(NO_FOG)
                    .build(false)));

    private static final Function<Identifier, RenderLayer> ALPHA = Util.memoize(t -> RenderLayer.of(
            "gojolimitless_alpha",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                    .texture(new RenderPhase.Texture(t, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                    .texturing(NO_FOG)
                    .build(false)));

    /** Lit, alpha-blended (smoke, dust): receives world lighting so it isn't glowing at night. */
    private static final Function<Identifier, RenderLayer> LIT = Util.memoize(t -> RenderLayer.of(
            "gojolimitless_lit",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                    .texture(new RenderPhase.Texture(t, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)));

    /**
     * Opaque, full-bright, writes depth: the inside of a domain (the void's sky sphere). Uses the translucent-emissive
     * entity program with blending off, so shaderpacks still recognise it as an emissive entity surface.
     */
    private static final Function<Identifier, RenderLayer> SKY = Util.memoize(t -> RenderLayer.of(
            "gojolimitless_sky",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 4096, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                    .texture(new RenderPhase.Texture(t, true, false))
                    .transparency(RenderPhase.NO_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .writeMaskState(RenderPhase.ALL_MASK)
                    .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                    .texturing(NO_FOG)
                    .build(false)));

    public static RenderLayer additive(String texture) { return ADDITIVE.apply(tex(texture)); }

    public static RenderLayer additive(Identifier texture) { return ADDITIVE.apply(texture); }

    public static RenderLayer alpha(Identifier texture) { return ALPHA.apply(texture); }

    public static RenderLayer sky(Identifier texture) { return SKY.apply(texture); }

    public static RenderLayer lit(String texture) { return LIT.apply(tex(texture)); }

    public static RenderLayer alpha(String texture) { return ALPHA.apply(tex(texture)); }
}
