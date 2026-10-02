package com.gojolimitless.client.render;

import com.gojolimitless.client.anim.CastAnimation;
import com.gojolimitless.client.anim.CastAnimator;
import com.gojolimitless.client.anim.HandShapes;
import com.gojolimitless.client.pose.PoseAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Blocky Minecraft-style fingers for the hand signs (Red's two raised fingers, the Taishakuten seal, Purple's
 * pinch...). They hang off the end of the arm, follow its rotation and bend, and take their colour from the skin's
 * own hand pixels. Drawn with the skin's entity render layer, so shaderpacks light them like the rest of the model.
 *
 * It extends the held-item feature only so playerAnimator keeps it in the first-person pass (which filters every
 * other feature out); it draws no items itself.
 */
public class FingerFeature extends PlayerHeldItemFeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final float[][] shape = new float[5][4];
    private static final boolean DEBUG = System.getenv("GOJO_AUTOTEST") != null;
    private static long lastLog;

    public FingerFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx,
                         HeldItemRenderer items) {
        super(ctx, items);
    }

    @Override
    public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity player, float limbAngle,
                       float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (player.isInvisible() || RenderPath.inShadowPass()) return;
        CastAnimation anim = CastAnimator.of(player);
        if (DEBUG && System.currentTimeMillis() - lastLog > 1000) {
            lastLog = System.currentTimeMillis();
            com.gojolimitless.GojoLimitless.LOG.info("[fingers] anim={} applied={} active={} right={} left={} shape={}", anim != null,
                    anim != null && anim.applied(), anim != null && anim.isActive(), anim != null ? anim.hand(true) : null,
                    anim != null ? anim.hand(false) : null, HandShapes.finger("index"));
        }
        if (anim == null || !anim.applied()) return;
        SkinTextures skin = player.getSkinTextures();
        boolean slim = skin.model() == SkinTextures.Model.SLIM;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(skin.texture()));
        PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            ModelPart arm = right ? model.rightArm : model.leftArm;
            if (!arm.visible) continue;
            PoseAnimation.HandSample h = anim.hand(right);
            if (h.w() <= 0.02f) continue;
            HandShapes.blend(h.a(), h.b(), h.k(), shape);
            boolean seal = "seal".equals(h.k() < 0.5f ? h.a() : h.b());
            ms.push();
            arm.rotate(ms);
            float armW = slim ? 3f : 4f;
            float cx = right ? -1f + (4f - armW) / 2f : 1f - (4f - armW) / 2f;
            float[] b = anim.armBend(right);
            if (Math.abs(b[1]) > 1e-4f) {
                ms.translate(cx / 16f, 4f / 16f, 0f);
                ms.multiply(new org.joml.Quaternionf().rotateAxis(b[1], (float) Math.cos(b[0]), 0f, (float) Math.sin(b[0])));
                ms.translate(-cx / 16f, -4f / 16f, 0f);
            }
            ms.translate(cx / 16f, 10f / 16f, 0f);           // the hand end: centre of the arm's bottom face
            float mir = right ? 1f : -1f;
            for (int f = 0; f < 5; f++) {
                String name = HandShapes.FINGERS[f];
                HandShapes.Finger fd = HandShapes.finger(name);
                if (fd == null) continue;
                float[] s = shape[f];
                ms.push();
                float bx = fd.bx() * armW / 4f;
                ms.translate(bx * mir / 16f, fd.by() / 16f, fd.bz() / 16f);
                if (seal && name.equals("middle")) ms.translate(0f, 0f, -HandShapes.sealCrossDepth() / 16f);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-fd.restPitch()));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-fd.restRoll() * mir));
                if (name.equals("thumb")) ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s[3] * mir));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-s[2]));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s[0] * mir));
                float sc = Math.min(1f, h.w());
                ms.scale(sc, sc, sc);
                int[] texel = texel(right, slim, fd, armW);
                float sx = fd.sx() * armW / 4f, sz = fd.sz();
                segment(vc, ms.peek(), sx, fd.len1(), sz, texel, light);
                ms.translate(0f, fd.len1() / 16f, 0f);
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s[1] * mir));
                segment(vc, ms.peek(), sx, fd.len2(), sz, texel, light);
                ms.pop();
            }
            ms.pop();
        }
    }

    /** The skin texel a finger takes its colour from: the palm-side face of the arm, bottom row. */
    private static int[] texel(boolean right, boolean slim, HandShapes.Finger fd, float armW) {
        int u0 = right ? 40 : 32, v0 = right ? 16 : 48;
        int w = (int) armW, d = 4, h = 12;
        // the inner (palm) side: EAST face for the right arm, WEST face for the left
        int fu = right ? u0 + d + w : u0;
        int col = Math.max(0, Math.min(d - 1, (int) Math.floor(fd.bz() + d / 2f)));
        if (!right) col = d - 1 - col;
        return new int[]{fu + col, v0 + d + h - 1};
    }

    /** A finger segment: a cuboid from y = 0 to len (pixels), centred on x/z, one texel for every face. */
    private static void segment(VertexConsumer vc, MatrixStack.Entry e, float sx, float len, float sz, int[] texel, int light) {
        float x0 = -sx / 32f, x1 = sx / 32f, y0 = 0f, y1 = len / 16f, z0 = -sz / 32f, z1 = sz / 32f;
        float u = (texel[0] + 0.5f) / 64f, v = (texel[1] + 0.5f) / 64f;
        Matrix4f m = e.getPositionMatrix();
        // -X, +X, -Y, +Y, -Z, +Z
        quad(vc, e, m, u, v, light, -1, 0, 0, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(vc, e, m, u, v, light, 1, 0, 0, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0);
        quad(vc, e, m, u, v, light, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        quad(vc, e, m, u, v, light, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(vc, e, m, u, v, light, 0, 0, -1, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0);
        quad(vc, e, m, u, v, light, 0, 0, 1, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, float u, float v, int light, float nx, float ny, float nz,
                             float... p) {
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(255, 255, 255, 255).texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        }
    }

    /** Arm-local pixel position of a fingertip in the current pose (for effects that sit on the finger). */
    public static Vector3f fingertipLocal(boolean right, boolean slim) {
        float armW = slim ? 3f : 4f;
        float cx = right ? -1f + (4f - armW) / 2f : 1f - (4f - armW) / 2f;
        return new Vector3f(cx, 13.2f, -1.0f);
    }
}
