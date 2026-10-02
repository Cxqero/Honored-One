"""Minecraft player rig + casting animations, authored in Blender and exported for the mod.

Rig: one Empty per model part (head, body, right_arm, left_arm, right_leg, left_leg) placed at the part's pivot,
parented to a root that maps Minecraft *model space* (y down, -z forward, pixels/16) into Blender space.
Each part's Euler XYZ rotation therefore equals Minecraft's (pitch, yaw, roll) exactly — ModelPart applies
rotationZYX(roll, yaw, pitch), i.e. R = Rz·Ry·Rx, the same as Blender's XYZ mode.

Run:  blender -b --factory-startup -P blender/anim/player_rig.py -- [--preview]
Writes: mod/src/main/resources/assets/gojolimitless/animations/player/<name>.json (+ preview sheets)
"""
import bpy, os, sys, json, math

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "animations", "player")
PREVIEW = "--preview" in sys.argv
PREVIEW_DIR = os.path.join(HERE, "previews")
FPS = 60

# part: (pivot, box_from, box_size, colour)  — Minecraft model-space pixels, relative to pivot
PARTS = {
    "head":      ((0, 0, 0),     (-4, -8, -4), (8, 8, 8),  (0.85, 0.65, 0.5)),
    "body":      ((0, 0, 0),     (-4, 0, -2),  (8, 12, 4), (0.1, 0.12, 0.16)),
    "right_arm": ((-5, 2, 0),    (-3, -2, -2), (4, 12, 4), (0.1, 0.12, 0.16)),
    "left_arm":  ((5, 2, 0),     (-1, -2, -2), (4, 12, 4), (0.1, 0.12, 0.16)),
    "right_leg": ((-1.9, 12, 0), (-2, 0, -2),  (4, 12, 4), (0.06, 0.07, 0.1)),
    "left_leg":  ((1.9, 12, 0),  (-2, 0, -2),  (4, 12, 4), (0.06, 0.07, 0.1)),
}


def build_rig():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.fps = FPS
    root = bpy.data.objects.new("Root", None)
    sc.collection.objects.link(root)
    root.rotation_euler = (-math.pi / 2, 0, 0)     # model (x, y, z) -> blender (x, z, -y)
    root.scale = (1 / 16, 1 / 16, 1 / 16)
    root.location = (0, 0, 24 / 16)
    root["weight"] = 0.0
    parts = {}
    for name, (pivot, frm, size, col) in PARTS.items():
        piv = bpy.data.objects.new(name, None)
        sc.collection.objects.link(piv)
        piv.parent = root
        piv.location = pivot
        piv.rotation_mode = "XYZ"
        # visible box for previews
        bpy.ops.mesh.primitive_cube_add(size=1)
        box = bpy.context.active_object
        box.name = name + "_box"
        box.parent = piv
        box.scale = (size[0], size[1], size[2])
        box.location = (frm[0] + size[0] / 2, frm[1] + size[1] / 2, frm[2] + size[2] / 2)
        m = bpy.data.materials.new(name)
        m.diffuse_color = (*col, 1)
        box.data.materials.append(m)
        parts[name] = piv
    # a nose so previews show which way the head faces (model -z is forward)
    bpy.ops.mesh.primitive_cube_add(size=1)
    nose = bpy.context.active_object
    nose.parent = parts["head"]; nose.scale = (2, 2, 1); nose.location = (0, -4, -4.5)
    nose.data.materials.append(bpy.data.materials["body"])
    return sc, root, parts


def key(obj, frame, rot=None, weight=None, interp="BEZIER"):
    if rot is not None:
        obj.rotation_euler = rot
        obj.keyframe_insert("rotation_euler", frame=frame)
    if weight is not None:
        obj["weight"] = weight
        obj.keyframe_insert('["weight"]', frame=frame)


def ease_all(obj, kind="BEZIER"):
    ad = obj.animation_data
    if not ad or not ad.action:
        return
    for fc in ad.action.fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation = kind
            kp.easing = "AUTO"
            kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"


