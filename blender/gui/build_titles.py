"""Technique title cards (1024x256 RGBA): big kanji + Japanese name + English name, with a real glow.
Run: blender -b --factory-startup -P blender/gui/build_titles.py"""
import bpy, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "lib"))
import vfxlib

OUT = os.path.join(ROOT, "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "textures", "gui")
os.makedirs(OUT, exist_ok=True)
F_JP = os.path.join(ROOT, "fonts", "NotoSerifJP-Bold-subset.otf")
F_EN = os.path.join(ROOT, "fonts", "Poppins-Medium.ttf")

TITLES = {
    # name: (kanji, jp line, en line, glow rgb)
    "blue": ("蒼", "術式順転", "LAPSE: BLUE", (0.25, 0.6, 1.0)),
    "blue_max": ("蒼", "出力最大", "MAXIMUM OUTPUT: BLUE", (0.25, 0.6, 1.0)),
    "red": ("赫", "術式反転", "REVERSAL: RED", (1.0, 0.18, 0.12)),
    "red_incant": ("赫", "位相 波羅蜜 光の柱", "PHASE, PARAMITA, PILLARS OF LIGHT", (1.0, 0.18, 0.12)),
    "purple": ("茈", "虚式", "HOLLOW TECHNIQUE: PURPLE", (0.7, 0.25, 1.0)),
    "purple_200": ("茈", "九綱 偏光 烏と声明 表裏の間", "HOLLOW PURPLE  200%", (0.7, 0.25, 1.0)),
    "domain": ("無量空処", "領域展開", "DOMAIN EXPANSION: UNLIMITED VOID", (0.55, 0.75, 1.0)),
    # incantation words, shown one at a time while charging
    "red_1": ("位相", "", "PHASE", (1.0, 0.18, 0.12)),
    "red_2": ("波羅蜜", "", "PARAMITA", (1.0, 0.18, 0.12)),
    "red_3": ("光の柱", "", "PILLARS OF LIGHT", (1.0, 0.18, 0.12)),
    "blue_1": ("位相", "", "PHASE", (0.25, 0.6, 1.0)),
    "blue_2": ("黄昏", "", "TWILIGHT", (0.25, 0.6, 1.0)),
    "blue_3": ("智慧の瞳", "", "EYES OF WISDOM", (0.25, 0.6, 1.0)),
    "purple_1": ("九綱", "", "NINE ROPES", (0.7, 0.25, 1.0)),
    "purple_2": ("偏光", "", "POLARIZED LIGHT", (0.7, 0.25, 1.0)),
    "purple_3": ("烏と声明", "", "CROW AND DECLARATION", (0.7, 0.25, 1.0)),
    "purple_4": ("表裏の間", "", "BETWEEN FRONT AND BACK", (0.7, 0.25, 1.0)),
}


def text_obj(sc, body, font, size, spacing=1.0):
    cu = bpy.data.curves.new("T", type="FONT")
    cu.body = body
    cu.font = bpy.data.fonts.load(font, check_existing=True)
    cu.size = size
    cu.space_character = spacing
    cu.align_x = "LEFT"
    cu.align_y = "CENTER"
    ob = bpy.data.objects.new("T", cu)
    sc.collection.objects.link(ob)
    mat = bpy.data.materials.new("Em")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes): nt.nodes.remove(n)
    em = nt.nodes.new("ShaderNodeEmission"); out = nt.nodes.new("ShaderNodeOutputMaterial")
    em.inputs["Strength"].default_value = 1.0
    nt.links.new(em.outputs[0], out.inputs[0])
    cu.materials.append(mat)
    return ob, em


