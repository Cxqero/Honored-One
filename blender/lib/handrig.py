"""Procedural stylised right hand (+ uniform sleeve) with an armature, for the hand-sign close-up inserts.

Minecraft hands have no fingers, so every hand sign (Red's raised fingers, the Purple signs, Unlimited Void's
Taishakuten seal) is shown as a Blender-rendered close-up. The hand is built from primitives, fused with a voxel
remesh into one watertight surface, skinned with computed weights and posed from presets.

Hand space (metres): wrist at the origin, fingers along +Z, palm faces -Y, thumb on +X (a right hand seen palm-on).
Shading is manga-style cel shading (osl/toon.osl) with an inverted-hull ink outline, so the result reads like the
greyscale manga panels of the reference with the technique's light colouring it.
"""
import bpy, bmesh, math, os
import numpy as np
from mathutils import Vector, Matrix, Euler

LIB = os.path.dirname(os.path.abspath(__file__))
OSL_DIR = os.path.join(os.path.dirname(LIB), "osl")

# name: (knuckle position, segment lengths, radius at base, radius at tip, rest splay deg (+ = toward thumb))
FINGERS = {
    "index":  ((0.0300, 0.0, 0.0905), (0.0410, 0.0245, 0.0200), 0.0095, 0.0073, 10.0),
    "middle": ((0.0092, 0.0, 0.0955), (0.0445, 0.0275, 0.0210), 0.0098, 0.0076, 2.0),
    "ring":   ((-0.0110, 0.0, 0.0915), (0.0415, 0.0260, 0.0200), 0.0093, 0.0072, -6.0),
    "pinky":  ((-0.0290, 0.0, 0.0820), (0.0320, 0.0200, 0.0180), 0.0080, 0.0063, -15.0),
}
THUMB_BASE = (0.0215, -0.0075, 0.0135)
THUMB_DIRS = ((0.56, -0.36, 0.74), (0.46, -0.34, 0.82), (0.40, -0.30, 0.87))
THUMB_LEN = (0.0440, 0.0335, 0.0285)
THUMB_R = (0.0128, 0.0106, 0.0096, 0.0088)

BONES_ORDER = []


def _v(t):
    return Vector(t)


def finger_chain(name):
    """Rest-pose joint positions for a finger: knuckle, PIP, DIP, tip."""
    base, segs, r0, r1, splay = FINGERS[name]
    d = Vector((math.sin(math.radians(splay)), 0.0, math.cos(math.radians(splay))))
    pts = [Vector(base)]
    for L in segs:
        pts.append(pts[-1] + d * L)
    return pts, r0, r1


def thumb_chain():
    pts = [Vector(THUMB_BASE)]
    for d, L in zip(THUMB_DIRS, THUMB_LEN):
        pts.append(pts[-1] + Vector(d).normalized() * L)
    return pts


# ------------------------------------------------------------------ geometry helpers
def _superellipsoid(bm, center, half, e=0.35, segs=32, rings=24, taper=0.0, arch=0.0):
    """Rounded box via a superellipsoid mapping of a UV sphere. taper narrows x toward -z; arch bulges +y."""
    geom = bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=rings, radius=1.0)
    for v in geom["verts"]:
        x, y, z = v.co
        f = lambda a: math.copysign(abs(a) ** e, a)
        fx, fy, fz = f(x), f(y), f(z)
        fx *= 1.0 - taper * (1.0 - (fz + 1.0) * 0.5)
        if fy > 0:
            fy *= 1.0 + arch * (1.0 - fx * fx)
        v.co = Vector((center[0] + half[0] * fx, center[1] + half[1] * fy, center[2] + half[2] * fz))


def _ellipsoid(bm, center, half, segs=24, rings=16):
    geom = bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=rings, radius=1.0)
    for v in geom["verts"]:
        x, y, z = v.co
        v.co = Vector((center[0] + half[0] * x, center[1] + half[1] * y, center[2] + half[2] * z))


def _tube(bm, pts, radii, flat=0.92, segs=20, cap=True):
    """Tube through pts with per-point radius; spheres at the ends (round finger tips)."""
    rings = []
    n = len(pts)
    for i, p in enumerate(pts):
        t = (pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)]).normalized()
        a = Vector((0, 1, 0)) if abs(t.y) < 0.9 else Vector((1, 0, 0))
        u = t.cross(a).normalized()
        w = t.cross(u).normalized()
        ring = []
        for k in range(segs):
            th = 2 * math.pi * k / segs
            off = u * math.cos(th) * radii[i] + w * math.sin(th) * radii[i] * flat
            ring.append(bm.verts.new(p + off))
        rings.append(ring)
    for i in range(n - 1):
        for k in range(segs):
            a, b = rings[i][k], rings[i][(k + 1) % segs]
            c, d = rings[i + 1][(k + 1) % segs], rings[i + 1][k]
            bm.faces.new((a, b, c, d))
    if cap:
        for i in (0, n - 1):
            g = bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=12, radius=radii[i])
            for v in g["verts"]:
                v.co = Vector((v.co.x, v.co.y * flat, v.co.z)) + pts[i]


