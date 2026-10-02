"""In-engine cutscene camera moves, authored in Blender and exported as per-frame camera tracks.

Caster space (Blender units = blocks): origin at the caster's feet, +Y = where the caster faces, +X = the caster's
right, +Z = up. The mod rotates this into the world using the caster's yaw at cast time.
Shots are keyframed on a camera + look-at target; hard cuts are CONSTANT keys; timeline markers become events
("flash:0.7", "flashv:0.8", "shake:0.4", "title:purple", "insert:name").

Run: blender -b --factory-startup -P blender/cutscene/build_cutscenes.py -- [--preview]
"""
import bpy, os, sys, json, math

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "cutscenes")
PREVIEW = "--preview" in sys.argv
SKIN = sys.argv[sys.argv.index("--skin") + 1] if "--skin" in sys.argv else os.path.join(HERE, "..", "..", "tools", "gojo_skin.png")
FPS = 60
sys.path.insert(0, HERE)


def new_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.fps = FPS
    sc.render.resolution_x, sc.render.resolution_y = 1920, 1080
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
    cam.data.sensor_fit = "VERTICAL"
    cam.data.sensor_height = 24.0
    sc.collection.objects.link(cam)
    sc.camera = cam
    tgt = bpy.data.objects.new("Target", None)
    sc.collection.objects.link(tgt)
    tr = cam.constraints.new("TRACK_TO")
    tr.target = tgt; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"
    return sc, cam, tgt


def fov_to_lens(fov_deg):
    return 12.0 / math.tan(math.radians(fov_deg) / 2)


def shot(cam, tgt, frame, loc, look, fov=70, roll=0.0, cut=False):
    """Key the camera at `frame`. cut=True makes the previous key hold until this frame (hard cut)."""
    cam.location = loc; tgt.location = look
    cam.data.lens = fov_to_lens(fov)
    cam["roll"] = roll
    for obj, path in ((cam, "location"), (tgt, "location"), (cam.data, "lens"), (cam, '["roll"]')):
        obj.keyframe_insert(path, frame=frame)
    if cut:
        for ad in (cam.animation_data, tgt.animation_data, cam.data.animation_data):
            for fc in ad.action.fcurves:
                pts = sorted(fc.keyframe_points, key=lambda k: k.co.x)
                for i, kp in enumerate(pts):
                    if abs(kp.co.x - frame) < 0.5 and i > 0:
                        pts[i - 1].interpolation = "CONSTANT"


def smooth_all(*objs):
    for o in objs:
        ad = o.animation_data
        if not ad or not ad.action:
            continue
        for fc in ad.action.fcurves:
            for kp in fc.keyframe_points:
                if kp.interpolation != "CONSTANT":
                    kp.interpolation = "BEZIER"
                    kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"


def export(name, sc, cam, end, markers, continues=False):
    for f, m in markers:
        sc.timeline_markers.new(m, frame=f)
    tgt = bpy.data.objects["Target"]
    frames = []
    for f in range(0, end + 1):
        sc.frame_set(f)
        m = cam.matrix_world
        look = tgt.matrix_world.translation
        pos = m.translation
        fwd = -m.col[2].xyz.normalized()
        up = m.col[1].xyz.normalized()
        # optional roll about the view axis (keyed as a custom property)
        roll = math.radians(float(cam.get("roll", 0.0)))
        if abs(roll) > 1e-5:
            from mathutils import Matrix
            up = (Matrix.Rotation(roll, 3, fwd) @ up).normalized()
        fov = math.degrees(cam.data.angle_y)
        frames.append([round(v, 4) for v in (pos.x, pos.y, pos.z, fwd.x, fwd.y, fwd.z, up.x, up.y, up.z)] + [round(fov, 3)]
                      + [round(v, 3) for v in (look.x, look.y, look.z)])
    ev = [{"frame": f, "name": m} for f, m in sorted(markers)]
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w") as fp:
        out = {"fps": FPS, "frames": frames, "events": ev}
        if continues:
            out["continues"] = True
        json.dump(out, fp, separators=(",", ":"))
    print("[cutscene] wrote", name, len(frames), "frames,", len(ev), "events")
    if PREVIEW and name.startswith("nuke"):
        import nuke_preview
        alt, start = [], None
        for f, m in sorted(markers):
            if m == "space:alt":
                start = f
            elif m == "space:main" and start is not None:
                alt.append((start, f)); start = None
        only = [int(x) for x in sys.argv[sys.argv.index("--frames") + 1].split(",")] if "--frames" in sys.argv else None
        nuke_preview.render(name, sc, cam, frames, alt, SKIN, os.path.join(HERE, "previews"), only=only)
    elif PREVIEW and name.startswith("purple"):
        import purple_preview
        only = [int(x) for x in sys.argv[sys.argv.index("--frames") + 1].split(",")] if "--frames" in sys.argv else None
        purple_preview.render(name, sc, cam, frames, SKIN, os.path.join(HERE, "previews"), only=only)
    elif PREVIEW:
        preview(name, sc, cam, end)


