"""Renders the shared + Lapse: Blue VFX textures with Cycles (OSL) and the compositor.
Run:  blender -b --factory-startup -P blender/assets/build_common_vfx.py -- [--out DIR]
"""
import bpy, os, sys, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib"))
import vfxlib
import numpy as np

A = vfxlib.args()
OUT = A["out"]
ONLY = None
if "--only" in sys.argv:
    ONLY = set(sys.argv[sys.argv.index("--only") + 1].split(","))


def mcmeta(name, clamp=True):
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"texture": {"blur": True, "clamp": clamp}}, f)


def simple(name, osl, w, h, params=None, samples=16, glare=None, aspect=None, clamp=True):
    if ONLY and name not in ONLY:
        return
    sc = vfxlib.reset()
    asp = aspect if aspect else w / h
    vfxlib.ortho_camera(sc, asp)
    if asp != 1.0:
        sc.camera.data.ortho_scale = 2.0 * asp
    vfxlib.resolution(sc, w, h, samples)
    p = dict(params or {})
    vfxlib.osl_plane(sc, osl, p, aspect=asp)
    if glare:
        for g in glare:
            vfxlib.glare(sc, **g)
    vfxlib.render(sc, os.path.join(OUT, name + ".png"))
    mcmeta(name, clamp)


def alpha_from_mask(name, osl, w, h, rgb, params=None, samples=16, alpha_scale=1.0):
    """Render a grayscale mask, then write RGBA with constant colour and mask as alpha."""
    if ONLY and name not in ONLY:
        return
    tmp = os.path.join(OUT, "_mask_tmp.png")
    sc = vfxlib.reset()
    vfxlib.ortho_camera(sc)
    vfxlib.resolution(sc, w, h, samples)
    vfxlib.osl_plane(sc, osl, params or {})
    vfxlib.render(sc, tmp)
    img = bpy.data.images.load(tmp)
    px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
    a = np.clip(px[..., 0] * alpha_scale, 0, 1)
    out = np.zeros_like(px)
    out[..., 0], out[..., 1], out[..., 2] = rgb
    out[..., 3] = a
    dst = bpy.data.images.new(name, w, h, alpha=True)
    dst.pixels[:] = out.ravel()
    dst.filepath_raw = os.path.join(OUT, name + ".png")
    dst.file_format = "PNG"
    dst.save()
    os.remove(tmp)
    mcmeta(name)
    print("[vfxlib] wrote", dst.filepath_raw)


# ---- shared ---------------------------------------------------------------
simple("spark", "spark.osl", 128, 128, samples=16,
       glare=[{"kind": "FOG_GLOW", "threshold": 0.9, "size": 7, "mix": 0.0}])
simple("ring", "ring.osl", 512, 512, samples=24,
       glare=[{"kind": "FOG_GLOW", "threshold": 1.0, "size": 7, "mix": 0.0}])
simple("flash_star", "point_src.osl", 512, 512, samples=24, params={"w": 0.01, "i": 400.0},
       glare=[{"kind": "STREAKS", "threshold": 0.2, "streaks": 4, "angle_offset": 0.0, "fade": 0.985, "mix": 0.0},
              {"kind": "FOG_GLOW", "threshold": 0.6, "size": 8, "mix": 0.0}])
simple("streak", "streak.osl", 256, 32, params={"aspect": 8.0}, samples=16, aspect=8.0)
simple("glow_soft", "blue_core.osl", 256, 256,
       params={"core_w": 0.25, "core_i": 1.0, "inner_w": 0.45, "inner_i": 0.0, "halo_w": 0.3, "halo_i": 0.0})
alpha_from_mask("dark_disk", "mask_disk.osl", 512, 512, (0.0, 0.0, 0.0),
                params={"ring_r": 0.0, "width": 0.55, "power": 2.2, "edge": 0.99})

# ---- Lapse: Blue ------------------------------------------------------------
simple("blue_core", "blue_core.osl", 512, 512, samples=32,
       glare=[{"kind": "FOG_GLOW", "threshold": 1.2, "size": 8, "mix": -0.3}])
simple("blue_vortex_a", "blue_vortex.osl", 1024, 1024, samples=24,
       params={"arms": 3.0, "twist": 2.6, "sharp": 3.0, "fil_freq": 5.0, "seed": 0.0, "gain": 1.1})
simple("blue_vortex_b", "blue_vortex.osl", 1024, 1024, samples=24,
       params={"arms": 7.0, "twist": 4.2, "sharp": 1.3, "fil_freq": 12.0, "seed": 11.0, "gain": 0.75, "inner_r": 0.16})
alpha_from_mask("blue_lens", "mask_disk.osl", 512, 512, (0.004, 0.018, 0.075),
                params={"ring_r": 0.0, "width": 0.42, "power": 2.6, "edge": 0.99}, alpha_scale=0.95)
simple("ribbon_energy", "ribbon_energy.osl", 1024, 128, params={"aspect": 8.0, "seg_freq": 9.0, "duty": 0.55},
       samples=16, aspect=8.0, clamp=False)
simple("ribbon_brush", "ribbon_brush.osl", 1024, 128, params={"aspect": 8.0, "hatch": 46.0, "slant": 0.6},
       samples=16, aspect=8.0, clamp=False)