# ------------------------------------------------------------------ animations
# Angles in radians, Minecraft convention. "aim" bones are offset by the head's look direction at runtime,
# so pitch -pi/2 on an aim arm means "pointing exactly where the player looks".
def anim_blue_tap(root, P):
    """Lapse: Blue (tap): a sharp two-finger point at the target, a snap, then relax. 0.7 s."""
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=0.0); key(root, 5, weight=1.0); key(root, 30, weight=1.0); key(root, 42, weight=0.0)
    key(ra, 0, (-0.35, 0.0, 0.0))
    key(ra, 6, (-1.25, 0.05, 0.02))
    key(ra, 10, (-1.68, 0.0, 0.0))     # overshoot: the snap
    key(ra, 14, (-1.53, 0.0, 0.0))
    key(ra, 30, (-1.50, 0.0, 0.0))
    key(ra, 42, (-0.8, 0.0, 0.0))
    key(la, 0, (0.0, 0.0, 0.0)); key(la, 10, (0.25, 0.0, -0.12)); key(la, 42, (0.05, 0.0, 0.0))
    key(body, 0, (0.0, 0.0, 0.0)); key(body, 10, (0.0, 0.14, 0.0)); key(body, 30, (0.0, 0.1, 0.0)); key(body, 42, (0.0, 0.0, 0.0))
    return 42, ["right_arm"]


def anim_blue_throw(root, P):
    """Maximum Output: Blue released: the arm swings up and hurls it forward with the whole body. 0.75 s."""
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=0.0); key(root, 3, weight=1.0); key(root, 34, weight=1.0); key(root, 45, weight=0.0)
    key(ra, 0, (-1.6, 0.0, 0.0))
    key(ra, 7, (-2.75, -0.1, 0.15))    # wind-up above the head
    key(ra, 13, (-1.35, 0.1, -0.05))   # release
    key(ra, 18, (-0.95, 0.15, -0.05))  # follow-through
    key(ra, 34, (-1.05, 0.05, 0.0))
    key(ra, 45, (-0.5, 0.0, 0.0))
    key(la, 0, (0.0, 0.0, 0.0)); key(la, 7, (-0.6, 0.0, -0.35)); key(la, 13, (0.45, 0.0, -0.2)); key(la, 45, (0.0, 0.0, 0.0))
    key(body, 0, (0.0, 0.0, 0.0)); key(body, 7, (-0.12, -0.18, 0.0)); key(body, 13, (0.22, 0.22, 0.0))
    key(body, 20, (0.15, 0.12, 0.0)); key(body, 45, (0.0, 0.0, 0.0))
    return 45, ["right_arm"]


def anim_red_tap(root, P):
    """Reversal: Red (tap): fingertip raised to the target, a flick forward as it fires. 0.6 s."""
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=0.0); key(root, 3, weight=1.0); key(root, 24, weight=1.0); key(root, 36, weight=0.0)
    key(ra, 0, (-0.6, 0.0, 0.0))
    key(ra, 4, (-1.85, -0.05, 0.0))     # cocked slightly high
    key(ra, 8, (-1.45, 0.0, 0.0))       # flick
    key(ra, 12, (-1.55, 0.0, 0.0))
    key(ra, 24, (-1.5, 0.0, 0.0))
    key(ra, 36, (-0.7, 0.0, 0.0))
    key(body, 0, (0, 0, 0)); key(body, 8, (0.06, 0.12, 0)); key(body, 36, (0, 0, 0))
    key(la, 0, (0, 0, 0)); key(la, 8, (0.2, 0, -0.1)); key(la, 36, (0, 0, 0))
    return 36, ["right_arm"]


def anim_red_charge(root, P):
    """Incantation Red: the hand comes up beside the face, index finger raised, the orb at its tip. Holds the last frame."""
    ra, la, body, head = P["right_arm"], P["left_arm"], P["body"], P["head"]
    key(root, 0, weight=0.0); key(root, 8, weight=1.0); key(root, 30, weight=1.0)
    key(ra, 0, (-0.3, 0.0, 0.0))
    key(ra, 10, (-2.35, -0.55, 0.28))
    key(ra, 30, (-2.25, -0.5, 0.25))
    key(la, 0, (0, 0, 0)); key(la, 12, (0.12, 0.0, -0.14)); key(la, 30, (0.1, 0.0, -0.12))
    key(body, 0, (0, 0, 0)); key(body, 12, (0.0, -0.12, 0.0)); key(body, 30, (0.0, -0.1, 0.0))
    return 30, []