def preview(name, sc, cam, end):
    """Stand-ins so framing can be judged: caster (1.8 tall, faces +Y) and the purple mass."""
    def box(n, loc, dims, col):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        o = bpy.context.active_object; o.name = n; o.scale = dims
        m = bpy.data.materials.new(n); m.use_nodes = True
        m.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (*col, 1)
        o.data.materials.append(m)
        return o
    cx, cy, cz = PREVIEW_CASTER.get(name, (0, 0, 0))
    box("caster", (cx, cy, cz + 0.9), (0.6, 0.3, 1.8), (0.2, 0.25, 0.35))
    box("face", (cx, cy + 0.2, cz + 1.6), (0.3, 0.1, 0.2), (0.9, 0.7, 0.55))
    for n, loc, dims, col in PREVIEW_BOXES.get(name, []):
        box(n, loc, dims, col)
    bpy.ops.mesh.primitive_plane_add(size=400, location=(0, 0, PREVIEW_GROUND.get(name, 0.0)))
    g = bpy.context.active_object
    gm = bpy.data.materials.new("g"); gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.25, 0.4, 0.2, 1); g.data.materials.append(gm)
    sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN")); sc.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(40), 0, math.radians(30))
    sc.world = bpy.data.worlds.new("W"); sc.world.color = (0.5, 0.6, 0.8)
    sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = 4
    sc.render.resolution_x, sc.render.resolution_y = 384, 216
    for o in PREVIEW_PROPS.get(name, []):
        n, loc, r, col = o
        bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=loc)
        s = bpy.context.active_object
        m = bpy.data.materials.new(n); m.use_nodes = True
        bsdf = m.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Emission Color"].default_value = (*col, 1); bsdf.inputs["Emission Strength"].default_value = 3
        s.data.materials.append(m)
    d = os.path.join(HERE, "previews"); os.makedirs(d, exist_ok=True)
    for f in range(0, end + 1, max(1, end // 11)):
        sc.frame_set(f)
        sc.render.filepath = os.path.join(d, f"{name}_{f:03d}.png")
        bpy.ops.render.render(write_still=True)


PREVIEW_PROPS = {
    "purple_tap": [("mass", (0, 6.5, 2.1), 4.5 * 0.6, (0.6, 0.2, 1.0))],
    "purple_200": [("mass", (0, 14.5, 5.5), 11 * 0.6, (0.6, 0.2, 1.0))],
}
# (the nuke tracks preview with the posed character: nuke_preview.py)
PREVIEW_CASTER = {}
PREVIEW_GROUND = {}
PREVIEW_BOXES = {}


# ------------------------------------------------------------------ Hollow Purple (tap), 2.6 s
def purple_tap():
    """Mass forms R+2 = 6.5 ahead at eye height (+0.45). Collide tick 20 = frame 60, launch tick 34 = frame 102."""
    sc, cam, tgt = new_scene()
    # 0.0–0.85 s: low three-quarter front, slow push-in while Blue and Red bloom at the hands
    shot(cam, tgt, 0, (3.0, 3.2, 0.95), (0.0, 0.5, 1.35), fov=46)
    shot(cam, tgt, 50, (2.4, 2.7, 1.05), (0.0, 0.6, 1.4), fov=42)
    # cut: wide side profile — the hands close, the infinities collide, the mass blooms ahead
    shot(cam, tgt, 51, (9.5, 3.0, 1.8), (0.0, 3.8, 1.8), fov=48, cut=True)
    shot(cam, tgt, 66, (9.0, 3.3, 1.9), (0.0, 4.2, 1.9), fov=46, roll=-3)
    # cut: over the right shoulder, the mass swelling in front
    shot(cam, tgt, 67, (1.2, -3.0, 2.1), (0.0, 6.5, 2.0), fov=56, cut=True)
    shot(cam, tgt, 100, (1.0, -2.5, 2.0), (0.0, 7.0, 2.0), fov=52)
    # launch (tick 34 = frame 102): chase it for a moment, lens widening for speed
    shot(cam, tgt, 104, (1.1, -2.0, 2.1), (0.0, 14.0, 2.0), fov=68)
    shot(cam, tgt, 156, (2.0, 14.0, 3.4), (0.0, 70.0, 2.0), fov=86)
    smooth_all(cam, tgt, cam.data)
    export("purple_tap", sc, cam, 156, [
        (60, "flashv:0.6"), (60, "shake:0.35"),
        (102, "shake:0.55"), (102, "title:purple"),
    ])


# ------------------------------------------------------------------ 200% Hollow Purple release, 3.8 s
def purple_200():
    """Caster space. The mass (R = 11) forms 14.5 ahead, centre 5.5 up; Blue and Red swing in from their orbit above
    him (r 3.2, 3.2 up) and collide at tick 16 (frame 48); it blooms by tick 24, swells, and is sent at tick 44
    (frame 132), flying off at 5.5 blocks a tick. Pose: purple_200_release."""
    sc, cam, tgt = new_scene()
    # T0-16: from the side, far enough to hold both - he sends them in, they swing down from above him and meet ahead
    shot(cam, tgt, 0, (12.5, 5.0, 2.0), (0.0, 7.0, 3.2), fov=56)
    shot(cam, tgt, 47, (12.0, 5.6, 2.3), (0.0, 8.5, 3.8), fov=58)
    # T16-26: low behind him - the collision, the mass blooming over him (the caster a silhouette against it)
    shot(cam, tgt, 48, (1.6, -6.2, 0.9), (0.0, 12.0, 4.6), fov=62, roll=-2, cut=True)
    shot(cam, tgt, 77, (1.7, -6.8, 0.95), (0.0, 12.0, 5.0), fov=64, roll=-1)
    # T26-44: far and wide - the scale of it: him, tiny, beside the sphere he is holding back
    shot(cam, tgt, 78, (-27.0, 4.0, 2.5), (0.0, 9.0, 5.0), fov=58, cut=True)
    shot(cam, tgt, 131, (-25.0, 4.5, 2.8), (0.0, 9.5, 5.2), fov=56)
    # T44-: behind him as he sends it - it tears away down its trench (4.5 s)
    shot(cam, tgt, 132, (2.2, -5.5, 2.6), (0.0, 20.0, 4.5), fov=60, cut=True)
    shot(cam, tgt, 170, (2.4, -6.2, 2.8), (0.0, 60.0, 4.0), fov=50)
    shot(cam, tgt, 228, (2.2, -6.0, 2.6), (0.0, 160.0, 4.0), fov=34)
    smooth_all(cam, tgt, cam.data)
    export("purple_200", sc, cam, 228, [
        (48, "flashv:0.45"), (48, "shake:0.6"),
        (132, "shake:0.9"), (132, "title:purple_200"), (134, "flash:0.12"),
    ])


# ------------------------------------------------------------------ Remote Hollow Purple, part 1 (ground), 7.3 s
# Shot for shot after the reference clip (docs/storyboard/NUKE_STORYBOARD.md), on the player's own model in-engine.
# Frames = ticks x 3. Poses: nuke_red (T0-84), nuke_throw (T84-262). Red at the right fingertips: in front of the
# chest (0.14, 0.71, 1.47) at T12, rising to beside the right eye (0.25, 0.30, 1.82) by T60.
def nuke_ground():
    sc, cam, tgt = new_scene()
    # T0-52: low from the front-left, looking up at him; the sign raised, Red igniting, its rays lancing out (0-2.5 s)
    shot(cam, tgt, 0, (-1.125, 2.203, 0.919), (0.047, 0.375, 1.359), fov=54)
    shot(cam, tgt, 155, (-0.919, 1.856, 0.994), (0.094, 0.328, 1.481), fov=50)
    # T52-64: over his shoulder, the raised fingers against Red and the sky (3.0 s)
    shot(cam, tgt, 156, (0.694, -0.356, 1.762), (0.169, 0.431, 1.669), fov=44, cut=True)
    shot(cam, tgt, 191, (0.638, -0.225, 1.753), (0.178, 0.412, 1.678), fov=41)
    # T64-84: his face, the sign and Red beside his eye (3.5-4.0 s)
    shot(cam, tgt, 192, (0.112, 1.387, 1.556), (0.094, 0.244, 1.659), fov=44, cut=True)
    shot(cam, tgt, 251, (0.131, 1.2, 1.584), (0.103, 0.244, 1.669), fov=41)
    # T84-110: the throw, full figure from low in front - Red streaks up out of the top of the frame (4.5-5.5 s)
    shot(cam, tgt, 252, (-0.581, 2.766, 0.694), (0.0, 0.0, 1.5), fov=60, cut=True)
    shot(cam, tgt, 329, (-0.516, 2.531, 0.731), (0.0, 0.0, 1.613), fov=57)
    # T110-146: the Six Eyes - head and shoulders, the two fingers in front of his face, pushing in (6.0-7.0 s)
    shot(cam, tgt, 330, (-0.619, 1.219, 1.5), (0.028, 0.169, 1.613), fov=40, cut=True)
    shot(cam, tgt, 438, (-0.469, 1.05, 1.537), (0.019, 0.159, 1.622), fov=38)
    smooth_all(cam, tgt, cam.data)
    export("nuke_ground", sc, cam, 438, [
        (252, "shake:0.35"), (252, "flashr:0.3"),
        (330, "flashb:0.15"),
    ])


# ------------------------------------------------------------------ Remote Hollow Purple, part 2 (sky), 17.7 s
def nuke_sky():
    """C space: origin at the convergence point, +y from the caster toward the target. Red holds at (22,3,9), Blue at
    (-22,3,9), the caster hovers at (0,-16,-4), the target stands ~18 below C. Frame 0 = T140; frame = (T-140) x 3.
    Blue forms T140 - boost T160 - Blue incantation T172/194/214 - lift T232 - leap T262-292 - Purple incantation
    T290/308/326/344 - converge T296-356 - collide insert T356 - implode insert T380 - bloom T396 - glint T462 -
    detonation T470-580. Frames 276-378 (T232-266) are in caster space ("space:alt"): behind the caster on the ground."""
    sc, cam, tgt = new_scene()
    # T140-158: the target's moment - low beside it, looking up as Blue ignites across the sky (7.5-8.0 s)
    shot(cam, tgt, 0, (5.2, -1.6, -17.3), (-6.0, 0.6, -12.6), fov=62)
    shot(cam, tgt, 53, (4.9, -1.4, -17.2), (-6.0, 0.8, -11.8), fov=60)
    # T158-196: far below, both in the sky - the boost streaks up into Blue, the flare, the first word (8.5-10 s)
    shot(cam, tgt, 54, (1.5, -36.0, -15.0), (0.0, 3.0, 2.0), fov=58, roll=-3, cut=True)
    shot(cam, tgt, 167, (1.0, -33.0, -14.0), (-1.0, 3.0, 2.5), fov=55, roll=-2)
    # T196-232: close on Blue as its spiral arms wind in with the last words (10.5-12.5 s)
    shot(cam, tgt, 168, (-9.0, -12.0, 3.0), (-22.0, 3.0, 9.3), fov=50, cut=True)
    shot(cam, tgt, 275, (-13.5, -14.0, 3.5), (-22.0, 3.0, 9.3), fov=45)
    # T232-266 (caster space): behind him on the ground - the target torn into the sky between the two; he crouches
    # and leaps (13-14 s)
    shot(cam, tgt, 276, (1.25, -3.5, 1.4), (0.0, 6.0, 3.4), fov=66, cut=True)
    shot(cam, tgt, 377, (1.2, -3.3, 1.36), (0.0, 6.0, 3.6), fov=64)
    # T266-300: far below and to the side, he rises across the sky to his place behind the target (14.5-15.0 s)
    shot(cam, tgt, 378, (9.0, -36.0, -15.5), (0.0, -16.0, -3.0), fov=58, cut=True)
    shot(cam, tgt, 479, (8.0, -35.0, -15.0), (0.0, -12.0, -1.0), fov=56)
    # T300-342: wide from below - the Purple incantation, Red and Blue starting in on the target (15-16 s)
    shot(cam, tgt, 480, (-2.2, -21.5, -8.2), (0.3, -6.0, 2.0), fov=68, cut=True)
    shot(cam, tgt, 605, (-2.0, -21.0, -7.9), (0.3, -5.0, 2.2), fov=64)
    # T342-356: over his shoulder, they rush in on it
    shot(cam, tgt, 606, (1.7, -21.0, -2.9), (0.0, 0.0, 0.6), fov=50, cut=True)
    shot(cam, tgt, 647, (1.6, -20.5, -2.8), (0.0, 0.0, 0.8), fov=44)
    # (collide T356-380 + implode T380-390 inserts cover 648-749)
    shot(cam, tgt, 648, (1.05, -13.8, -2.3), (0.05, -16.0, -2.46), fov=42, cut=True)
    # T390-420: his face, lit by the point hanging in front of him - then by the bloom as it erupts and swallows
    # everything (19-20.5 s; the clip has the mass behind him, here it is where it really is: in front)
    shot(cam, tgt, 750, (1.05, -13.8, -2.3), (0.05, -16.0, -2.46), fov=42)
    shot(cam, tgt, 768, (1.05, -13.85, -2.3), (0.05, -16.05, -2.46), fov=42)
    shot(cam, tgt, 828, (1.1, -15.9, -2.35), (0.05, -18.2, -2.5), fov=47)
    shot(cam, tgt, 839, (1.1, -16.0, -2.35), (0.05, -18.25, -2.5), fov=48)
    # T420-462: wide and low - his silhouette against the white heart, the whole sky magenta (21-22.5 s)
    shot(cam, tgt, 840, (-6.5, -43.0, -7.5), (0.0, -8.0, -1.5), fov=46, cut=True)
    shot(cam, tgt, 965, (-5.8, -41.0, -7.0), (0.0, -8.0, -1.2), fov=44)
    # behind the glint: the detonation, far and high so the whole blast is in frame - the fireball swelling, the
    # shock ring racing out across the land, the smoke climbing into a column (T462-580)
    shot(cam, tgt, 966, (-66.0, -126.0, 50.0), (0.0, 0.0, -10.0), fov=70, cut=True)
    shot(cam, tgt, 1320, (-58.0, -116.0, 58.0), (0.0, 0.0, 8.0), fov=68)
    smooth_all(cam, tgt, cam.data)
    export("nuke_sky", sc, cam, 1320, [
        (0, "flashb:0.45"), (0, "shake:0.25"),
        (276, "space:alt"), (378, "space:main"),
        (648, "insert:nuke_collide"), (720, "insert:nuke_implode"),
        (768, "shake:1.0"), (840, "shake:0.5"),
        (966, "insert:nuke_glint"), (990, "shake:1.3"),
    ], continues=True)


# ------------------------------------------------------------------ Domain Expansion: Unlimited Void (hold), 10.2 s
# Caster space at the game's 15/16 player scale: eyes ~(±0.09, 0.23, 1.61). DomainEntity: D_WHITE 44, D_INK 62,
# D_TUNNEL 102, D_FLASH 158, D_OPEN 160 (frames = ticks x 3). Pose: domain_seal.
def domain_full():
    sc, cam, tgt = new_scene()
    # T0-14: the Six Eyes - right on his eye, pushing out to the face (S1E7 0.0-0.75 s)
    shot(cam, tgt, 0, (0.1, 0.95, 1.6), (0.02, 0.2, 1.62), fov=30)
    shot(cam, tgt, 41, (0.18, 1.3, 1.58), (0.02, 0.2, 1.6), fov=38)
    # T14-44: the seal raised beside his face, the collar pulled down (1.0-3.0 s)
    shot(cam, tgt, 42, (0.45, 1.45, 1.42), (0.04, 0.15, 1.5), fov=44, cut=True)
    shot(cam, tgt, 131, (0.38, 1.3, 1.44), (0.04, 0.15, 1.51), fov=42)
    # T44-62: the world goes white; the same framing, drifting back
    shot(cam, tgt, 132, (0.4, 1.4, 1.42), (0.04, 0.15, 1.5), fov=43)
    shot(cam, tgt, 185, (0.52, 1.85, 1.37), (0.03, 0.12, 1.45), fov=47)
    # T62-82: the ink erupts behind him
    shot(cam, tgt, 186, (0.85, 2.7, 1.15), (0.0, 0.0, 1.25), fov=55, cut=True)
    shot(cam, tgt, 245, (1.1, 3.4, 1.1), (0.0, 0.0, 1.2), fov=58)
    # T82-102: space manifests - the neon lines spread through the ink; around to his other side, looking past him
    shot(cam, tgt, 246, (-1.6, 3.0, 1.3), (0.2, -1.0, 1.35), fov=60, cut=True)
    shot(cam, tgt, 305, (-2.4, 2.1, 1.45), (0.3, -2.0, 1.3), fov=64)
    # T102-158: through the tunnel of information, gathering speed, gliding past him and out ahead
    shot(cam, tgt, 306, (0.9, -2.2, 1.6), (0.0, 15.0, 1.2), fov=62, cut=True)
    shot(cam, tgt, 390, (2.6, 1.0, 1.5), (0.0, 14.0, 1.0), fov=60)
    shot(cam, tgt, 473, (3.4, 5.0, 1.4), (-2.0, 18.0, 1.0), fov=54)
    # T158 flash; T160-204: the void - low behind him, the black hole hanging ahead
    shot(cam, tgt, 474, (1.2, -3.2, 0.7), (0.0, 12.0, 4.2), fov=62, cut=True)
    shot(cam, tgt, 612, (1.45, -3.9, 0.8), (0.0, 12.0, 4.6), fov=60)
    smooth_all(cam, tgt, cam.data)
    export("domain_full", sc, cam, 612, [(186, "shake:0.35")])


# ------------------------------------------------------------------ the 0.2-second Unlimited Void (tap), 1.55 s
# I_WHITE 8, I_INK 11, I_VOID 15, I_WIPE 19, I_END 31. Pose: domain_instant.
def domain_instant():
    sc, cam, tgt = new_scene()
    shot(cam, tgt, 0, (0.4, 1.35, 1.42), (0.04, 0.15, 1.5), fov=42)
    shot(cam, tgt, 44, (0.46, 1.6, 1.4), (0.03, 0.13, 1.47), fov=45)
    shot(cam, tgt, 45, (1.1, -3.0, 0.8), (0.0, 12.0, 4.4), fov=62, cut=True)
    shot(cam, tgt, 93, (1.2, -3.3, 0.85), (0.0, 12.0, 4.5), fov=61)
    smooth_all(cam, tgt, cam.data)
    export("domain_instant", sc, cam, 93, [(33, "shake:0.25")])


ONLY = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
for fn in (purple_tap, purple_200, nuke_ground, nuke_sky, domain_full, domain_instant):
    if ONLY is None or fn.__name__ in ONLY:
        fn()
