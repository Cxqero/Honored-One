package com.gojolimitless.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Procedural lightning: midpoint-displaced bolts drawn as a soft glow ribbon plus a hot core ribbon. */
public final class Lightning {
    private Lightning() {}

    /** Points of a bolt from a to b (xyz triples, relative coords), with branches ignored. */
    public static float[] bolt(float ax, float ay, float az, float bx, float by, float bz, long seed, int depth, float jag) {
        int n = (1 << depth) + 1;
        float[] p = new float[n * 3];
        p[0] = ax; p[1] = ay; p[2] = az;
        p[(n - 1) * 3] = bx; p[(n - 1) * 3 + 1] = by; p[(n - 1) * 3 + 2] = bz;
        Random r = Random.create(seed);
        float len = MathHelper.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        for (int step = n - 1; step > 1; step /= 2) {
            float disp = len * jag * step / (n - 1);
            for (int i = step / 2; i < n; i += step) {
                int i0 = i - step / 2, i1 = i + step / 2;
                for (int c = 0; c < 3; c++) {
                    p[i * 3 + c] = (p[i0 * 3 + c] + p[i1 * 3 + c]) * 0.5f + (r.nextFloat() - 0.5f) * disp;
                }
            }
        }
        return p;
    }

    /** Draw bolt points with a violet (or any) glow. */
    public static void draw(MatrixStack.Entry e, VertexConsumerProvider vcp, Vec3d camRel, float[] pts, float width,
                            float r, float g, float b, float intensity) {
        int n = pts.length / 3;
        float[] w = new float[n], wc = new float[n], col = new float[n * 4], core = new float[n * 4];
        for (int i = 0; i < n; i++) {
            float taper = 0.35f + 0.65f * MathHelper.sin((float) Math.PI * i / (n - 1));
            w[i] = width * 5f * taper;
            wc[i] = width * taper;
            col[i * 4] = r * intensity * 0.45f; col[i * 4 + 1] = g * intensity * 0.45f; col[i * 4 + 2] = b * intensity * 0.45f; col[i * 4 + 3] = 1;
            float ci = intensity * 1.2f;
            core[i * 4] = Math.min(1, (r * 0.4f + 0.6f) * ci); core[i * 4 + 1] = Math.min(1, (g * 0.4f + 0.6f) * ci);
            core[i * 4 + 2] = Math.min(1, (b * 0.4f + 0.6f) * ci); core[i * 4 + 3] = 1;
        }
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), e, camRel, pts, n, w, col, 0.5f, 0f);
        Vfx.ribbon(vcp.getBuffer(VfxLayers.additive("glow_soft")), e, camRel, pts, n, wc, core, 0.5f, 0f);
    }
}
