package com.gojolimitless.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Geometry helpers for energy effects. All positions are relative to the current matrix origin.
 * Colours are linear 0..1 floats; for additive layers alpha is ignored and RGB acts as intensity.
 */
public final class Vfx {
    private Vfx() {}

    public static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final Vector3f TMP = new Vector3f();

    public static Quaternionf cameraRotation() {
        return MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation();
    }

    public static Vec3d cameraPos() {
        return MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
    }

    public static void vert(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z,
                            float r, float g, float b, float a, float u, float v) {
        vc.vertex(e.getPositionMatrix(), x, y, z)
                .color(clamp(r), clamp(g), clamp(b), clamp(a))
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(e, 0f, 1f, 0f);
    }

    private static float clamp(float f) { return f < 0 ? 0 : (f > 1 ? 1 : f); }

    /** Camera-facing quad of half-size {@code size} centred at (cx,cy,cz), rotated {@code roll} radians around the view axis. */
    public static void billboard(VertexConsumer vc, MatrixStack.Entry e, float cx, float cy, float cz,
                                 float size, float roll, float r, float g, float b, float a) {
        billboard(vc, e, cx, cy, cz, size, size, roll, r, g, b, a, 0, 0, 1, 1);
    }

    public static void billboard(VertexConsumer vc, MatrixStack.Entry e, float cx, float cy, float cz,
                                 float sx, float sy, float roll, float r, float g, float b, float a,
                                 float u0, float v0, float u1, float v1) {
        Quaternionf q = cameraRotation();
        float c = MathHelper.cos(roll), s = MathHelper.sin(roll);
        float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0] * sx, y = corners[i][1] * sy;
            TMP.set(x * c - y * s, x * s + y * c, 0f);
            q.transform(TMP);
            vert(vc, e, cx + TMP.x, cy + TMP.y, cz + TMP.z, r, g, b, a, uv[i][0], uv[i][1]);
        }
    }

    /** Camera-facing flipbook frame (grid x grid atlas, frame 0 top-left). */
    public static void billboardFrame(VertexConsumer vc, MatrixStack.Entry e, float cx, float cy, float cz, float size, float roll,
                                      float r, float g, float b, float a, int frame, int grid, int light) {
        int fx = frame % grid, fy = frame / grid;
        float s = 1f / grid;
        float u0 = fx * s, v0 = fy * s;
        Quaternionf q = cameraRotation();
        float c = MathHelper.cos(roll), sn = MathHelper.sin(roll);
        float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uv = {{u0 + s, v0 + s}, {u0 + s, v0}, {u0, v0}, {u0, v0 + s}};
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0] * size, y = corners[i][1] * size;
            TMP.set(x * c - y * sn, x * sn + y * c, 0f);
            q.transform(TMP);
            vc.vertex(e.getPositionMatrix(), cx + TMP.x, cy + TMP.y, cz + TMP.z)
                    .color(r, g, b, a).texture(uv[i][0], uv[i][1]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0f, 1f, 0f);
        }
    }

    /** Flat quad lying in the plane perpendicular to {@code normal} (for rings/shockwaves). */
    public static void planeQuad(VertexConsumer vc, MatrixStack.Entry e, float cx, float cy, float cz,
                                 Vector3f normal, float size, float roll, float r, float g, float b, float a) {
        Vector3f n = new Vector3f(normal).normalize();
        Vector3f t = Math.abs(n.y) < 0.99f ? new Vector3f(0, 1, 0).cross(n).normalize() : new Vector3f(1, 0, 0).cross(n).normalize();
        Vector3f bt = new Vector3f(n).cross(t).normalize();
        float c = MathHelper.cos(roll), s = MathHelper.sin(roll);
        Vector3f tt = new Vector3f(t).mul(c).add(new Vector3f(bt).mul(s));
        Vector3f bb = new Vector3f(bt).mul(c).sub(new Vector3f(t).mul(s));
        float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0] * size, y = corners[i][1] * size;
            vert(vc, e, cx + tt.x * x + bb.x * y, cy + tt.y * x + bb.y * y, cz + tt.z * x + bb.z * y, r, g, b, a, uv[i][0], uv[i][1]);
        }
    }

    /**
     * Camera-facing ribbon through {@code pts} (xyz triples). Width and colour per point.
     * U runs along the ribbon ({@code u0 + i * uStep}), V across.
     */
    public static void ribbon(VertexConsumer vc, MatrixStack.Entry e, Vec3d camRel, float[] pts, int n,
                              float[] width, float[] rgba, float u0, float uStep) {
        if (n < 2) return;
        Vector3f side = new Vector3f(), prevL = new Vector3f(), prevR = new Vector3f();
        Vector3f tangent = new Vector3f(), toCam = new Vector3f();
        float prevU = 0; float[] prevC = new float[4];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            tangent.set(pts[b * 3] - pts[a * 3], pts[b * 3 + 1] - pts[a * 3 + 1], pts[b * 3 + 2] - pts[a * 3 + 2]);
            toCam.set((float) camRel.x - pts[i * 3], (float) camRel.y - pts[i * 3 + 1], (float) camRel.z - pts[i * 3 + 2]);
            tangent.cross(toCam, side);
            if (side.lengthSquared() < 1e-10f) side.set(0, 1, 0);
            side.normalize(width[i]);
            float px = pts[i * 3], py = pts[i * 3 + 1], pz = pts[i * 3 + 2];
            Vector3f L = new Vector3f(px + side.x, py + side.y, pz + side.z);
            Vector3f R = new Vector3f(px - side.x, py - side.y, pz - side.z);
            float u = u0 + i * uStep;
            if (i > 0) {
                vert(vc, e, prevL.x, prevL.y, prevL.z, prevC[0], prevC[1], prevC[2], prevC[3], prevU, 0);
                vert(vc, e, L.x, L.y, L.z, rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], rgba[i * 4 + 3], u, 0);
                vert(vc, e, R.x, R.y, R.z, rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], rgba[i * 4 + 3], u, 1);
                vert(vc, e, prevR.x, prevR.y, prevR.z, prevC[0], prevC[1], prevC[2], prevC[3], prevU, 1);
            }
            prevL.set(L); prevR.set(R); prevU = u;
            System.arraycopy(rgba, i * 4, prevC, 0, 4);
        }
    }

    /** Camera-facing streak from a (tail) to b (head), textured with u 0→1 from tail to head. */
    public static void streak(VertexConsumer vc, MatrixStack.Entry e, Vec3d camRel,
                              float ax, float ay, float az, float bx, float by, float bz, float width,
                              float r, float g, float b, float a) {
        Vector3f t = new Vector3f(bx - ax, by - ay, bz - az);
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        Vector3f toCam = new Vector3f((float) camRel.x - mx, (float) camRel.y - my, (float) camRel.z - mz);
        Vector3f side = t.cross(toCam, new Vector3f());
        if (side.lengthSquared() < 1e-10f) return;
        side.normalize(width);
        vert(vc, e, ax + side.x, ay + side.y, az + side.z, r, g, b, a, 0, 0);
        vert(vc, e, bx + side.x, by + side.y, bz + side.z, r, g, b, a, 1, 0);
        vert(vc, e, bx - side.x, by - side.y, bz - side.z, r, g, b, a, 1, 1);
        vert(vc, e, ax - side.x, ay - side.y, az - side.z, r, g, b, a, 0, 1);
    }

    /**
     * UV sphere with rim lighting computed per vertex on the CPU (fresnel), so it works with any shader.
     * intensity = base + rim * (1 - |n·v|)^power
     */
    public static void fresnelSphere(VertexConsumer vc, MatrixStack.Entry e, Vec3d camRel, float radius,
                                     int lat, int lon, float r, float g, float b, float base, float rim, float power) {
        float[][] P = new float[(lat + 1) * (lon + 1)][];
        float[] I = new float[(lat + 1) * (lon + 1)];
        for (int i = 0; i <= lat; i++) {
            float th = (float) Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                float ph = (float) (2 * Math.PI) * j / lon;
                float nx = MathHelper.sin(th) * MathHelper.cos(ph), ny = MathHelper.cos(th), nz = MathHelper.sin(th) * MathHelper.sin(ph);
                float px = nx * radius, py = ny * radius, pz = nz * radius;
                float vx = (float) camRel.x - px, vy = (float) camRel.y - py, vz = (float) camRel.z - pz;
                float vl = MathHelper.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6f;
                float ndv = Math.abs((nx * vx + ny * vy + nz * vz) / vl);
                int k = i * (lon + 1) + j;
                P[k] = new float[]{px, py, pz};
                I[k] = base + rim * (float) Math.pow(1f - ndv, power);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int k0 = i * (lon + 1) + j, k1 = k0 + 1, k2 = k0 + lon + 2, k3 = k0 + lon + 1;
                int[] ks = {k0, k1, k2, k3};
                for (int k : ks) {
                    float in = I[k];
                    vert(vc, e, P[k][0], P[k][1], P[k][2], r * in, g * in, b * in, 1f, 0.5f, 0.5f);
                }
            }
        }
    }

    /** Deterministic hash → [0,1). */
    public static float hash(int n) {
        n = (n << 13) ^ n;
        int m = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
        return m / 2147483648f;
    }

    public static float smooth(float e0, float e1, float x) {
        float t = MathHelper.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /**
     * A camera-facing smoke card is cut in a hard straight line where it passes into the ground (seen plainly under
     * shaderpacks). The smoke_puff body is opaque out to ~0.6 of the card's half-size and gone by ~0.78, so a card
     * whose centre is at least 0.78 half-sizes above the ground is cut only where it is already clear: the puff rests
     * on the ground. Takes and returns the card's y relative to the effect origin; wx/wz are its world position.
     */
    public static float restOnGround(float localY, float size, double originY, double wx, double wz) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return localY;
        int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(wx), MathHelper.floor(wz));
        return Math.max(localY, (float) (top - originY) + 0.78f * size);
    }
}
