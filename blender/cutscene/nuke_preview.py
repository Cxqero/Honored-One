"""Framing previews for the nuke camera tracks: the real Minecraft character (mcchar), posed with the exported
animation at the same moment, and Red, Blue, the target and the bloom where NukeEntity puts them. Used by
build_cutscenes.py --preview; renders one frame at the start, middle and end of every shot.

Spaces (Blender units = blocks): caster space for nuke_ground and the "space:alt" part of nuke_sky (caster's feet at
the origin, facing +y), C space for the rest of nuke_sky (the convergence point at the origin). The layout assumes a
flat world with the target DIST blocks ahead and C LIFT blocks above it.
"""
import bpy, os, sys, math
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "anim"))
sys.path.insert(0, os.path.join(HERE, "..", "lib"))
import moves
import mcchar

DIST, LIFT = 40.0, 18.0
HOVER = Vector((0, -16, -4))
RED_HOLD, BLUE_HOLD = Vector((22, 3, 9)), Vector((-22, 3, 9))
T_RED_FORM, T_SIGN, T_THROW, T_SKY, T_BOOST = 4, 60, 84, 140, 160
T_BLUE = (172, 194, 214)
T_LIFT, T_LEAP, T_LEAP_END, T_CONVERGE, T_COLLIDE, T_BLOOM = 232, 262, 292, 296, 356, 396
RADIUS, EXPAND = 64.0, 60


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def bezier(a, b, c, d, k):
    u = 1 - k
    return a * u ** 3 + b * (3 * u * u * k) + c * (3 * u * k * k) + d * k ** 3


class Stage:
    def __init__(self, sc, skin):
        self.sc = sc
        self.ch = mcchar.McChar(skin)
        self.red = self._orb("Red", (1.0, 0.12, 0.06), 12)
        self.blue = self._orb("Blue", (0.15, 0.5, 1.0), 10)
        self.heart = self._orb("Heart", (1.0, 0.85, 1.0), 30)
        self.field = self._orb("Field", (0.8, 0.2, 1.0), 0.6, alpha=0.25)
        self.target = self._box("Target", (1.4, 1.2, 2.7), (0.55, 0.5, 0.45))
        bpy.ops.mesh.primitive_plane_add(size=600)
        self.ground = bpy.context.active_object
        gm = bpy.data.materials.new("g"); gm.use_nodes = True
        gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.16, 0.22, 0.14, 1)
        self.ground.data.materials.append(gm)
        sc.world = bpy.data.worlds.new("W"); sc.world.use_nodes = True
        bg = sc.world.node_tree.nodes["Background"]
        bg.inputs[0].default_value = (0.16, 0.19, 0.3, 1); bg.inputs[1].default_value = 1.0
        sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN")); sc.collection.objects.link(sun)
        sun.rotation_euler = (math.radians(50), 0, math.radians(200)); sun.data.energy = 2.5

    def _orb(self, name, col, strength, alpha=1.0):
        bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, segments=24, ring_count=12)
        o = bpy.context.active_object; o.name = name
        m = bpy.data.materials.new(name); m.use_nodes = True
        nt = m.node_tree
        b = nt.nodes["Principled BSDF"]
        b.inputs["Base Color"].default_value = (*col, 1)
        b.inputs["Emission Color"].default_value = (*col, 1); b.inputs["Emission Strength"].default_value = strength
        b.inputs["Alpha"].default_value = alpha
        o.data.materials.append(m)
        return o

    def _box(self, name, dims, col):
        bpy.ops.mesh.primitive_cube_add(size=1)
        o = bpy.context.active_object; o.name = name; o.scale = dims
        m = bpy.data.materials.new(name); m.use_nodes = True
        m.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (*col, 1)
        o.data.materials.append(m)
        return o

    def pose(self, anim, frame, pitch_deg, place):
        a = moves.ANIMS[anim]
        p = a.sample(min(frame, a.length))
        h = dict(p.get("head", {}))
        r = h.get("rot", (0.0, 0.0, 0.0))
        h["rot"] = (r[0] + math.radians(pitch_deg), r[1], r[2])
        p["head"] = h
        self.ch.place = place
        self.ch.apply(p)
        return p

    def put(self, ob, loc, radius):
        if loc is None or radius <= 0:
            ob.hide_render = True
            return
        ob.hide_render = False
        ob.location = loc
        ob.scale = (radius,) * 3


def aim_pitch(T, caster, C):
    if T < T_THROW:
        return 0.0
    if T < T_THROW + 28:
        return -68.0
    if T < T_SKY:
        return -4.0
    if T < T_LEAP:
        return -38.0
    d = C - (caster + Vector((0, 0, 1.62)))
    return -math.degrees(math.atan2(d.z, math.hypot(d.x, d.y)))


def red_size(T):
    if T < T_THROW:
        return 0.03 + 0.025 * smooth(T_RED_FORM, T_SIGN, T)      # (the solid core only: the rest is glow)
    if T < T_SKY:
        return 0.13 + 1.07 * smooth(T_THROW, T_THROW + 26, T)
    if T < T_CONVERGE:
        return 2.2
    return 2.2 + 0.9 * smooth(T_CONVERGE, T_COLLIDE, T)


def blue_size(T):
    if T < T_SKY:
        return 0.0
    f = smooth(T_SKY, T_SKY + 10, T) * 2.3
    f += 0.45 * sum(smooth(t, t + 6, T) for t in T_BLUE)
    return f + 0.6 * smooth(T_CONVERGE, T_COLLIDE, T)


