package com.gojolimitless.client.fx;

import com.gojolimitless.config.ConfigManager;
import com.gojolimitless.entity.BlueOrbEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** Client-only flying terrain: real block models torn out of the ground, pulled / blasted / erased. */
public final class DebrisSystem {
    public static final int PULL = 0, BLAST = 1, ERASE = 2;

    private static final class Piece {
        BlockState state;
        double x, y, z, px, py, pz, vx, vy, vz;
        float rot, prevRot, spin, scale, prevScale, baseScale;
        float ax, ay, az;
        int age, maxAge, mode, owner;
        double tx, ty, tz; // last known target
        boolean dead, orphaned;
    }

    private final List<Piece> pieces = new ArrayList<>();
    /** Erased blocks don't fly anywhere — they leave a brief violet glint where they were. [x, y, z, age] */
    private final List<float[]> glints = new ArrayList<>();
    private final Random rand = Random.create();

    public void clear() { pieces.clear(); glints.clear(); }

    public int size() { return pieces.size(); }

    public void spawn(ClientWorld world, int owner, int mode, double ox, double oy, double oz, int[] data) {
        int cap = ConfigManager.get().client.maxDebris;
        if (mode == ERASE) {
            for (int i = 0; i + 3 < data.length; i += 4) {
                if (glints.size() >= cap) return;
                glints.add(new float[]{data[i] + rand.nextFloat(), data[i + 1] + rand.nextFloat(), data[i + 2] + rand.nextFloat(), -rand.nextInt(4)});
            }
            return;
        }
        for (int i = 0; i + 3 < data.length; i += 4) {
            if (pieces.size() >= cap) return;
            BlockState st = net.minecraft.block.Block.getStateFromRawId(data[i + 3]);
            if (st.getRenderType() != BlockRenderType.MODEL) continue;
            Piece p = new Piece();
            p.state = st;
            p.x = p.px = data[i] + 0.5; p.y = p.py = data[i + 1] + 0.5; p.z = p.pz = data[i + 2] + 0.5;
            p.mode = mode; p.owner = owner;
            p.tx = ox; p.ty = oy; p.tz = oz;
            p.baseScale = 0.45f + rand.nextFloat() * 0.55f;
            p.scale = p.prevScale = p.baseScale;
            float ax = rand.nextFloat() - 0.5f, ay = rand.nextFloat() - 0.5f, az = rand.nextFloat() - 0.5f;
            float l = MathHelper.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
            p.ax = ax / l; p.ay = ay / l; p.az = az / l;
            p.spin = (rand.nextFloat() - 0.5f) * 0.9f;
            switch (mode) {
                case PULL -> {
                    // ripped upward first, then drawn in
                    p.vx = (rand.nextFloat() - 0.5f) * 0.25; p.vy = 0.15 + rand.nextFloat() * 0.35; p.vz = (rand.nextFloat() - 0.5f) * 0.25;
                    p.maxAge = 160;
                }
                case BLAST -> {
                    Vec3d d = new Vec3d(p.x - ox, p.y - oy, p.z - oz);
                    double len = d.length() + 1e-3;
                    double sp = 0.8 + rand.nextFloat() * 1.6;
                    p.vx = d.x / len * sp; p.vy = d.y / len * sp + 0.3 + rand.nextFloat() * 0.6; p.vz = d.z / len * sp;
                    p.maxAge = 70 + rand.nextInt(50);
                }
                default -> p.maxAge = 8;
            }
            pieces.add(p);
        }
    }

    public void tick(ClientWorld world) {
        for (float[] g : glints) g[3]++;
        glints.removeIf(g -> g[3] > 12);
        for (Piece p : pieces) {
            p.px = p.x; p.py = p.y; p.pz = p.z; p.prevRot = p.rot; p.prevScale = p.scale;
            p.age++;
            p.rot += p.spin;
            switch (p.mode) {
                case PULL -> tickPull(world, p);
                case BLAST -> {
                    p.vy -= 0.06; p.vx *= 0.985; p.vy *= 0.985; p.vz *= 0.985;
                    p.x += p.vx; p.y += p.vy; p.z += p.vz;
                    if (p.age > p.maxAge - 15) p.scale = p.baseScale * Math.max(0, (p.maxAge - p.age) / 15f);
                }
                default -> p.scale = p.baseScale * Math.max(0, 1f - p.age / (float) p.maxAge);
            }
            if (p.age >= p.maxAge || p.scale <= 0.01f) p.dead = true;
        }
        pieces.removeIf(p -> p.dead);
    }