def _densify(pts, radii, per=6):
    out_p, out_r = [], []
    for i in range(len(pts) - 1):
        for k in range(per):
            f = k / per
            out_p.append(pts[i].lerp(pts[i + 1], f))
            out_r.append(radii[i] + (radii[i + 1] - radii[i]) * f)
    out_p.append(pts[-1]); out_r.append(radii[-1])
    return out_p, out_r


def build_mesh(voxel=0.0011):
    """All parts in one bmesh, fused by voxel remesh into a single organic surface."""
    bm = bmesh.new()
    # palm: rounded box, wider at the knuckles, a little thicker on the thumb side
    _superellipsoid(bm, (0.0012, 0.0, 0.049), (0.0360, 0.0118, 0.0450), e=0.62, segs=40, rings=30, taper=0.2, arch=0.35)
    # knuckle ridge (back of the hand) and the pads under the knuckles (palm side)
    _ellipsoid(bm, (0.0005, 0.0015, 0.086), (0.040, 0.0112, 0.0105))
    # thenar (ball of the thumb) and hypothenar
    _ellipsoid(bm, (0.0205, -0.0085, 0.036), (0.0165, 0.0118, 0.027))
    _ellipsoid(bm, (-0.0230, -0.0060, 0.038), (0.0115, 0.0095, 0.030))
    # wrist / forearm going into the sleeve
    bm_w = []
    wrist_pts = [Vector((0.0, 0.0, z)) for z in (0.012, -0.005, -0.03, -0.07, -0.12)]
    _tube(bm, wrist_pts, [0.0300, 0.0285, 0.0280, 0.0290, 0.0305], flat=0.70, segs=28, cap=False)
    # fingers
    for name in FINGERS:
        pts, r0, r1 = finger_chain(name)
        start = pts[0] + (pts[0] - pts[1]).normalized() * 0.012     # root the tube inside the palm
        chain = [start] + pts
        radii = [r0 * 1.05, r0, r0 * 0.94 + r1 * 0.06, r0 * 0.5 + r1 * 0.5, r1]
        # slight bulge at the joints
        p, r = _densify(chain, radii, per=6)
        for i in range(len(p)):
            for j in (6, 12, 18):
                if abs(i - j) <= 1 and j < len(p):
                    r[i] *= 1.0 + (0.035 if i == j else 0.015)
        _tube(bm, p, r, flat=0.9, segs=18)
    # thumb
    tp = thumb_chain()
    chain = [tp[0] + (tp[0] - tp[1]).normalized() * 0.006] + tp
    p, r = _densify(chain, [THUMB_R[0] * 1.1, *THUMB_R], per=6)
    _tube(bm, p, r, flat=0.86, segs=18)

    me = bpy.data.meshes.new("HandMesh")
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new("Hand", me)
    bpy.context.scene.collection.objects.link(ob)
    # fuse
    rm = ob.modifiers.new("Remesh", "REMESH")
    rm.mode = "VOXEL"; rm.voxel_size = voxel; rm.adaptivity = 0.0
    sm = ob.modifiers.new("Smooth", "SMOOTH")
    sm.factor = 0.8; sm.iterations = 6
    _apply_all(ob)
    for f in ob.data.polygons:
        f.use_smooth = True
    return ob


def _apply_all(ob):
    bpy.context.view_layer.objects.active = ob
    for o in bpy.context.view_layer.objects:
        o.select_set(o == ob)
    for m in list(ob.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)


