"""Volumetric smoke/dust puff flipbook (8x8 frames, 256 px each → 2048² RGBA atlas), rendered with Cycles volumes.
Grey-white smoke, lit from above; tinted in game (dust for Red, pale haze for Blue, violet for Purple).
Run: blender -b --factory-startup -P blender/assets/build_smoke.py -- [--frames 64] [--size 256]"""
import bpy, os, sys, math, json
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib"))
import vfxlib

A = vfxlib.args()
OUT = A["out"]
argv = sys.argv
FR = int(argv[argv.index("--frames") + 1]) if "--frames" in argv else 64
SZ = int(argv[argv.index("--size") + 1]) if "--size" in argv else 256
NAME = argv[argv.index("--name") + 1] if "--name" in argv else "smoke_puff"
STEP = int(argv[argv.index("--step") + 1]) if "--step" in argv else 1   # render every Nth frame of the FR-frame timeline
FRAMES = list(range(0, FR, STEP))
GRID = int(math.ceil(math.sqrt(len(FRAMES))))

sc = vfxlib.reset()
sc.cycles.max_bounces = 4
sc.cycles.volume_bounces = 1
sc.cycles.transparent_max_bounces = 8
sc.cycles.volume_step_rate = 1.0
sc.cycles.volume_max_steps = 256
sc.render.film_transparent = True
sc.view_settings.view_transform = "Standard"
vfxlib.resolution(sc, SZ, SZ, int(argv[argv.index("--samples") + 1]) if "--samples" in argv else 24)
sc.cycles.use_denoising = True
sc.cycles.denoiser = "OPENIMAGEDENOISE"

cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
sc.collection.objects.link(cam); sc.camera = cam
cam.data.type = "ORTHO"; cam.data.ortho_scale = 2.2
cam.location = (0, -6, 0); cam.rotation_euler = (math.pi / 2, 0, 0)

sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN"))
sc.collection.objects.link(sun)
sun.rotation_euler = (math.radians(35), math.radians(-25), 0)
sun.data.energy = 6.0
sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.45, 0.5, 0.6, 1)
sc.world.node_tree.nodes["Background"].inputs[1].default_value = 0.35

bpy.ops.mesh.primitive_cube_add(size=2.0)
dom = bpy.context.active_object
mat = bpy.data.materials.new("Smoke"); mat.use_nodes = True
nt = mat.node_tree
for n in list(nt.nodes): nt.nodes.remove(n)
out = nt.nodes.new("ShaderNodeOutputMaterial")
vol = nt.nodes.new("ShaderNodeVolumePrincipled")
vol.inputs["Color"].default_value = (0.82, 0.8, 0.78, 1)
vol.inputs["Anisotropy"].default_value = 0.1
tc = nt.nodes.new("ShaderNodeTexCoord")
noise = nt.nodes.new("ShaderNodeTexNoise"); noise.noise_dimensions = "4D"
noise.inputs["Scale"].default_value = 1.7; noise.inputs["Detail"].default_value = 8.0; noise.inputs["Roughness"].default_value = 0.62
fine = nt.nodes.new("ShaderNodeTexNoise"); fine.noise_dimensions = "4D"
fine.inputs["Scale"].default_value = 7.0; fine.inputs["Detail"].default_value = 4.0
sub = nt.nodes.new("ShaderNodeVectorMath"); sub.operation = "SUBTRACT"; sub.inputs[1].default_value = (0.5, 0.5, 0.5)
amp = nt.nodes.new("ShaderNodeVectorMath"); amp.operation = "SCALE"
add = nt.nodes.new("ShaderNodeVectorMath"); add.operation = "ADD"
ln = nt.nodes.new("ShaderNodeVectorMath"); ln.operation = "LENGTH"
mr = nt.nodes.new("ShaderNodeMapRange"); mr.clamp = True
mr.inputs["To Min"].default_value = 1.0; mr.inputs["To Max"].default_value = 0.0
mulfine = nt.nodes.new("ShaderNodeMath"); mulfine.operation = "MULTIPLY"
dens = nt.nodes.new("ShaderNodeMath"); dens.operation = "MULTIPLY"
nt.links.new(tc.outputs["Object"], noise.inputs["Vector"])
nt.links.new(tc.outputs["Object"], fine.inputs["Vector"])
nt.links.new(noise.outputs["Color"], sub.inputs[0])
nt.links.new(sub.outputs[0], amp.inputs[0])
nt.links.new(tc.outputs["Object"], add.inputs[0])
nt.links.new(amp.outputs[0], add.inputs[1])
nt.links.new(add.outputs[0], ln.inputs[0])
nt.links.new(ln.outputs["Value"], mr.inputs["Value"])
nt.links.new(mr.outputs[0], mulfine.inputs[0])
nt.links.new(fine.outputs["Fac"], mulfine.inputs[1])
nt.links.new(mulfine.outputs[0], dens.inputs[0])
nt.links.new(dens.outputs[0], vol.inputs["Density"])
nt.links.new(vol.outputs[0], out.inputs["Volume"])
dom.data.materials.append(mat)

frames_dir = os.path.join(OUT, "_frames_" + NAME)
os.makedirs(frames_dir, exist_ok=True)
for f in FRAMES:
    t = f / (FR - 1)
    R = 0.36 + 0.5 * (1 - (1 - t) ** 2.2)            # fast expansion that eases out
    noise.inputs["W"].default_value = t * 1.4
    fine.inputs["W"].default_value = 3.0 + t * 2.0
    amp.inputs["Scale"].default_value = 0.75 + 0.35 * t  # billowing lobes grow
    mr.inputs["From Min"].default_value = R - 0.12
    mr.inputs["From Max"].default_value = R
    dens.inputs[1].default_value = 26.0 * (1 - t) ** 1.5 + 0.4   # dense and sculpted early, thins out as it spreads
    p = os.path.join(frames_dir, f"{f:03d}.png")
    if not os.path.exists(p):
        sc.render.image_settings.file_format = "PNG"; sc.render.image_settings.color_mode = "RGBA"
        sc.render.filepath = p
        bpy.ops.render.render(write_still=True)
        print(f"[smoke] frame {f + 1}/{FR}", flush=True)
vfxlib_samples = sc.cycles.samples

# assemble atlas
atlas = np.zeros((GRID * SZ, GRID * SZ, 4), dtype=np.float32)
for i, f in enumerate(FRAMES):
    img = bpy.data.images.load(os.path.join(frames_dir, f"{f:03d}.png"))
    px = np.array(img.pixels[:], dtype=np.float32).reshape(SZ, SZ, 4)
    gx, gy = i % GRID, i // GRID
    # image rows are bottom-up in Blender; atlas row 0 must be the top row of the PNG
    y0 = (GRID - 1 - gy) * SZ
    atlas[y0:y0 + SZ, gx * SZ:(gx + 1) * SZ] = px
    bpy.data.images.remove(img)
d = bpy.data.images.new(NAME, GRID * SZ, GRID * SZ, alpha=True)
d.pixels[:] = atlas.ravel()
d.filepath_raw = os.path.join(OUT, NAME + ".png"); d.file_format = "PNG"; d.save()
with open(d.filepath_raw + ".mcmeta", "w") as fp:
    json.dump({"texture": {"blur": True, "clamp": True}}, fp)
with open(os.path.join(OUT, NAME + ".flipbook.json"), "w") as fp:
    json.dump({"frames": len(FRAMES), "grid": GRID}, fp)
print("[smoke] atlas", d.filepath_raw)
