"""Framing previews for the Hollow Purple camera tracks (purple_tap, purple_200): the posed character, Blue and Red
swinging in and colliding, the imaginary mass blooming ahead and flying off, where PurpleEntity / PurpleRenderer
put them. Caster space: feet at the origin, facing +y. Frames = ticks x 3 from the release.
"""
import bpy, os, sys, math
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nuke_preview as NP

EYE = 1.62
SPEC = {
    # radius, collide tick, launch tick, form distance, rise factor, speed (blocks/tick), release anim, spread
    "purple_200": dict(R=11.0, collide=16, launch=44, dist=11.0 + 3.5, rise=0.35, speed=110 / 20.0, anim="purple_200_release", full=True),
    "purple_tap": dict(R=4.5, collide=20, launch=34, dist=4.5 + 2.0, rise=0.1, speed=120 / 20.0, anim="purple_tap", full=False),
}


def ease_out_back(x):
    c1, c3 = 1.70158, 2.70158
    return 1 + c3 * (x - 1) ** 3 + c1 * (x - 1) ** 2


def layout(st, name, f):
    s = SPEC[name]
    T = f / 3.0
    place = Matrix.Rotation(math.pi, 4, "Z")
    p = st.pose(s["anim"], int(f), 0.0, place)
    form = Vector((0, s["dist"], EYE + s["R"] * s["rise"]))
    R = s["R"]
    if T < s["collide"]:
        k = T / s["collide"]
        c = k ** 3
        if s["full"]:
            a = T * 3.2
            lh = Vector((math.cos(a) * 3.2, math.sin(a) * 3.2, 3.2))
            rh = Vector((-math.cos(a) * 3.2, -math.sin(a) * 3.2, 3.2))
        else:
            lh = st.ch.point("left", 3.5, p)
            rh = st.ch.point("right", 3.5, p)
        size = (1.3 if s["full"] else 0.45) * (0.6 + 0.6 * min(1.0, k * 3))
        st.put(st.blue, lh.lerp(form, c), size)
        st.put(st.red, rh.lerp(form, c), size)
        st.put(st.heart, None, 0)
    else:
        st.put(st.blue, None, 0)
        st.put(st.red, None, 0)
        grow = ease_out_back(min(1.0, (T - s["collide"]) / 8.0))
        pos = form.copy()
        if T >= s["launch"]:
            pos.y += (T - s["launch"]) * s["speed"]
        st.put(st.heart, pos, R * grow * 0.6)
    st.put(st.field, None, 0)
    st.target.hide_render = True
    st.ground.location = (0, 0, 0)


def render(name, sc, cam, cam_frames, skin, outdir, only=None, size=270):
    st = NP.Stage(sc, skin)
    # the mass reads violet in the preview
    m = st.heart.data.materials[0].node_tree.nodes["Principled BSDF"]
    m.inputs["Emission Color"].default_value = (0.65, 0.25, 1.0, 1)
    m.inputs["Base Color"].default_value = (0.65, 0.25, 1.0, 1)
    sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = 6; sc.cycles.use_denoising = False
    sc.cycles.max_bounces = 1
    sc.view_settings.view_transform = "Standard"
    sc.render.resolution_x, sc.render.resolution_y = int(size * 16 / 9), size
    cam.data.clip_start = 0.02
    os.makedirs(outdir, exist_ok=True)
    frames = only or NP.shots(len(cam_frames), cam_frames)
    for f in frames:
        sc.frame_set(f)
        layout(st, name, f)
        sc.render.filepath = os.path.join(outdir, f"{name}_{f:04d}.png")
        bpy.ops.render.render(write_still=True)
    print("[preview]", name, len(frames), "frames ->", outdir, flush=True)