def anim_red_release(root, P):
    """Incantation Red released: the arm drives forward from beside the face, body following through. 0.7 s."""
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=1.0); key(root, 30, weight=1.0); key(root, 42, weight=0.0)
    key(ra, 0, (-2.2, -0.5, 0.25))          # aim-relative: starts high and inward, where the charge pose ended
    key(ra, 5, (-1.62, 0.02, 0.0))          # thrust
    key(ra, 9, (-1.5, 0.0, 0.0))
    key(ra, 30, (-1.45, 0.0, 0.0))
    key(ra, 42, (-0.6, 0.0, 0.0))
    key(body, 0, (0, -0.1, 0)); key(body, 5, (0.16, 0.18, 0)); key(body, 16, (0.08, 0.1, 0)); key(body, 42, (0, 0, 0))
    key(la, 0, (0.1, 0, -0.12)); key(la, 5, (0.45, 0, -0.2)); key(la, 42, (0, 0, 0))
    return 42, ["right_arm"]


def anim_purple_tap(root, P):
    """Hollow Purple: Blue in the left hand, Red in the right, arms open; the hands close and the infinities collide
    (tick 20 = frame 60); the mass swells; both arms drive it forward at launch (tick 34 = frame 102)."""
    ra, la, body, head = P["right_arm"], P["left_arm"], P["body"], P["head"]
    key(root, 0, weight=0.0); key(root, 6, weight=1.0); key(root, 118, weight=1.0); key(root, 132, weight=0.0)
    key(ra, 0, (-0.2, 0.0, 0.1));  key(la, 0, (-0.2, 0.0, -0.1))
    key(ra, 12, (-1.15, 0.0, 0.95)); key(la, 12, (-1.15, 0.0, -0.95))      # arms open, orbs at the hands
    key(ra, 40, (-1.25, 0.0, 0.85)); key(la, 40, (-1.25, 0.0, -0.85))
    key(ra, 58, (-1.45, -0.35, 0.12)); key(la, 58, (-1.45, 0.35, -0.12))   # hands meet in front
    key(ra, 98, (-1.5, -0.3, 0.1));  key(la, 98, (-1.5, 0.3, -0.1))
    key(ra, 104, (-1.62, -0.12, 0.0)); key(la, 104, (-1.62, 0.12, 0.0))    # thrust
    key(ra, 118, (-1.5, -0.1, 0.0)); key(la, 118, (-1.5, 0.1, 0.0))
    key(ra, 132, (-0.4, 0.0, 0.0)); key(la, 132, (-0.4, 0.0, 0.0))
    key(body, 0, (0, 0, 0)); key(body, 58, (-0.06, 0, 0)); key(body, 104, (0.18, 0, 0)); key(body, 132, (0, 0, 0))
    return 132, []


def _sign(root, P, ra_rot, la_rot, body_rot=(0, 0, 0)):
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=1.0); key(root, 18, weight=1.0)
    key(ra, 0, (-1.3, -0.4, 0.2)); key(la, 0, (-1.3, 0.4, -0.2)); key(body, 0, (0, 0, 0))
    key(ra, 12, ra_rot); key(la, 12, la_rot); key(body, 12, body_rot)
    key(ra, 18, ra_rot); key(la, 18, la_rot); key(body, 18, body_rot)
    return 18, []


# 200% incantation hand signs, one per word (held until the next word)
def anim_purple_sign_1(root, P):   # 九綱 Nine Ropes — right hand raised before the face, left at the chest
    return _sign(root, P, (-2.35, -0.45, 0.3), (-1.1, 0.35, -0.25))
def anim_purple_sign_2(root, P):   # 偏光 Polarized Light — both hands together at the sternum
    return _sign(root, P, (-1.35, -0.55, 0.15), (-1.35, 0.55, -0.15), (0.04, 0, 0))
def anim_purple_sign_3(root, P):   # 烏と声明 Crow and Declaration — forearms crossed high
    return _sign(root, P, (-1.9, -0.7, 0.25), (-1.9, 0.7, -0.25), (-0.05, 0, 0))
def anim_purple_sign_4(root, P):   # 表裏の間 Between Front and Back — arms open, palms out, gathering
    return _sign(root, P, (-1.0, 0.1, 1.15), (-1.0, -0.1, -1.15), (-0.08, 0, 0))