def render_title(name, kanji, jp, en, glow):
    sc = vfxlib.reset()
    vfxlib.ortho_camera(sc, 4.0)
    sc.camera.data.ortho_scale = 8.0
    vfxlib.resolution(sc, 1024, 256, 24)
    big = 2.7 if len(kanji) == 1 else (1.55 if jp else 1.25)
    k, ke = text_obj(sc, kanji, F_JP, big)
    j, je = text_obj(sc, jp if jp else " ", F_JP, 0.44, 1.08)
    e, ee = text_obj(sc, en, F_EN, 0.34, 1.35)
    ke.inputs["Color"].default_value = (1.3, 1.3, 1.35, 1)
    je.inputs["Color"].default_value = (0.85, 0.88, 0.95, 1)
    ee.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1)
    # separator line
    bpy.ops.mesh.primitive_plane_add(size=1)
    line = bpy.context.active_object
    lm = bpy.data.materials.new("L"); lm.use_nodes = True
    lm.node_tree.nodes["Principled BSDF"].inputs["Emission Color"].default_value = (*glow, 1)
    lm.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = 3.0
    lm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0, 0, 0, 1)
    line.data.materials.append(lm)
    bpy.context.view_layer.update()
    wk = k.dimensions.x
    wb = max(j.dimensions.x, e.dimensions.x)
    gap = 0.22
    total = wk + gap + 0.02 + gap + wb
    fitk = min(1.0, 7.3 / total)          # auto-fit long names into the card
    if fitk < 1.0:
        for o in (k, j, e):
            o.scale = (fitk, fitk, fitk)
        bpy.context.view_layer.update()
        wk, wb, gap = k.dimensions.x, max(j.dimensions.x, e.dimensions.x), gap * fitk
        total = wk + gap + 0.02 + gap + wb
    x0 = -total / 2
    k.location = (x0, 0.02, 0)
    line.scale = (0.02, 1.55, 1)
    line.location = (x0 + wk + gap, 0, 0)
    j.location = (x0 + wk + 2 * gap + 0.02, 0.30, 0)
    e.location = (x0 + wk + 2 * gap + 0.02, -0.34 if jp else 0.0, 0)
    # glow: bloom the kanji in the technique's colour
    sc.use_nodes = True
    nt = sc.node_tree
    rl = nt.nodes.get("Render Layers"); comp = nt.nodes.get("Composite")
    g = nt.nodes.new("CompositorNodeGlare"); g.glare_type = "FOG_GLOW"; g.quality = "HIGH"; g.threshold = 0.9; g.size = 9; g.mix = 0.0
    mixn = nt.nodes.new("CompositorNodeMixRGB"); mixn.blend_type = "MULTIPLY"; mixn.inputs[0].default_value = 1.0
    mixn.inputs[2].default_value = (*[c * 1.6 for c in glow], 1)
    add = nt.nodes.new("CompositorNodeMixRGB"); add.blend_type = "ADD"; add.inputs[0].default_value = 1.0
    # glow = glare(img) - img, tinted, added back
    sub = nt.nodes.new("CompositorNodeMixRGB"); sub.blend_type = "SUBTRACT"; sub.inputs[0].default_value = 1.0
    nt.links.new(rl.outputs["Image"], g.inputs["Image"])
    nt.links.new(g.outputs["Image"], sub.inputs[1]); nt.links.new(rl.outputs["Image"], sub.inputs[2])
    nt.links.new(sub.outputs[0], mixn.inputs[1])
    nt.links.new(rl.outputs["Image"], add.inputs[1]); nt.links.new(mixn.outputs[0], add.inputs[2])
    nt.links.new(add.outputs[0], comp.inputs["Image"])
    tmp = os.path.join(OUT, "_tmp.png")
    vfxlib.render(sc, tmp)
    # black background → straight alpha
    img = bpy.data.images.load(tmp)
    px = np.array(img.pixels[:], dtype=np.float32).reshape(256, 1024, 4)
    rgb = np.clip(px[..., :3], 0, 1)
    a = np.clip(rgb.max(axis=2) * 1.15, 0, 1)
    col = np.where(a[..., None] > 1e-4, np.clip(rgb / np.maximum(a[..., None], 1e-4), 0, 1), 0)
    out = np.concatenate([col, a[..., None]], axis=2)
    dst = bpy.data.images.new(name, 1024, 256, alpha=True)
    dst.pixels[:] = out.ravel()
    dst.filepath_raw = os.path.join(OUT, f"title_{name}.png")
    dst.file_format = "PNG"
    dst.save()
    os.remove(tmp)
    with open(dst.filepath_raw + ".mcmeta", "w") as f:
        f.write('{"texture": {"blur": true, "clamp": true}}')
    print("[titles] wrote", dst.filepath_raw)


ONLY = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None
for name, (kanji, jp, en, glow) in TITLES.items():
    if ONLY is None or name in ONLY:
        render_title(name, kanji, jp, en, glow)
