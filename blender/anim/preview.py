"""Render rig2 animations on the Minecraft character, with cameras matching the game's test shots.

Views: front (the game's third-person front camera, 4 blocks ahead at eye height looking back), back (third-person
back), fp (first person: from the eyes, 70° vertical FOV), side, and low (a heroic low angle).
"""
import bpy, os, sys, math
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "lib"))
import importlib
import mcchar as MC
importlib.reload(MC)
from mathutils import Vector

EYE = 1.62
# Same cameras as the game's test harness (AutoTest.camFor), converted from Minecraft space (player at the origin
# facing north) to Blender (character facing -y): blender = (-x, z, y). Vertical FOV 70 like the game's default.
VIEWS = {
    "front34": ((-1.5, -2.9, 1.5), (0, 0, 1.1), 70),
    "front": ((0, -3.3, 1.4), (0, 0, 1.1), 70),
    "side": ((-3.3, -0.2, 1.3), (0, 0, 1.1), 70),
    "back34": ((1.6, 2.7, 1.8), (0, 0, 1.1), 70),
    "low": ((-1.0, -2.4, 0.35), (0, 0, 1.3), 70),
    "face": ((-0.5, -1.3, 1.75), (0, 0, 1.55), 70),
    "fp": ((0, 0, EYE), (0, -5, EYE), 70),
    # closer review cameras
    "hero": ((-1.25, -1.85, 1.35), (0, 0, 1.15), 55),
    "side2": ((-2.2, -0.35, 1.25), (0, 0, 1.15), 55),
    "back2": ((1.1, 1.9, 1.55), (0, 0, 1.2), 55),
}


def _opts(argv):
    o = {"skin": None, "slim": False, "out": "/tmp/animprev", "size": 360, "views": "front,side,fp"}
    i = 0
    while i < len(argv):
        if argv[i] in ("--skin", "--out", "--size", "--views"):
            o[argv[i][2:]] = argv[i + 1]; i += 2
        elif argv[i] == "--slim":
            o["slim"] = True; i += 1
        else:
            i += 1
    return o


def run(anims, argv, frames=None):
    o = _opts(argv)
    os.makedirs(o["out"], exist_ok=True)
    size = int(o["size"])
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = 6; sc.cycles.use_denoising = False
    sc.cycles.max_bounces = 0; sc.cycles.transparent_max_bounces = 8
    sc.view_settings.view_transform = "Standard"
    sc.render.resolution_x = int(size * 16 / 9); sc.render.resolution_y = size
    sc.world = bpy.data.worlds.new("W"); sc.world.use_nodes = True
    sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.45, 0.6, 0.8, 1)
    # a ground plane so poses read against the floor
    bpy.ops.mesh.primitive_plane_add(size=40)
    gm = bpy.data.materials.new("Ground"); gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.25, 0.45, 0.2, 1)
    bpy.context.active_object.data.materials.append(gm)
    sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN")); sc.collection.objects.link(sun)
    sun.rotation_euler = (0.7, 0.2, 0.4)
    ch = MC.McChar(o["skin"], slim=o["slim"])
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); sc.collection.objects.link(cam); sc.camera = cam
    cam.data.sensor_fit = "VERTICAL"
    cam.data.clip_start = 0.02
    tgt = bpy.data.objects.new("T", None); sc.collection.objects.link(tgt)
    tr = cam.constraints.new("TRACK_TO"); tr.target = tgt; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"
    written = []
    for an in anims:
        fr = frames if frames is not None else [0, an.length // 4, an.length // 2, 3 * an.length // 4, an.length]
        for f in fr:
            for v in o["views"].split(","):
                ch.apply(an.sample(f, fp=(v == "fp")))
                loc, look, fov = VIEWS[v]
                cam.location = loc; tgt.location = look
                cam.data.angle = math.radians(fov)
                # first person: only the arms are drawn (playerAnimator's THIRD_PERSON_MODEL mode)
                for pn in ("head", "body", "right_leg", "left_leg"):
                    for ob in ch.meshes[pn]:
                        ob.hide_render = v == "fp"
                bpy.context.view_layer.update()
                path = os.path.join(o["out"], f"{an.name}_{f:03d}_{v}.png")
                sc.render.filepath = path
                bpy.ops.render.render(write_still=True)
                written.append(path)
        print("[preview]", an.name, flush=True)
    return written
