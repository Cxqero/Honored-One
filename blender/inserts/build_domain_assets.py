"""Domain Expansion: Unlimited Void (無量空処) — textures and full-screen inserts (docs/storyboard/DOMAIN_STORYBOARD.md).

Textures (textures/vfx):
    void_blackhole   the black hole's ring, prismatic rim and accretion smoke (additive; the disk is dark_disk)
    void_smoke       blue-white smoke streaming off the ring to one side (additive)
    void_sky         the inside of the barrier: navy space, stars, nebula (equirectangular, opaque)
    nebula_frags     4x4 atlas of white torn "nebula fragments" for the hyperspace tunnel (additive)
    ink_burst        4x4 flipbook of black-violet ink erupting (RGBA)
Inserts (inserts/):
    domain_seal       1.6 s hand-seal close-up (the Taishakuten seal: index and middle fingers crossed), ends in white
    domain_seal_fast  0.4 s version for the 0.2-second domain
    domain_void_flash 0.1 s: the black hole, for the 0.2-second domain's single glimpse
    domain_ink_wipe   0.6 s white ink wipe (alpha) that closes the domain

Run: blender -b --factory-startup -P blender/inserts/build_domain_assets.py -- [--only a,b] [--preview --frames 0,10 --scale 50]
"""
import bpy, os, sys, math, random, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import importlib
import insertlib as IL
import handrig as HR
import vfxlib as VL
importlib.reload(IL); importlib.reload(HR); importlib.reload(VL)
from mathutils import Vector

O = IL.cli()
VFX = os.path.join(IL.ROOT, "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "textures", "vfx")
TMP = os.path.join(IL.ROOT, "inserts", "previews", "_tmp")
os.makedirs(TMP, exist_ok=True)


def want(name):
    return O["only"] is None or name in O["only"]


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def mcmeta(path):
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)


# ===================================================================================================== textures
def tex_2d(name, osl, params, w, h, aspect=1.0, samples=24, glare=None):
    sc = VL.reset()
    VL.ortho_camera(sc, aspect)
    pl, s = VL.osl_plane(sc, osl, params, aspect=aspect)
    VL.resolution(sc, w, h, samples)
    if glare:
        VL.glare(sc, **glare)
    out = os.path.join(VFX if not O["preview"] else TMP, name + ".png")
    VL.render(sc, out)
    if not O["preview"]:
        mcmeta(out)
    return out


def textures(pick=None):
    ok = lambda n: pick is None or n in pick
    if ok("void_blackhole"):
      tex_2d("void_blackhole", "blackhole.osl", dict(hole=0.44, outer=0.8, seed=4.0, gain=1.0), 1024, 1024, samples=32,
           glare=dict(kind="FOG_GLOW", threshold=1.2, size=7, mix=-0.65))
    if ok("void_smoke"):
        tex_2d("void_smoke", "void_smoke.osl", dict(seed=2.0), 1024, 512, aspect=2.0, samples=24)
    if ok("void_sky"):
        tex_2d("void_sky", "void_sky.osl", dict(seed=3.0), 2048, 1024, aspect=2.0, samples=16)
    if ok("nebula_frags"):
        tex_2d("nebula_frags", "nebula_frag.osl", {}, 1024, 1024, samples=16)


def ink_frame(prog, path, w=512, h=512, mode=0, cx=0.0, cy=0.0, aspect=1.0, color=(0.002, 0.001, 0.004), seed=1.0):
    """One RGBA frame of the ink mask."""
    sc = VL.reset()
    sc.render.film_transparent = True
    VL.ortho_camera(sc, aspect)
    bpy.ops.mesh.primitive_plane_add(size=2.0)
    pl = bpy.context.active_object
    pl.scale = (aspect, 1, 1)
    mat = bpy.data.materials.new("Ink"); mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes): nt.nodes.remove(n)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    s = IL.osl_node(nt, "ink.osl", dict(prog=prog, mode=mode, cx=cx, cy=cy, aspect=aspect, seed=seed))
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (*color, 1)
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mx = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(s.outputs["Fac"], mx.inputs["Fac"]); nt.links.new(tr.outputs[0], mx.inputs[1]); nt.links.new(em.outputs[0], mx.inputs[2])
    nt.links.new(mx.outputs[0], o.inputs["Surface"])
    pl.data.materials.append(mat)
    VL.resolution(sc, w, h, 12)
    VL.render(sc, path, color_mode="RGBA")


