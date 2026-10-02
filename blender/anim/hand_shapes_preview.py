"""Preview the blocky-finger hand shapes and the bend rig on a real skin.
    tools/blender -b -P blender/anim/hand_shapes_preview.py -- --skin <skin.png> [--slim] --out <dir>
"""
import bpy, os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import importlib
import mcchar as MC
importlib.reload(MC)
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
opt = {"skin": None, "slim": False, "out": "/tmp/handprev", "size": 420}
i = 0
while i < len(argv):
    if argv[i] in ("--skin", "--out", "--size"):
        opt[argv[i][2:]] = argv[i + 1]; i += 2
    elif argv[i] == "--slim":
        opt["slim"] = True; i += 1
    else:
        i += 1
os.makedirs(opt["out"], exist_ok=True)
size = int(opt["size"])

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = 8; sc.cycles.use_denoising = False
sc.cycles.max_bounces = 0; sc.cycles.transparent_max_bounces = 8
sc.view_settings.view_transform = "Standard"
sc.render.resolution_x = size; sc.render.resolution_y = size
sc.world = bpy.data.worlds.new("W"); sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.42, 0.55, 0.72, 1)

ch = MC.McChar(opt["skin"], slim=opt["slim"])
cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); sc.collection.objects.link(cam); sc.camera = cam
tgt = bpy.data.objects.new("T", None); sc.collection.objects.link(tgt)
tr = cam.constraints.new("TRACK_TO"); tr.target = tgt; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"


def shoot(name, cam_loc, look, lens=50):
    cam.location = cam_loc; tgt.location = look; cam.data.lens = lens
    bpy.context.view_layer.update()
    sc.render.filepath = os.path.join(opt["out"], name + ".png")
    bpy.ops.render.render(write_still=True)


def hand_world(side="right"):
    """World position of the hand end, to aim close-ups."""
    piv = ch.pivots[f"{side}_arm"]
    b = (0, 0)
    M = piv.matrix_world @ ch.hand_matrix(side, *b)
    return M.to_translation()


# 1) every hand shape, right arm raised in front of the face (like a sign), close-up from the front-right
shapes = ["relaxed", "fist", "open", "two_up", "point", "seal", "horns", "pinch", "grip"]
for s in shapes:
    pose = {
        "right_arm": {"rot": (math.radians(-150), math.radians(10), math.radians(15))},
        "head": {"rot": (math.radians(-5), math.radians(-12), 0)},
        "hands": {"right": (s, 1.0)},
    }
    ch.apply(pose)
    bpy.context.view_layer.update()
    h = hand_world("right")
    shoot(f"shape_{s}", h + Vector((-0.55, -0.9, 0.05)), h + Vector((0, 0, 0.02)), lens=85)
    print("[hands] shape", s, flush=True)

# 2) full-body test pose with bends: Red's sign near the face (elbow bent), the other hand in the pocket
pose = {
    "right_arm": {"rot": (math.radians(-60), math.radians(-25), math.radians(8)), "bend": (0.0, math.radians(-95))},
    "left_arm": {"rot": (math.radians(12), 0, math.radians(-8)), "bend": (0.0, math.radians(-35))},
    "body": {"rot": (math.radians(-4), math.radians(8), 0)},
    "head": {"rot": (math.radians(-6), math.radians(-10), 0)},
    "right_leg": {"rot": (math.radians(-4), 0, math.radians(3))},
    "left_leg": {"rot": (math.radians(6), 0, math.radians(-4)), "bend": (0.0, math.radians(12))},
    "hands": {"right": ("two_up", 1.0), "left": ("grip", 1.0)},
}
ch.apply(pose)
for nm, loc in (("body_front", (1.2, -3.2, 1.4)), ("body_side", (3.4, -0.6, 1.3)), ("body_low", (0.9, -2.4, 0.35))):
    shoot(nm, Vector(loc), Vector((0, 0, 1.1)), lens=50)
print("[hands] done", flush=True)