# ------------------------------------------------------------------ armature
def build_armature():
    arm_data = bpy.data.armatures.new("HandRig")
    arm = bpy.data.objects.new("HandRig", arm_data)
    bpy.context.scene.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    for o in bpy.context.view_layer.objects:
        o.select_set(o == arm)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones

    def bone(name, head, tail, parent=None, roll_to=(0, -1, 0)):
        b = eb.new(name)
        b.head = Vector(head); b.tail = Vector(tail)
        b.align_roll(Vector(roll_to))
        if parent:
            b.parent = eb[parent]
        return b

    bone("forearm", (0, 0, -0.12), (0, 0, 0.0))
    bone("palm", (0, 0, 0.0), (0, 0, 0.082), "forearm")
    for name in FINGERS:
        pts, _, _ = finger_chain(name)
        par = "palm"
        for i in range(3):
            bn = f"{name}.{i}"
            bone(bn, pts[i], pts[i + 1], par)
            par = bn
    tp = thumb_chain()
    par = "palm"
    for i in range(3):
        bn = f"thumb.{i}"
        bone(bn, tp[i], tp[i + 1], par, roll_to=(-0.75, -0.65, 0.1))
        par = bn
    bpy.ops.object.mode_set(mode="OBJECT")
    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
    return arm


def skin(mesh, arm):
    """Weights from distance to each bone segment, penalising vertices 'behind' a finger bone's head so the palm
    keeps its shape when the fingers curl. Palm distance is measured to a rectangle, not a line."""
    vs = np.array([v.co[:] for v in mesh.data.vertices])
    bones = [b for b in arm.data.bones]
    D = np.zeros((len(vs), len(bones)))
    for j, b in enumerate(bones):
        h = np.array(b.head_local[:]); t = np.array(b.tail_local[:])
        if b.name == "palm":
            q = vs.copy()
            q[:, 0] = np.clip(q[:, 0], -0.027, 0.028)
            q[:, 1] = 0.0
            q[:, 2] = np.clip(q[:, 2], 0.004, 0.080)
            D[:, j] = np.linalg.norm(vs - q, axis=1)
            continue
        d = t - h; L2 = d @ d; L = math.sqrt(L2)
        tt = ((vs - h) @ d) / L2
        c = np.clip(tt, 0, 1)
        proj = h + c[:, None] * d
        dist = np.linalg.norm(vs - proj, axis=1)
        before = np.maximum(0, -tt) * L
        after = np.maximum(0, tt - 1) * L
        is_finger = "." in b.name
        kb = 2.2 if is_finger and b.name.endswith(".0") else (1.0 if is_finger else 0.4)
        D[:, j] = dist + kb * before + 1.0 * after
    W = 1.0 / (D + 1e-4) ** 6
    # keep the 3 strongest influences
    idx = np.argsort(-W, axis=1)[:, :3]
    groups = {b.name: mesh.vertex_groups.new(name=b.name) for b in bones}
    for i in range(len(vs)):
        ws = W[i, idx[i]]
        ws = ws / ws.sum()
        for k, j in enumerate(idx[i]):
            if ws[k] > 0.01:
                groups[bones[j].name].add([i], float(ws[k]), "REPLACE")
    mesh.parent = arm
    md = mesh.modifiers.new("Armature", "ARMATURE")
    md.object = arm


# ------------------------------------------------------------------ nails + sleeve
def add_nails(arm, mat):
    nails = []
    for name in list(FINGERS) + ["thumb"]:
        b = arm.data.bones[f"{name}.2"]
        L = b.length
        r = FINGERS[name][3] if name != "thumb" else THUMB_R[3]
        bm = bmesh.new()
        _superellipsoid(bm, (0, 0, 0), (r * 0.78, r * 0.2, L * 0.36), e=0.55, segs=20, rings=12)
        me = bpy.data.meshes.new(f"nail_{name}"); bm.to_mesh(me); bm.free()
        ob = bpy.data.objects.new(f"nail_{name}", me)
        bpy.context.scene.collection.objects.link(ob)
        for f in me.polygons:
            f.use_smooth = True
        ob.parent = arm; ob.parent_type = "BONE"; ob.parent_bone = b.name
        # bone space: Y along the bone, Z toward the palm → the nail sits on -Z (back of the finger)
        # (parent_type BONE places the child at the bone's tail; move back along -Y)
        ob.matrix_parent_inverse = Matrix.Identity(4)
        ob.location = (0, -L * 0.44, -r * 0.86)
        ob.rotation_euler = (math.radians(-90) + math.radians(4), 0, 0)
        ob.data.materials.append(mat)
        nails.append(ob)
    return nails