def anim_purple_200_release(root, P):
    """200% release: arms stay open while Blue and Red swing in (collide at tick 16 = frame 48), the mass swells,
    and both arms hurl it forward at launch (tick 44 = frame 132)."""
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=1.0); key(root, 150, weight=1.0); key(root, 168, weight=0.0)
    key(ra, 0, (-1.0, 0.1, 1.15)); key(la, 0, (-1.0, -0.1, -1.15))
    key(ra, 44, (-1.4, -0.3, 0.3)); key(la, 44, (-1.4, 0.3, -0.3))
    key(ra, 60, (-2.2, -0.2, 0.5)); key(la, 60, (-2.2, 0.2, -0.5))     # lifting the mass
    key(ra, 126, (-2.4, -0.2, 0.45)); key(la, 126, (-2.4, 0.2, -0.45))
    key(ra, 134, (-1.55, -0.1, 0.05)); key(la, 134, (-1.55, 0.1, -0.05)) # hurl
    key(ra, 150, (-1.4, 0.0, 0.0)); key(la, 150, (-1.4, 0.0, 0.0))
    key(ra, 168, (-0.4, 0, 0)); key(la, 168, (-0.4, 0, 0))
    key(body, 0, (-0.08, 0, 0)); key(body, 60, (-0.15, 0, 0)); key(body, 134, (0.22, 0, 0)); key(body, 168, (0, 0, 0))
    return 168, []


# ------------------------------------------------------------------ remote Hollow Purple ("the nuke")
def anim_nuke_red(root, P):
    """Red at the fingertip: the right hand comes up in front of the face, two fingers raised. Holds."""
    ra, la, body = P["right_arm"], P["left_arm"], P["body"]
    key(root, 0, weight=0.0); key(root, 10, weight=1.0); key(root, 36, weight=1.0)
    key(ra, 0, (-0.3, 0.0, 0.0))
    key(ra, 12, (-2.5, -0.32, 0.14))
    key(ra, 36, (-2.44, -0.3, 0.12))
    key(la, 0, (0, 0, 0)); key(la, 14, (0.18, 0.0, -0.12)); key(la, 36, (0.15, 0.0, -0.1))
    key(body, 0, (0, 0, 0)); key(body, 14, (0.0, -0.1, 0.0)); key(body, 36, (0.0, -0.08, 0.0))
    return 36, []


def anim_nuke_throw(root, P):
    """Red hurled into the sky: the arm snaps straight up, holds there while Red climbs, then settles. 1.9 s, holds."""
    ra, la, body, rl, ll = P["right_arm"], P["left_arm"], P["body"], P["right_leg"], P["left_leg"]
    key(root, 0, weight=1.0); key(root, 112, weight=1.0)
    key(ra, 0, (-2.44, -0.3, 0.12))
    key(ra, 5, (-2.1, -0.15, 0.1))           # dip
    key(ra, 12, (-3.12, 0.0, 0.05))          # straight up
    key(ra, 60, (-3.05, 0.0, 0.06))
    key(ra, 100, (-0.35, 0.0, 0.12))
    key(ra, 112, (-0.25, 0.0, 0.1))
    key(la, 0, (0.15, 0, -0.1)); key(la, 12, (0.5, 0, -0.2)); key(la, 60, (0.35, 0, -0.18)); key(la, 112, (0.05, 0, -0.08))
    key(body, 0, (0, -0.08, 0)); key(body, 12, (-0.12, 0.05, 0)); key(body, 60, (-0.08, 0, 0)); key(body, 112, (0, 0, 0))
    key(rl, 0, (0, 0, 0)); key(rl, 12, (0.12, 0, 0)); key(rl, 112, (0, 0, 0))
    key(ll, 0, (0, 0, 0)); key(ll, 12, (-0.1, 0, 0)); key(ll, 112, (0, 0, 0))
    return 112, []


def anim_nuke_air(root, P):
    """Carried up by Blue: knee drawn up, the right hand in a sign before the chest for the Purple incantation. Holds."""
    ra, la, body, rl, ll = P["right_arm"], P["left_arm"], P["body"], P["right_leg"], P["left_leg"]
    key(root, 0, weight=0.0); key(root, 12, weight=1.0); key(root, 60, weight=1.0)
    key(ra, 0, (-0.3, 0, 0.1)); key(ra, 16, (-1.4, -0.5, 0.12)); key(ra, 60, (-1.35, -0.48, 0.1))
    key(la, 0, (0.05, 0, -0.1)); key(la, 16, (-0.25, 0.1, -0.42)); key(la, 60, (-0.3, 0.1, -0.38))
    key(body, 0, (0, 0, 0)); key(body, 16, (0.08, -0.05, 0)); key(body, 60, (0.06, -0.05, 0))
    key(rl, 0, (0, 0, 0)); key(rl, 18, (-0.62, 0.05, 0.04)); key(rl, 60, (-0.58, 0.05, 0.04))
    key(ll, 0, (0, 0, 0)); key(ll, 18, (0.22, -0.03, -0.03)); key(ll, 60, (0.25, -0.03, -0.03))
    return 60, []


