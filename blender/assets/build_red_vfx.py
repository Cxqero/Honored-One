"""Reversal: Red textures + shared smoke flipbook + HUD vignettes.
Run: blender -b --factory-startup -P blender/assets/build_red_vfx.py -- [--only a,b]"""
import bpy, os, sys, json, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib"))
import vfxlib
import numpy as np

A = vfxlib.args()
OUT = A["out"]
GUI = os.path.join(OUT, "..", "gui")
ONLY = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None


def want(n): return ONLY is None or n in ONLY


def mcmeta(path, clamp=True):
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": clamp}}, f)


def simple(name, osl, w, h, params=None, samples=16, glare=None, aspect=None):
    if not want(name): return
    sc = vfxlib.reset()
    asp = aspect or w / h
    vfxlib.ortho_camera(sc, asp)
    sc.camera.data.ortho_scale = 2.0 * max(1.0, asp)
    vfxlib.resolution(sc, w, h, samples)
    vfxlib.osl_plane(sc, osl, params or {}, aspect=asp)
    for g in glare or []:
        vfxlib.glare(sc, **g)
    p = os.path.join(OUT, name + ".png")
    vfxlib.render(sc, p)
    mcmeta(p)


simple("red_core", "blue_core.osl", 512, 512, samples=32,
       params={"core_w": 0.05, "core_i": 6.0, "inner_w": 0.13, "inner_i": 1.5, "halo_w": 0.17, "halo_i": 0.5},
       glare=[{"kind": "FOG_GLOW", "threshold": 1.2, "size": 8, "mix": -0.3}])
simple("red_swirl", "red_swirl.osl", 1024, 1024, samples=24, params={"twist": -1.8, "rays": 9.0, "seed": 3.0})
simple("red_swirl_b", "red_swirl.osl", 1024, 1024, samples=24, params={"twist": 2.4, "rays": 14.0, "seed": 17.0, "gain": 0.8})
simple("ray", "ray.osl", 1024, 64, params={"aspect": 16.0}, samples=16, aspect=16.0)


def recolor(src, dst, rgb_fn):
    """Recolour a greyscale-ish texture with numpy (keeps Blender's render, changes palette)."""
    img = bpy.data.images.load(os.path.join(OUT, src + ".png"))
    w, h = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
    out = rgb_fn(px)
    d = bpy.data.images.new(dst, w, h, alpha=True)
    d.pixels[:] = out.ravel()
    d.filepath_raw = os.path.join(OUT, dst + ".png"); d.file_format = "PNG"; d.save()
    mcmeta(d.filepath_raw)
    print("[red] wrote", d.filepath_raw)


if want("red_core"):
    # Blue's core shader gives a blue palette: remap luminance to white-hot → orange → crimson
    def to_red(px):
        L = px[..., :3].max(axis=2)
        r = np.clip(L * 1.0, 0, 1); g = np.clip((L - 0.35) * 1.4, 0, 1) * 0.85; b = np.clip((L - 0.6) * 2.2, 0, 1) * 0.8
        g = np.maximum(g, L * 0.12); b = np.maximum(b, L * 0.06)
        out = px.copy(); out[..., 0], out[..., 1], out[..., 2] = r, g, b; out[..., 3] = 1
        return out
    recolor("red_core", "red_core", to_red)


# ---- HUD vignettes (alpha) --------------------------------------------------
def vignette(name, rgb, strength):
    if not want(name): return
    sc = vfxlib.reset()
    vfxlib.ortho_camera(sc, 1.777)
    sc.camera.data.ortho_scale = 2.0 * 1.777
    vfxlib.resolution(sc, 1024, 576, 8)
    vfxlib.osl_plane(sc, "vignette.osl", {"aspect": 1.777}, aspect=1.777)
    tmp = os.path.join(GUI, "_v.png"); os.makedirs(GUI, exist_ok=True)
    vfxlib.render(sc, tmp)
    img = bpy.data.images.load(tmp)
    px = np.array(img.pixels[:], dtype=np.float32).reshape(576, 1024, 4)
    a = np.clip(px[..., 0] * strength, 0, 1)
    out = np.zeros_like(px); out[..., 0], out[..., 1], out[..., 2] = rgb; out[..., 3] = a
    d = bpy.data.images.new(name, 1024, 576, alpha=True); d.pixels[:] = out.ravel()
    d.filepath_raw = os.path.join(GUI, name + ".png"); d.file_format = "PNG"; d.save(); os.remove(tmp)
    mcmeta(d.filepath_raw)
    print("[red] wrote", d.filepath_raw)


vignette("vignette_red", (0.55, 0.0, 0.02), 0.95)
vignette("vignette_dark", (0.0, 0.0, 0.0), 1.0)
vignette("vignette_violet", (0.25, 0.0, 0.45), 0.95)