def ink_burst_atlas():
    """16-frame flipbook: ink erupting from the centre, jagged, flinging droplets, until it fills the frame."""
    from PIL import Image
    frames = []
    for i in range(16):
        p = os.path.join(TMP, f"ink_{i:02d}.png")
        prog = 0.46 * (i / 15.0) ** 0.8          # the front reaches the tile edge on the last frame
        ink_frame(prog, p, seed=5.0)
        frames.append(p)
    atlas = Image.new("RGBA", (2048, 2048), (0, 0, 0, 0))
    for i, p in enumerate(frames):
        atlas.paste(Image.open(p).convert("RGBA"), ((i % 4) * 512, (i // 4) * 512))
    out = os.path.join(VFX if not O["preview"] else TMP, "ink_burst.png")
    atlas.save(out, optimize=True)
    if not O["preview"]:
        mcmeta(out)
    print("[domain] wrote", out)


# ===================================================================================================== inserts
def seal(name="domain_seal", N=48, fast=False):
    """Hand-seal close-up. The hand rises into frame and the fingers cross; Six Eyes light rims it cyan; it ends
    in the flat white of the domain's first beat."""
    sc = IL.new_scene(1280, 720, 30, samples=O["samples"] or 20)
    cam, tgt = IL.camera(sc, lens=42)
    key = (0.55, -0.45, 0.72)
    H = HR.build_hand(skin_params=dict(Base=(0.84, 0.85, 0.9), Shade=(0.46, 0.47, 0.55), Deep=(0.22, 0.22, 0.29),
                                       KeyDir=key, KeyStr=0.95, Band1=0.3, Band2=-0.12),
                      nail_params=dict(KeyDir=key), sleeve_params=dict(KeyDir=key))
    arm = H["arm"]
    arm.rotation_euler = (math.radians(-6), math.radians(-8), math.radians(12))
    rise = 0 if fast else 16
    if fast:
        HR.set_pose(arm, "cross", frame=0)
        arm.location = (0, 0, 0); arm.keyframe_insert("location", frame=0)
    else:
        HR.set_pose(arm, "relaxed", frame=0)
        HR.set_pose(arm, "relaxed", frame=6)
        HR.set_pose(arm, "cross", frame=17)
        arm.location = (0.03, 0.02, -0.16); arm.keyframe_insert("location", frame=0)
        arm.location = (0.0, 0.0, 0.0); arm.keyframe_insert("location", frame=rise)
    HR.set_pose(arm, "cross", frame=N)
    look = Vector((0.01, 0.0, 0.13))
    if fast:
        IL.key_cam(cam, tgt, 0, (-0.08, -0.36, 0.16), look, lens=46)
        IL.key_cam(cam, tgt, N, (-0.07, -0.31, 0.155), look, lens=48)
    else:
        IL.key_cam(cam, tgt, 0, (-0.1, -0.42, 0.14), look, lens=42)
        IL.key_cam(cam, tgt, N, (-0.075, -0.33, 0.155), look, lens=46)
    bg_mat, bg, _ = IL.emission_material("Sky", "storm.osl", dict(
        aspect=1.7778, scale=0.9, cx=-0.25, cy=0.35, horizon=-1.5,
        sky_lo=(0.012, 0.012, 0.022), sky_hi=(0.05, 0.045, 0.075), cloud_lit=(0.2, 0.19, 0.26), cloud_dark=(0.03, 0.03, 0.05),
        tint=(0.35, 0.8, 1.0)))
    IL.plane("Sky", bg_mat, size=1.0, loc=(0.9, 3.4, 0.4), billboard_to=cam, sx=2.6, sy=1.46)
    white = (0.96, 0.93, 0.95)
    for f in range(0, N + 1):
        # the Six Eyes light: a cool point up by the (unseen) face, strengthening as the seal forms
        k = smooth(4 if not fast else 0, 20 if not fast else 4, f)
        HR.set_light(H["scripts"], frame=f, LightPos=(-0.06, -0.05, 0.3), LightCol=(0.35, 0.85, 1.0), LightStr=1.4 * k,
                     LightRange=0.12, RimCol=(0.45, 0.9, 1.0), RimStr=1.3 * k, KeyStr=0.95)
        IL.key_socket(bg.inputs["glow"], f, 0.25 * k)
        IL.key_socket(bg.inputs["flood"], f, 0.15 * k)
        IL.key_socket(bg.inputs["tm"], f, f * 0.05)
    # the world drains to white at the end: a white card in front of the lens, fading in over the last frames
    wm = bpy.data.materials.new("White"); wm.use_nodes = True
    nt = wm.node_tree
    for n_ in list(nt.nodes): nt.nodes.remove(n_)
    o_ = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (*white, 1)
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mx = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(tr.outputs[0], mx.inputs[1]); nt.links.new(em.outputs[0], mx.inputs[2]); nt.links.new(mx.outputs[0], o_.inputs["Surface"])
    card = IL.plane("WhiteCard", wm, size=0.06); card.parent = cam; card.location = (0, 0, -0.02)
    w0 = N - (4 if fast else 5)
    for f in range(0, N + 1):
        IL.key_socket(mx.inputs["Fac"], f, smooth(w0, N - 1, f))
    IL.cam_vignette(cam, strength=0.7)
    IL.compositor(sc, fog=(1.0, 7, 0.0), dispersion=0.01)
    IL.render_insert(sc, name, N, fps=30, preview=O["preview"], only_frames=O["frames"], scale=O["scale"])


def void_flash():
    """Three frames of the black hole — the only thing the 0.2-second domain lets anyone see."""
    from PIL import Image, ImageFilter
    N = 3
    out = os.path.join(IL.ASSETS if not O["preview"] else TMP, "domain_void_flash")
    os.makedirs(out, exist_ok=True)
    sky = os.path.join(TMP, "flash_sky.png")
    tex_path = lambda n: os.path.join(VFX, n + ".png")
    # compose in 2D from the textures (render them first if needed)
    for n in ("void_sky", "void_blackhole", "void_smoke"):
        if not os.path.exists(tex_path(n)):
            textures(); break
    W, H = 1280, 720
    base = Image.open(tex_path("void_sky")).convert("RGB").resize((W * 2, H * 2)).crop((W // 2, H // 2, W // 2 + W, H // 2 + H))
    bh = Image.open(tex_path("void_blackhole")).convert("RGB")
    sm = Image.open(tex_path("void_smoke")).convert("RGB")
    import numpy as np
    for f in range(N):
        img = np.asarray(base).astype(np.float32) / 255.0
        s = 620 + f * 14
        cx, cy = int(W * 0.34), int(H * 0.47)
        # smoke to the right
        smr = np.asarray(sm.resize((int(s * 1.9), int(s * 0.95)))).astype(np.float32) / 255.0
        x0, y0 = cx + int(s * 0.1), cy - smr.shape[0] // 2
        _add(img, smr, x0, y0, 0.9)
        # the hole: black disk under the additive ring
        yy, xx = np.mgrid[0:H, 0:W]
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / (s * 0.5)
        img *= np.clip((d - 0.255) / 0.02, 0, 1)[..., None]
        bhr = np.asarray(bh.resize((s, s))).astype(np.float32) / 255.0
        _add(img, bhr, cx - s // 2, cy - s // 2, 1.0)
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, f"{f:03d}.jpg"), quality=90)
    if not O["preview"]:
        with open(os.path.join(out, "meta.json"), "w") as fp:
            json.dump({"fps": 30, "frames": N, "ext": "jpg", "blend": "alpha"}, fp)
    print("[domain] wrote", out)


def _add(img, layer, x0, y0, gain):
    H, W, _ = img.shape
    h, w, _ = layer.shape
    xa, ya = max(0, x0), max(0, y0)
    xb, yb = min(W, x0 + w), min(H, y0 + h)
    if xa >= xb or ya >= yb:
        return
    img[ya:yb, xa:xb] += layer[ya - y0:yb - y0, xa - x0:xb - x0] * gain


def ink_wipe():
    """White ink splattering across the frame from the left (alpha insert over the game), ending fully white."""
    N = 18
    out = os.path.join(IL.ASSETS if not O["preview"] else TMP, "domain_ink_wipe")
    os.makedirs(out, exist_ok=True)
    todo = O["frames"] if O["frames"] is not None else range(N)
    for f in todo:
        prog = (f / (N - 1)) ** 0.9
        ink_frame(prog, os.path.join(out, f"{f:03d}.png"), w=960, h=540, mode=1, aspect=1.7778,
                  color=(0.97, 0.95, 0.97), seed=7.0)
    if not O["preview"]:
        with open(os.path.join(out, "meta.json"), "w") as fp:
            json.dump({"fps": 30, "frames": N, "ext": "png", "blend": "alpha"}, fp)


if want("textures"):
    textures()
elif O["only"] is not None and any(n in O["only"] for n in ("void_blackhole", "void_smoke", "void_sky", "nebula_frags")):
    textures(O["only"])
if want("ink_burst"):
    ink_burst_atlas()
if want("domain_void_flash"):
    void_flash()
if want("domain_ink_wipe"):
    ink_wipe()
if want("domain_seal"):
    seal()
if want("domain_seal_fast"):
    seal("domain_seal_fast", 12, fast=True)