def converge(hold, T, side):
    if T < T_CONVERGE:
        return hold
    k = max(0.0, min(1.0, (T - T_CONVERGE) / (T_COLLIDE - T_CONVERGE)))
    e = k ** 2.4
    meet = Vector((side * 0.6, 0, 0))
    return hold.lerp(meet, e) + Vector((0, 0, math.sin(math.pi * e) * 3.0))


def layout(st, track, f, alt):
    """Place everything for frame f of `track`. alt: this frame is authored in caster space."""
    rot = Matrix.Rotation(math.pi, 4, "Z")          # mcchar faces -y; the caster faces +y
    if track == "nuke_ground":
        T = f / 3.0
        origin = Vector((0, 0, 0)); C = Vector((0, DIST, LIFT)); off = Vector((0, 0, 0))
    else:
        T = T_SKY + f / 3.0
        origin = Vector((0, -DIST, -LIFT)); C = Vector((0, 0, 0))
        off = Vector((0, DIST, LIFT)) if alt else Vector((0, 0, 0))   # C space → caster space
    # the caster
    if T < T_LEAP:
        caster = origin.copy()
    elif T < T_LEAP_END:
        k = smooth(T_LEAP, T_LEAP_END, T + 1)
        a = origin; d = HOVER
        b = a + Vector((0, 0, 9)) + (d - a) * 0.25
        c = d + Vector((0, 0, 4)) - Vector((0, 3, 0))
        caster = bezier(a, b, c, d, k)
    else:
        caster = HOVER + Vector((0, -2.2 * smooth(T_BLOOM, T_BLOOM + 20, T), 0))
    if T < T_THROW:
        anim, af = "nuke_red", T * 3
    elif T < T_LEAP:
        anim, af = "nuke_throw", (T - T_THROW) * 3
    elif T < T_BLOOM:
        anim, af = "nuke_air", (T - T_LEAP) * 3
    else:
        anim, af = "nuke_brace", (T - T_BLOOM) * 3
    place = Matrix.Translation(caster + off) @ rot
    p = st.pose(anim, int(af), aim_pitch(T, caster, C), place)
    tip = st.ch.point("right", 3.5, p)
    # Red
    if T < T_THROW:
        red = tip
    elif T < T_THROW + 26:
        k = (T - T_THROW) / 26.0
        apex = origin + Vector((0, 4, 38)) + off
        red = tip.lerp(apex, 1 - (1 - k) ** 2)
    elif T < T_SKY:
        apex = origin + Vector((0, 4, 38))
        red = apex.lerp(C + RED_HOLD, smooth(T_THROW + 26, T_SKY - 2, T)) + off
    else:
        red = converge(C + RED_HOLD, T, 1) + off
    st.put(st.red, red if T < T_COLLIDE and T >= T_RED_FORM else None, red_size(T))
    st.put(st.blue, converge(C + BLUE_HOLD, T, -1) + off if T_SKY <= T < T_COLLIDE else None, blue_size(T))
    # the target: on the ground until the lift, then held at C
    tgt = C + Vector((0, 0, -LIFT + 1.35))
    if T >= T_LIFT:
        tgt = tgt.lerp(C, smooth(T_LIFT, T_LIFT + 40, T))
    st.target.location = tgt + off
    st.target.hide_render = T >= T_BLOOM + 4
    # the bloom
    if T >= T_BLOOM:
        k = min(1.0, (T - T_BLOOM) / EXPAND)
        r = RADIUS * (1 - (1 - k) ** 2.6)
        st.put(st.field, C + off, r)
        st.put(st.heart, C + off, 2.5 + 0.1 * r)
    else:
        st.put(st.field, None, 0)
        st.put(st.heart, C + off, 1.2 if T >= T_COLLIDE + 4 else 0)
    st.ground.location = (0, 0, (0 if track == "nuke_ground" else -LIFT) + off.z)


def shots(frames_n, cam_frames):
    """Start, middle and end frame of every shot (cuts found like the game does)."""
    cuts = [0]
    for i in range(frames_n - 1):
        a, b = cam_frames[i], cam_frames[i + 1]
        d = sum((a[j] - b[j]) ** 2 for j in range(3))
        dot = sum(a[3 + j] * b[3 + j] for j in range(3))
        if d > 1.0 or dot < 0.97:
            cuts.append(i + 1)
    cuts.append(frames_n)
    out = []
    for s, e in zip(cuts, cuts[1:]):
        out += sorted({s, (s + e - 1) // 2, max(s, e - 2)})
    return out


def render(name, sc, cam, cam_frames, alt_ranges, skin, outdir, only=None, size=270):
    st = Stage(sc, skin)
    sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = 6; sc.cycles.use_denoising = False
    sc.cycles.max_bounces = 1
    sc.view_settings.view_transform = "Standard"
    sc.render.resolution_x, sc.render.resolution_y = int(size * 16 / 9), size
    cam.data.clip_start = 0.02
    os.makedirs(outdir, exist_ok=True)
    frames = only or shots(len(cam_frames), cam_frames)
    for f in frames:
        alt = any(a <= f < b for a, b in alt_ranges)
        sc.frame_set(f)
        layout(st, name, f, alt)
        sc.render.filepath = os.path.join(outdir, f"{name}_{f:04d}.png")
        bpy.ops.render.render(write_still=True)
    print("[preview]", name, len(frames), "frames ->", outdir, flush=True)
