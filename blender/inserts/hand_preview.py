"""Look-dev for the hand rig: renders a pose from a few angles.
Run: blender -b --factory-startup -P blender/inserts/hand_preview.py -- --pose sword [--size 480]
"""
import bpy, os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import importlib, handrig
importlib.reload(handrig)
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
pose = argv[argv.index("--pose") + 1] if "--pose" in argv else "sword"
size = int(argv[argv.index("--size") + 1]) if "--size" in argv else 480
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "previews")
os.makedirs(out, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.shading_system = True
sc.cycles.samples = 8; sc.cycles.max_bounces = 0; sc.cycles.transparent_max_bounces = 8
sc.view_settings.view_transform = "Standard"
sc.world = bpy.data.worlds.new("W"); sc.world.use_nodes = True
sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.32, 0.34, 0.4, 1)
sc.render.resolution_x = size; sc.render.resolution_y = size

H = handrig.build_hand()
handrig.set_pose(H["arm"], pose)
handrig.set_light(H["scripts"], LightPos=(0.02, -0.03, 0.2), LightStr=1.2, LightRange=0.05, RimStr=0.9)

cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); sc.collection.objects.link(cam); sc.camera = cam
cam.data.lens = 50
tgt = bpy.data.objects.new("T", None); sc.collection.objects.link(tgt); tgt.location = (0.005, 0, 0.08)
tr = cam.constraints.new("TRACK_TO"); tr.target = tgt; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"
views = {"palm": (0.05, -0.42, 0.12), "back": (-0.04, 0.42, 0.12), "side": (0.42, -0.08, 0.12), "low": (0.12, -0.3, -0.12)}
for n, loc in views.items():
    cam.location = loc
    sc.render.filepath = os.path.join(out, f"hand_{pose}_{n}.png")
    bpy.ops.render.render(write_still=True)