def build_sleeve(arm, mat, length=0.24, radius=0.044, seed=3):
    """Gojo's uniform sleeve: a loose dark cuff with soft folds, following the forearm bone."""
    bm = bmesh.new()
    segs, rings = 48, 26
    rng = np.random.default_rng(seed)
    ph = rng.uniform(0, 6.283, 6)
    verts = []
    for i in range(rings):
        f = i / (rings - 1)
        z = -0.006 - f * length
        rr = radius * (1.0 + 0.10 * (1 - f) ** 3)          # slight flare at the cuff
        ring = []
        for k in range(segs):
            th = 2 * math.pi * k / segs
            fold = 0.07 * math.sin(th * 5 + ph[0] + f * 3.0) + 0.05 * math.sin(th * 3 + ph[1] - f * 5.0) \
                + 0.035 * math.sin(th * 9 + ph[2] + f * 11.0)
            fold *= 0.4 + 0.6 * math.sin(math.pi * min(1.0, f * 1.6))
            r = rr * (1.0 + fold)
            ring.append(bm.verts.new((math.cos(th) * r, math.sin(th) * r * 0.86, z)))
        verts.append(ring)
    for i in range(rings - 1):
        for k in range(segs):
            bm.faces.new((verts[i][k], verts[i][(k + 1) % segs], verts[i + 1][(k + 1) % segs], verts[i + 1][k]))
    # thickness at the cuff opening so it doesn't look paper thin
    me = bpy.data.meshes.new("Sleeve"); bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new("Sleeve", me)
    bpy.context.scene.collection.objects.link(ob)
    so = ob.modifiers.new("Solid", "SOLIDIFY"); so.thickness = 0.004; so.offset = 1.0
    sub = ob.modifiers.new("Sub", "SUBSURF"); sub.levels = 1; sub.render_levels = 2
    for f in me.polygons:
        f.use_smooth = True
    ob.parent = arm
    ob.data.materials.append(mat)
    return ob


