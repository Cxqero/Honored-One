"""Hollow Purple textures: turbulent plasma sphere flipbook (seamless 32-frame loop by rotating a 3D noise field)
and the violet-white core. Run: blender -b --factory-startup -P blender/assets/build_purple_vfx.py"""
import bpy, os, sys, json, math
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib"))
import vfxlib

A = vfxlib.args()
OUT = A["out"]
FR, SZ, GRID = 32, 256, 6


def mcmeta(path):
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)


# ---------------------------------------------------------------- plasma flipbook
sc = vfxlib.reset()
cam = vfxlib.ortho_camera(sc)
cam.data.ortho_scale = 2.6
vfxlib.resolution(sc, SZ, SZ, 12)
bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=1.0)
sph = bpy.context.active_object
bpy.ops.object.shade_smooth()
mat = bpy.data.materials.new("Plasma"); mat.use_nodes = True
nt = mat.node_tree
for n in list(nt.nodes): nt.nodes.remove(n)
out = nt.nodes.new("ShaderNodeOutputMaterial")
em = nt.nodes.new("ShaderNodeEmission")
tc = nt.nodes.new("ShaderNodeTexCoord")
nz = nt.nodes.new("ShaderNodeTexNoise"); nz.noise_dimensions = "3D"
nz.inputs["Scale"].default_value = 2.4; nz.inputs["Detail"].default_value = 9.0
nz.inputs["Roughness"].default_value = 0.62; nz.inputs["Distortion"].default_value = 0.9
ridge = nt.nodes.new("ShaderNodeMath"); ridge.operation = "SUBTRACT"; ridge.inputs[1].default_value = 0.5
ab = nt.nodes.new("ShaderNodeMath"); ab.operation = "ABSOLUTE"
sharp = nt.nodes.new("ShaderNodeMath"); sharp.operation = "MULTIPLY"; sharp.inputs[1].default_value = 6.0; sharp.use_clamp = True
inv = nt.nodes.new("ShaderNodeMath"); inv.operation = "SUBTRACT"; inv.inputs[0].default_value = 1.0
pw = nt.nodes.new("ShaderNodeMath"); pw.operation = "POWER"; pw.inputs[1].default_value = 2.5     # thin bright filaments
lw = nt.nodes.new("ShaderNodeLayerWeight"); lw.inputs["Blend"].default_value = 0.35
rimp = nt.nodes.new("ShaderNodeMath"); rimp.operation = "POWER"; rimp.inputs[1].default_value = 1.6
add = nt.nodes.new("ShaderNodeMath"); add.operation = "ADD"
mulr = nt.nodes.new("ShaderNodeMath"); mulr.operation = "MULTIPLY"; mulr.inputs[1].default_value = 0.9
ramp = nt.nodes.new("ShaderNodeValToRGB")
cr = ramp.color_ramp
cr.elements[0].position = 0.0; cr.elements[0].color = (0.06, 0.0, 0.16, 1)
cr.elements[1].position = 1.0; cr.elements[1].color = (1.0, 0.9, 1.0, 1)
e1 = cr.elements.new(0.22); e1.color = (0.3, 0.03, 0.75, 1)
e2 = cr.elements.new(0.55); e2.color = (0.8, 0.22, 1.0, 1)
e3 = cr.elements.new(0.85); e3.color = (1.0, 0.7, 1.0, 1)
strength = nt.nodes.new("ShaderNodeMath"); strength.operation = "MULTIPLY"; strength.inputs[1].default_value = 1.15
nt.links.new(tc.outputs["Object"], nz.inputs["Vector"])
nt.links.new(nz.outputs["Fac"], ridge.inputs[0]); nt.links.new(ridge.outputs[0], ab.inputs[0])
nt.links.new(ab.outputs[0], sharp.inputs[0]); nt.links.new(sharp.outputs[0], inv.inputs[1]); nt.links.new(inv.outputs[0], pw.inputs[0])
nt.links.new(lw.outputs["Facing"], rimp.inputs[0])
nt.links.new(pw.outputs[0], add.inputs[0]); nt.links.new(rimp.outputs[0], mulr.inputs[0]); nt.links.new(mulr.outputs[0], add.inputs[1])
nt.links.new(add.outputs[0], ramp.inputs["Fac"])
nt.links.new(ramp.outputs["Color"], em.inputs["Color"])
nt.links.new(add.outputs[0], strength.inputs[0]); nt.links.new(strength.outputs[0], em.inputs["Strength"])
nt.links.new(em.outputs[0], out.inputs["Surface"])
sph.data.materials.append(mat)
vfxlib.glare(sc, "FOG_GLOW", threshold=1.0, size=7, mix=-0.4)

frames_dir = os.path.join(OUT, "_frames_plasma"); os.makedirs(frames_dir, exist_ok=True)
for f in range(FR):
    k = f / FR
    sph.rotation_euler = (math.radians(20), 0, 2 * math.pi * k)           # full turn = seamless loop
    nz.inputs["Distortion"].default_value = 0.9
    p = os.path.join(frames_dir, f"{f:03d}.png")
    sc.render.filepath = p
    sc.render.image_settings.file_format = "PNG"; sc.render.image_settings.color_mode = "RGB"
    bpy.ops.render.render(write_still=True)
    print(f"[purple] frame {f + 1}/{FR}", flush=True)

atlas = np.zeros((GRID * SZ, GRID * SZ, 4), dtype=np.float32); atlas[..., 3] = 1
for i in range(FR):
    img = bpy.data.images.load(os.path.join(frames_dir, f"{i:03d}.png"))
    px = np.array(img.pixels[:], dtype=np.float32).reshape(SZ, SZ, 4)
    gx, gy = i % GRID, i // GRID
    y0 = (GRID - 1 - gy) * SZ
    atlas[y0:y0 + SZ, gx * SZ:(gx + 1) * SZ] = px
    bpy.data.images.remove(img)
d = bpy.data.images.new("purple_plasma", GRID * SZ, GRID * SZ, alpha=False)
d.pixels[:] = atlas.ravel()
d.filepath_raw = os.path.join(OUT, "purple_plasma.png"); d.file_format = "PNG"; d.save()
mcmeta(d.filepath_raw)
print("[purple] atlas", d.filepath_raw)

# ---------------------------------------------------------------- violet-white core
sc = vfxlib.reset(); vfxlib.ortho_camera(sc); vfxlib.resolution(sc, 512, 512, 32)
vfxlib.osl_plane(sc, "blue_core.osl", {"core_w": 0.05, "core_i": 6.0, "inner_w": 0.14, "inner_i": 1.4, "halo_w": 0.18, "halo_i": 0.5})
vfxlib.glare(sc, "FOG_GLOW", threshold=1.2, size=8, mix=-0.3)
p = os.path.join(OUT, "purple_core.png"); vfxlib.render(sc, p)
img = bpy.data.images.load(p)
px = np.array(img.pixels[:], dtype=np.float32).reshape(512, 512, 4)
L = px[..., :3].max(axis=2)
px[..., 0] = np.clip(L * 0.85 + np.clip(L - 0.6, 0, 1) * 0.5, 0, 1)
px[..., 1] = np.clip((L - 0.45) * 1.6, 0, 1) * 0.9 + L * 0.08
px[..., 2] = np.clip(L * 1.1, 0, 1)
img.pixels[:] = px.ravel(); img.filepath_raw = p; img.save()
mcmeta(p)
print("[purple] core", p)