def anim_nuke_brace(root, P):
    """The mass erupts: the sign breaks, arms thrown out by the force, legs trailing. Holds."""
    ra, la, body, rl, ll = P["right_arm"], P["left_arm"], P["body"], P["right_leg"], P["left_leg"]
    key(root, 0, weight=1.0); key(root, 40, weight=1.0)
    key(ra, 0, (-1.35, -0.48, 0.1)); key(ra, 6, (-0.9, 0.0, 0.7)); key(ra, 40, (-0.55, 0.0, 0.5))
    key(la, 0, (-0.3, 0.1, -0.38)); key(la, 6, (-0.85, 0.0, -0.75)); key(la, 40, (-0.5, 0.0, -0.5))
    key(body, 0, (0.06, -0.05, 0)); key(body, 6, (-0.16, 0, 0)); key(body, 40, (-0.1, 0, 0))
    key(rl, 0, (-0.58, 0.05, 0.04)); key(rl, 8, (0.2, 0, 0.05)); key(rl, 40, (0.12, 0, 0.04))
    key(ll, 0, (0.25, -0.03, -0.03)); key(ll, 8, (-0.15, 0, -0.05)); key(ll, 40, (-0.08, 0, -0.04))
    return 40, []


ANIMS = {"nuke_red": anim_nuke_red, "nuke_throw": anim_nuke_throw, "nuke_air": anim_nuke_air, "nuke_brace": anim_nuke_brace,
         "blue_tap": anim_blue_tap, "blue_throw": anim_blue_throw,
         "purple_tap": anim_purple_tap, "purple_200_release": anim_purple_200_release,
         "purple_sign_1": anim_purple_sign_1, "purple_sign_2": anim_purple_sign_2,
         "purple_sign_3": anim_purple_sign_3, "purple_sign_4": anim_purple_sign_4,
         "red_tap": anim_red_tap, "red_charge": anim_red_charge, "red_release": anim_red_release}


def export(name, fn):
    sc, root, P = build_rig()
    end, aim = fn(root, P)
    for o in [root, *P.values()]:
        ease_all(o)
    sc.frame_start, sc.frame_end = 0, end
    tracks = {b: [] for b in P}
    weight = []
    for f in range(0, end + 1):
        sc.frame_set(f)
        for b, o in P.items():
            e = o.rotation_euler
            tracks[b].append([round(e.x, 5), round(e.y, 5), round(e.z, 5)])
        weight.append(round(float(root["weight"]), 4))
    # drop bones that never move (vanilla keeps control of them)
    tracks = {b: v for b, v in tracks.items() if any(abs(c) > 1e-5 for fr in v for c in fr)}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump({"fps": FPS, "frames": end + 1, "aim": aim, "weight": weight, "tracks": tracks}, f, separators=(",", ":"))
    print("[anim] wrote", name, end + 1, "frames, bones:", list(tracks))
    if PREVIEW:
        preview(sc, name, end)


def preview(sc, name, end):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    # three-quarter front view from the character's right (the rig faces Blender -Y, its right arm is at -X)
    target = bpy.data.objects.new("Target", None); sc.collection.objects.link(target); target.location = (0, 0, 1.2)
    cam.location = (-3.0, -3.6, 1.9)
    tr = cam.constraints.new("TRACK_TO"); tr.target = target; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"
    sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN"))
    sc.collection.objects.link(sun); sun.rotation_euler = (math.radians(50), 0, math.radians(30))
    sun.data.energy = 3.0
    sc.world = bpy.data.worlds.new("W"); sc.world.color = (0.35, 0.4, 0.5)
    sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = 8
    sc.render.resolution_x, sc.render.resolution_y = 320, 320
    for m in bpy.data.materials:
        m.use_nodes = True
        m.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = m.diffuse_color
    frames = [0, end // 6, end // 3, end // 2, 2 * end // 3, end]
    for f in frames:
        sc.frame_set(f)
        sc.render.filepath = os.path.join(PREVIEW_DIR, f"{name}_{f:03d}.png")
        bpy.ops.render.render(write_still=True)


for n, fn in ANIMS.items():
    export(n, fn)