# ------------------------------------------------------------------ materials
def toon_material(name, **params):
    """Emission material driven by osl/toon.osl. Returns (material, script node)."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    sc = nt.nodes.new("ShaderNodeScript")
    sc.mode = "EXTERNAL"; sc.filepath = os.path.join(OSL_DIR, "toon.osl"); sc.update()
    em = nt.nodes.new("ShaderNodeEmission")
    # contact shadows in the creases (curled fingers, thumb over the palm) — ink-wash depth for a flat toon
    ao = nt.nodes.new("ShaderNodeAmbientOcclusion")
    ao.samples = 12
    ao.inputs["Distance"].default_value = 0.012
    ao.only_local = True
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.inputs["From Min"].default_value = 0.35; mr.inputs["From Max"].default_value = 0.85
    mr.inputs["To Min"].default_value = 0.42; mr.inputs["To Max"].default_value = 1.0
    nt.links.new(ao.outputs["AO"], mr.inputs["Value"])
    mul = nt.nodes.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"
    mul.inputs["Factor"].default_value = 1.0
    nt.links.new(sc.outputs["Out"], mul.inputs[6])
    nt.links.new(mr.outputs["Result"], mul.inputs[7])
    nt.links.new(mul.outputs[2], em.inputs["Color"])
    nt.links.new(em.outputs[0], out.inputs["Surface"])
    for k, v in params.items():
        _set(sc.inputs[k], v)
    return mat, sc


def _set(sock, v):
    if sock.type == "RGBA" and len(v) == 3:
        v = (*v, 1.0)
    sock.default_value = v


def outline_material(color=(0.02, 0.02, 0.03)):
    """Inverted-hull ink: black where the shell's inside faces the camera, transparent elsewhere."""
    mat = bpy.data.materials.new("Ink")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (*color, 1)
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(geo.outputs["Backfacing"], mix.inputs["Fac"])
    nt.links.new(em.outputs[0], mix.inputs[1])
    nt.links.new(tr.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    return mat


def add_outline(ob, ink, thickness=0.0011):
    """Solidify shell with flipped normals using the ink material (appended as the last slot)."""
    ob.data.materials.append(ink)
    so = ob.modifiers.new("Outline", "SOLIDIFY")
    so.thickness = thickness
    so.offset = 1.0
    so.use_flip_normals = True
    so.use_rim = False
    so.material_offset = len(ob.data.materials) - 1
    return so


# ------------------------------------------------------------------ poses
# Euler XYZ in each bone's local space, degrees: X = curl toward the palm, Y = twist, Z = side-to-side.
# "splay" values are absolute (+ = toward the thumb); the rest splay is subtracted automatically.
POSES = {
    "open": {},
    # Red: index and middle raised together, ring and little finger folded, thumb pinning them
    "sword": {
        "index": [(3, 0, 3.2), (4, 0, 0), (3, 0, 0)],
        "middle": [(2, 0, 0.6), (3, 0, 0), (3, 0, 0)],
        "ring": [(82, 0, -3), (98, 0, 0), (52, 0, 0)],
        "pinky": [(86, 0, -10), (92, 0, 0), (50, 0, 0)],
        "thumb": [(28, -10, 34), (32, 0, 0), (28, 0, 0)],
    },
    # Unlimited Void (帝釈天印): index and middle fingers crossed — the middle finger wraps over the front of the index,
    # ring and little finger folded, thumb pressing them down
    "cross": {
        "index": [(-8, 0, -5), (2, 0, 0), (2, 0, 0)],
        "middle": [(24, -8, 31), (8, 0, 0), (6, 0, 0)],
        "ring": [(84, 0, -3), (96, 0, 0), (52, 0, 0)],
        "pinky": [(88, 0, -9), (90, 0, 0), (50, 0, 0)],
        "thumb": [(30, -12, 36), (34, 0, 0), (26, 0, 0)],
    },
    # relaxed, slightly curled
    "relaxed": {
        "index": [(14, 0, 7), (18, 0, 0), (10, 0, 0)],
        "middle": [(16, 0, 1), (22, 0, 0), (12, 0, 0)],
        "ring": [(20, 0, -4), (26, 0, 0), (14, 0, 0)],
        "pinky": [(24, 0, -10), (30, 0, 0), (16, 0, 0)],
        "thumb": [(10, 0, 6), (10, 0, 0), (8, 0, 0)],
    },
}


def set_pose(arm, pose, frame=None, blend=1.0):
    p = POSES[pose] if isinstance(pose, str) else pose
    for name in list(FINGERS) + ["thumb"]:
        rots = p.get(name, [(0, 0, 0)] * 3)
        for i in range(3):
            pb = arm.pose.bones[f"{name}.{i}"]
            x, y, z = rots[i]
            if name != "thumb" and i == 0:
                # absolute splay (+ toward the thumb) → local Z rotation relative to the rest fan
                z = -(z - FINGERS[name][4])
            pb.rotation_euler = Euler((math.radians(x * blend), math.radians(y * blend), math.radians(z * blend)), "XYZ")
            if frame is not None:
                pb.keyframe_insert("rotation_euler", frame=frame)
    for extra in ("palm", "forearm"):
        if extra in p:
            pb = arm.pose.bones[extra]
            x, y, z = p[extra]
            pb.rotation_euler = Euler((math.radians(x * blend), math.radians(y * blend), math.radians(z * blend)), "XYZ")
            if frame is not None:
                pb.keyframe_insert("rotation_euler", frame=frame)


def build_hand(skin_params=None, sleeve_params=None, nail_params=None, outline=0.0013, voxel=0.0011):
    """Returns dict(arm, hand, sleeve, nails, scripts) — scripts are the toon script nodes (for animating lights)."""
    sp = dict(Base=(0.88, 0.87, 0.88), Shade=(0.56, 0.55, 0.59), Deep=(0.33, 0.32, 0.37))
    sp.update(skin_params or {})
    skin_mat, s1 = toon_material("Skin", **sp)
    np_ = dict(Base=(0.95, 0.94, 0.95), Shade=(0.66, 0.65, 0.69), Deep=(0.45, 0.44, 0.49))
    np_.update(nail_params or {})
    nail_mat, s2 = toon_material("Nail", **np_)
    slp = dict(Base=(0.13, 0.13, 0.16), Shade=(0.075, 0.075, 0.095), Deep=(0.04, 0.04, 0.055), Band1=0.45, Band2=0.0)
    slp.update(sleeve_params or {})
    sleeve_mat, s3 = toon_material("Sleeve", **slp)
    ink = outline_material()

    hand = build_mesh(voxel=voxel)
    hand.data.materials.append(skin_mat)
    arm = build_armature()
    skin(hand, arm)
    nails = add_nails(arm, nail_mat)
    sleeve = build_sleeve(arm, sleeve_mat)
    if outline > 0:
        add_outline(hand, ink, outline)
        add_outline(sleeve, ink, outline * 1.3)
        for n in nails:
            add_outline(n, ink, outline * 0.6)
    return {"arm": arm, "hand": hand, "sleeve": sleeve, "nails": nails, "scripts": [s1, s2, s3], "ink": ink}


def set_light(scripts, frame=None, **params):
    """Set (and optionally keyframe) toon parameters on every hand material at once."""
    for s in scripts:
        for k, v in params.items():
            _set(s.inputs[k], v)
            if frame is not None:
                s.inputs[k].keyframe_insert("default_value", frame=frame)