    private void tickPull(ClientWorld world, Piece p) {
        Entity e = p.owner >= 0 ? world.getEntityById(p.owner) : null;
        float coreR = 1.5f, pullR = 12f;
        if (e instanceof BlueOrbEntity orb) {
            p.tx = orb.getX(); p.ty = orb.getY(); p.tz = orb.getZ();
            coreR = orb.getRadius(); pullR = orb.getPullRadius();
        } else if (p.owner >= 0 && !p.orphaned) {
            p.orphaned = true;
            p.maxAge = Math.min(p.maxAge, p.age + 10);   // orb gone: collapse quickly
        }
        double dx = p.tx - p.x, dy = p.ty - p.y, dz = p.tz - p.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4;
        double pullIn = MathHelper.clamp(p.age / 8.0, 0, 1);   // the rip-up moment before the pull takes over
        double accel = (0.12 + 0.9 * Math.pow(MathHelper.clamp(1 - d / (pullR * 1.6), 0, 1), 2)) * pullIn;
        // tangential swirl around the vertical axis
        double sx = -dz / d, sz = dx / d;
        p.vx = p.vx * 0.9 + dx / d * accel + sx * accel * 0.55;
        p.vy = p.vy * 0.9 + dy / d * accel;
        p.vz = p.vz * 0.9 + dz / d * accel + sz * accel * 0.55;
        p.x += p.vx; p.y += p.vy; p.z += p.vz;
        // spaghettified as it nears the core
        p.scale = p.baseScale * (float) MathHelper.clamp((d - coreR * 0.5) / (coreR * 2.0), 0.0, 1.0);
        p.spin *= 1.02f;
        if (d < coreR * 0.55) p.dead = true;
    }

    public void renderGlints(FxContext ctx) {
        if (glints.isEmpty()) return;
        var vc = ctx.consumers().getBuffer(com.gojolimitless.client.render.VfxLayers.additive("spark"));
        var e = ctx.matrices().peek();
        float G = com.gojolimitless.client.render.RenderPath.glowScale();
        for (float[] g : glints) {
            float k = (g[3] + ctx.tickDelta()) / 12f;
            if (k < 0) continue;
            float f = (1 - k) * (1 - k) * G;
            com.gojolimitless.client.render.Vfx.billboard(vc, e, (float) (g[0] - ctx.origin().x), (float) (g[1] - ctx.origin().y), (float) (g[2] - ctx.origin().z),
                    0.35f + 0.5f * k, 0, 0.75f * f, 0.35f * f, f, 1);
        }
    }

    public void render(FxContext ctx) {
        if (pieces.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockRenderManager brm = mc.getBlockRenderManager();
        ClientWorld world = mc.world;
        if (world == null) return;
        MatrixStack ms = ctx.matrices();
        float td = ctx.tickDelta();
        BlockPos.Mutable bp = new BlockPos.Mutable();
        for (Piece p : pieces) {
            double x = MathHelper.lerp(td, p.px, p.x), y = MathHelper.lerp(td, p.py, p.y), z = MathHelper.lerp(td, p.pz, p.z);
            float sc = MathHelper.lerp(td, p.prevScale, p.scale);
            if (sc <= 0.01f) continue;
            float rot = MathHelper.lerp(td, p.prevRot, p.rot);
            bp.set(x, y, z);
            // pieces flying through freshly carved space often sit inside unlit cells: floor the light so they never go black
            int bl = Math.max(world.getLightLevel(net.minecraft.world.LightType.BLOCK, bp), 3);
            int sl = Math.max(world.getLightLevel(net.minecraft.world.LightType.SKY, bp), Math.max(world.getLightLevel(net.minecraft.world.LightType.SKY, bp.up(3)), 11));
            int light = net.minecraft.client.render.LightmapTextureManager.pack(bl, sl);
            ms.push();
            ms.translate(x - ctx.origin().x, y - ctx.origin().y, z - ctx.origin().z);
            ms.multiply(new Quaternionf().rotateAxis(rot, p.ax, p.ay, p.az));
            ms.scale(sc, sc, sc);
            ms.translate(-0.5, -0.5, -0.5);
            brm.renderBlockAsEntity(p.state, ms, ctx.consumers(), light, OverlayTexture.DEFAULT_UV);
            ms.pop();
        }
    }
}
