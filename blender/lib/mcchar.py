"""A Minecraft player model in Blender that matches the game exactly — for authoring and previewing animations.

* Cuboids use Minecraft's box UV layout (ModelPart.Cuboid) on a real 64x64 skin, inner and outer layers, wide or
  slim arms, pixel-sharp.
* Pivots and rotations follow ModelPart: translate(pivot) then Rz(roll)·Ry(yaw)·Rx(pitch) in model space
  (pixels, y down, -z forward). A root object maps model space into Blender (x, z, -y) / 16 with the feet on z = 0.
* Bends reproduce bendy-lib (what the mod uses in game): every face is split into 1-pixel quads and each vertex is
  moved with the same formula (BendableCuboid.applyBend), so a preview bend looks like the in-game bend.
* Blocky fingers come from assets/gojolimitless/hands/hands.json, the same file the mod reads.

Pose format (all angles radians unless noted), per part name in PARTS:
    {"rot": (pitch, yaw, roll), "pos": (dx, dy, dz) pixels added to the default pivot, "bend": (axis, angle)}
plus "root": {"rot": (x, y, z), "pos": (x, y, z) pixels} — the whole-body transform (playerAnimator "body"),
and "hands": {"right": (shape, weight), "left": (shape, weight)}.
"""
import bpy, bmesh, math, os, json
import numpy as np
from mathutils import Matrix, Vector, Euler

LIB = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.dirname(os.path.dirname(LIB))
HANDS_JSON = os.path.join(ROOT_DIR, "mod", "src", "main", "resources", "assets", "gojolimitless", "hands", "hands.json")

# name: (pivot, box from, box size, uv, bend direction ('up' | 'down' | None), outer uv)
WIDE = {
    "head":      ((0, 0, 0),     (-4, -8, -4), (8, 8, 8),  (0, 0),   None,   (32, 0)),
    "body":      ((0, 0, 0),     (-4, 0, -2),  (8, 12, 4), (16, 16), "down", (16, 32)),
    "right_arm": ((-5, 2, 0),    (-3, -2, -2), (4, 12, 4), (40, 16), "up",   (40, 32)),
    "left_arm":  ((5, 2, 0),     (-1, -2, -2), (4, 12, 4), (32, 48), "up",   (48, 48)),
    "right_leg": ((-1.9, 12, 0), (-2, 0, -2),  (4, 12, 4), (0, 16),  "up",   (0, 32)),
    "left_leg":  ((1.9, 12, 0),  (-2, 0, -2),  (4, 12, 4), (16, 48), "up",   (0, 48)),
}
SLIM_ARMS = {
    "right_arm": ((-5, 2.5, 0), (-2, -2, -2), (3, 12, 4), (40, 16), "up", (40, 32)),
    "left_arm":  ((5, 2.5, 0),  (-1, -2, -2), (3, 12, 4), (32, 48), "up", (48, 48)),
}
INFLATE = {"head": 0.5}          # outer layer inflation (hat 0.5, the rest 0.25)
PARTS = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]
FINGERS = ["index", "middle", "ring", "pinky", "thumb"]


# ------------------------------------------------------------------ skin material
def skin_material(path, name="Skin"):
    img = bpy.data.images.load(path, check_existing=True)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.blend_method = "CLIP" if hasattr(mat, "blend_method") else None
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    tex = nt.nodes.new("ShaderNodeTexImage"); tex.image = img; tex.interpolation = "Closest"
    # Minecraft-style lighting: flat colour shaded by a fixed directional term (like the game's entity lighting)
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    dot = nt.nodes.new("ShaderNodeVectorMath"); dot.operation = "DOT_PRODUCT"
    dot.inputs[1].default_value = (0.3, -0.45, 0.84)
    nt.links.new(geo.outputs["Normal"], dot.inputs[0])
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.inputs["From Min"].default_value = -1.0; mr.inputs["From Max"].default_value = 1.0
    mr.inputs["To Min"].default_value = 0.52; mr.inputs["To Max"].default_value = 1.0
    nt.links.new(dot.outputs["Value"], mr.inputs["Value"])
    mul = nt.nodes.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"
    mul.inputs["Factor"].default_value = 1.0
    nt.links.new(tex.outputs["Color"], mul.inputs[6]); nt.links.new(mr.outputs["Result"], mul.inputs[7])
    em = nt.nodes.new("ShaderNodeEmission")
    nt.links.new(mul.outputs[2], em.inputs["Color"])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    gt = nt.nodes.new("ShaderNodeMath"); gt.operation = "GREATER_THAN"; gt.inputs[1].default_value = 0.1
    nt.links.new(tex.outputs["Alpha"], gt.inputs[0])
    nt.links.new(gt.outputs[0], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    return mat, img


def skin_pixel(img, u, v):
    """RGBA of texel (u, v) (top-left origin)."""
    w, h = img.size
    px = img.pixels
    i = ((h - 1 - v) * w + u) * 4
    return tuple(px[i:i + 4])


# ------------------------------------------------------------------ cuboid with Minecraft UVs, subdivided per pixel
def _face_grid(bm, uvl, corners, u1, v1, u2, v2, tw, th, verts_out):
    """Quad face from 3 corners (like bendy-lib's createAndAddQuads), split into 1-texel quads."""
    e0, e1, e2 = (Vector(c) for c in corners)
    du = 1 if u2 < u1 else -1
    dv = 1 if v1 < v2 else -1

    def pos(u, v):
        a = e0 + (e1 - e0) * ((u - u2) / (u1 - u2)) + (e2 - e0) * ((v - v1) / (v2 - v1))
        key = (round(a.x, 4), round(a.y, 4), round(a.z, 4))
        if key not in verts_out:
            verts_out[key] = bm.verts.new(a)
        return verts_out[key]

    u = u2
    while u != u1:
        v = v1
        while v != v2:
            uu, vv = u + du, v + dv
            q = [pos(u, v), pos(uu, v), pos(uu, vv), pos(u, vv)]
            uvs = [(u, v), (uu, v), (uu, vv), (u, vv)]
            try:
                f = bm.faces.new(q)
            except ValueError:
                v = vv
                continue
            for loop, (a, b) in zip(f.loops, uvs):
                loop[uvl].uv = (a / tw, 1.0 - b / th)
            v = vv
        u = uu


def cuboid_mesh(name, frm, size, uv, inflate=0.0, tw=64, th=64):
    x, y, z = frm
    sx, sy, sz = size
    pmin = (x - inflate, y - inflate, z - inflate)
    pmax = (x + sx + inflate, y + sy + inflate, z + sz + inflate)
    v1 = (pmin[0], pmin[1], pmin[2]); v2 = (pmax[0], pmin[1], pmin[2]); v3 = (pmax[0], pmax[1], pmin[2])
    v4 = (pmin[0], pmax[1], pmin[2]); v5 = (pmin[0], pmin[1], pmax[2]); v6 = (pmax[0], pmin[1], pmax[2])
    v7 = (pmax[0], pmax[1], pmax[2]); v8 = (pmin[0], pmax[1], pmax[2])
    u, v = uv
    j, k, l = u, u + sz, u + sz + sx
    m, n, o = u + sz + sx + sx, u + sz + sx + sz, u + sz + sx + sz + sx
    p, q, r = v, v + sz, v + sz + sy
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UV")
    verts = {}
    _face_grid(bm, uvl, (v6, v5, v2), k, p, l, q, tw, th, verts)
    _face_grid(bm, uvl, (v3, v4, v7), l, q, m, p, tw, th, verts)
    _face_grid(bm, uvl, (v1, v5, v4), j, q, k, r, tw, th, verts)
    _face_grid(bm, uvl, (v2, v1, v3), k, q, l, r, tw, th, verts)
    _face_grid(bm, uvl, (v6, v2, v7), l, q, n, r, tw, th, verts)
    _face_grid(bm, uvl, (v5, v6, v8), n, q, o, r, tw, th, verts)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    return me


def box_mesh(name, center, size, color):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for vv in bm.verts:
        vv.co = Vector((center[0] + vv.co.x * size[0], center[1] + vv.co.y * size[1], center[2] + vv.co.z * size[2]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    return me


# ------------------------------------------------------------------ the character
class McChar:
    def __init__(self, skin_path, slim=False, name="Player", outer=True, collection=None):
        self.slim = slim
        self.defs = dict(WIDE)
        if slim:
            self.defs.update(SLIM_ARMS)
        self.mat, self.img = skin_material(skin_path, name + "Skin")
        sc = bpy.context.scene
        coll = collection or sc.collection
        self.root = bpy.data.objects.new(name + "_root", None)
        coll.objects.link(self.root)
        # where the whole character stands in the scene (identity = feet at the origin, facing -y)
        self.place = Matrix.Identity(4)
        # model (x, y, z) px → blender (x, z, -y) / 16, feet at z = 0
        self.root.matrix_world = Matrix.Translation((0, 0, 24 / 16)) @ Matrix.Rotation(-math.pi / 2, 4, "X") @ Matrix.Scale(1 / 16, 4)
        self.pivots, self.meshes, self.rest = {}, {}, {}
        for pn in PARTS:
            pivot, frm, size, uv, bend_dir, ouv = self.defs[pn]
            piv = bpy.data.objects.new(f"{name}_{pn}", None)
            coll.objects.link(piv)
            piv.parent = self.root
            piv.location = pivot
            piv.rotation_mode = "XYZ"
            self.pivots[pn] = piv
            parts = []
            me = cuboid_mesh(f"{name}_{pn}_in", frm, size, uv)
            parts.append(me)
            if outer:
                me2 = cuboid_mesh(f"{name}_{pn}_out", frm, size, ouv, inflate=INFLATE.get(pn, 0.25))
                parts.append(me2)
            obs = []
            for me in parts:
                me.materials.append(self.mat)
                ob = bpy.data.objects.new(me.name, me)
                coll.objects.link(ob)
                ob.parent = piv
                obs.append(ob)
                self.rest[ob.name] = np.array([vv.co[:] for vv in me.vertices])
            self.meshes[pn] = obs
        with open(HANDS_JSON) as f:
            self.hands_def = json.load(f)
        self.fingers = {"right": self._build_fingers("right"), "left": self._build_fingers("left")}

    # ---------------- bends (bendy-lib's BendableCuboid.applyBend, per vertex)
    def _bend_part(self, pn, axis, value):
        pivot, frm, size, uv, bend_dir, ouv = self.defs[pn]
        if bend_dir is None:
            return
        d = np.array((0.0, 1.0, 0.0)) if bend_dir == "up" else np.array((0.0, -1.0, 0.0))
        # axis in the part's frame: (cos a, 0, sin a) rotated by the direction's quaternion (DOWN = rotX(pi))
        ax = np.array((math.cos(axis), 0.0, math.sin(axis)))
        if bend_dir == "down":
            ax = np.array((ax[0], -ax[1], -ax[2]))
        fix = np.array((frm[0] + size[0] / 2, frm[1] + size[1] / 2, frm[2] + size[2] / 2))
        R = np.array(Matrix.Rotation(value, 3, Vector(ax))) if abs(value) > 1e-4 else np.eye(3)
        bend_n = np.cross(d, ax)
        for ob in self.meshes[pn]:
            infl = INFLATE.get(pn, 0.25) if ob.name.endswith("_out") else 0.0
            pmin = np.array(frm) - infl
            pmax = np.array(frm) + np.array(size) + infl
            v1, v7 = pmin, pmax
            inverted = bend_dir == "up"
            base_pt, other_pt = (v7, v1) if inverted else (v1, v7)
            half = (np.dot(d, v7) - np.dot(d, v1)) / 2
            rest = self.rest[ob.name]
            out = rest.copy()
            if abs(value) > 1e-4:
                dist_bend = (rest - fix) @ bend_n
                if inverted:
                    dist_bend = -dist_bend
                dist_base = (rest - base_pt) @ d
                dist_other = (rest - other_pt) @ d
                s = math.tan(value / 2) * dist_bend
                near_base = np.abs(dist_base) < np.abs(dist_other)
                shift_base = -dist_base / half * s
                shift_other = -dist_other / half * s
                p = rest + np.where(near_base[:, None], shift_base[:, None], shift_other[:, None]) * d
                pr = (p - fix) @ R.T + fix
                out = np.where(near_base[:, None], pr, p)
            me = ob.data
            me.vertices.foreach_set("co", out.astype(np.float32).ravel())
            me.update()
        return R, fix

    def hand_matrix(self, side, axis, value):
        """Matrix (arm-local, pixels) of the hand end after the bend: fingers are built in this frame."""
        pn = f"{side}_arm"
        pivot, frm, size, uv, bend_dir, ouv = self.defs[pn]
        fix = Vector((frm[0] + size[0] / 2, frm[1] + size[1] / 2, frm[2] + size[2] / 2))
        end = Vector((frm[0] + size[0] / 2, frm[1] + size[1], frm[2] + size[2] / 2))
        M = Matrix.Translation(end)
        if abs(value) > 1e-4:
            ax = Vector((math.cos(axis), 0.0, math.sin(axis)))
            M = Matrix.Translation(fix) @ Matrix.Rotation(value, 4, ax) @ Matrix.Translation(-fix) @ M
        return M

    def point(self, side, beyond_px, pose):
        """World position `beyond_px` pixels past the end of the arm (the fingertips at ~3.5), after apply(pose)."""
        b = pose.get(f"{side}_arm", {}).get("bend", (0.0, 0.0))
        bpy.context.view_layer.update()
        return self.pivots[f"{side}_arm"].matrix_world @ self.hand_matrix(side, b[0], b[1]) @ Vector((0, beyond_px, 0))

    # ---------------- fingers
    def _build_fingers(self, side):
        pn = f"{side}_arm"
        arm_w = self.defs[pn][2][0]
        mir = -1.0 if side == "left" else 1.0
        fingers = {}
        # finger colour: the palm-side face of the arm, bottom row (same texel as FingerFeature.texel)
        right = side == "right"
        u0, v0 = (40, 16) if right else (32, 48)
        sx, sy, sz = self.defs[pn][2]
        for fname in FINGERS:
            fd = self.hands_def["fingers"][fname]
            fu = u0 + sz + sx if right else u0
            col_i = max(0, min(sz - 1, int(math.floor(fd["base"][2] + sz / 2))))
            if not right:
                col_i = sz - 1 - col_i
            texel = (int(fu + col_i), int(v0 + sz + sy - 1))
            col = skin_pixel(self.img, *texel)
            mat = bpy.data.materials.new(f"{side}_{fname}")
            mat.use_nodes = True
            nt = mat.node_tree
            for n in list(nt.nodes):
                nt.nodes.remove(n)
            out = nt.nodes.new("ShaderNodeOutputMaterial")
            geo = nt.nodes.new("ShaderNodeNewGeometry")
            dot = nt.nodes.new("ShaderNodeVectorMath"); dot.operation = "DOT_PRODUCT"
            dot.inputs[1].default_value = (0.3, -0.45, 0.84)
            nt.links.new(geo.outputs["Normal"], dot.inputs[0])
            mr = nt.nodes.new("ShaderNodeMapRange")
            mr.inputs["From Min"].default_value = -1.0; mr.inputs["From Max"].default_value = 1.0
            mr.inputs["To Min"].default_value = 0.52; mr.inputs["To Max"].default_value = 1.0
            nt.links.new(dot.outputs["Value"], mr.inputs["Value"])
            rgb = nt.nodes.new("ShaderNodeRGB"); rgb.outputs[0].default_value = (col[0], col[1], col[2], 1)
            mul = nt.nodes.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"
            mul.inputs["Factor"].default_value = 1.0
            nt.links.new(rgb.outputs[0], mul.inputs[6]); nt.links.new(mr.outputs["Result"], mul.inputs[7])
            em = nt.nodes.new("ShaderNodeEmission")
            nt.links.new(mul.outputs[2], em.inputs["Color"]); nt.links.new(em.outputs[0], out.inputs["Surface"])
            segs = []
            for si, L in enumerate(fd["segments"]):
                w, dz = fd["size"]
                w *= arm_w / 4.0
                me = box_mesh(f"{side}_{fname}_{si}", (0, L / 2, 0), (w, L, dz), col)
                me.materials.append(mat)
                ob = bpy.data.objects.new(me.name, me)
                bpy.context.scene.collection.objects.link(ob)
                segs.append(ob)
            fingers[fname] = segs
        return fingers

    def _blend(self, a, b, k):
        shapes = self.hands_def["shapes"]
        sa, sb = shapes.get(a, shapes["relaxed"]), shapes.get(b, shapes["relaxed"])
        out = {}
        for fname in FINGERS:
            x, y = list(sa[fname]) + [0] * (4 - len(sa[fname])), list(sb[fname]) + [0] * (4 - len(sb[fname]))
            out[fname] = [x[i] + (y[i] - x[i]) * k for i in range(4)]
        return out

    def _pose_fingers(self, side, shape, weight, axis, value, blended=None):
        pn = f"{side}_arm"
        arm_w = self.defs[pn][2][0]
        mir = -1.0 if side == "left" else 1.0
        H = self.hand_matrix(side, axis, value)
        piv = self.pivots[pn]
        shapes = self.hands_def["shapes"]
        sh = blended if blended is not None else shapes.get(shape, shapes["relaxed"])
        vis = weight > 0.02
        for fname in FINGERS:
            fd = self.hands_def["fingers"][fname]
            a = sh[fname]
            c1, c2, spl = (a[0], a[1], a[2])
            opp = a[3] if len(a) > 3 else 0.0
            bx, by, bz = fd["base"]
            bx *= arm_w / 4.0
            pitch0, roll0 = fd["rest"]
            # finger frame at its base: y along the finger; curl rotates the tip toward the palm (+x)
            B = H @ Matrix.Translation((bx * mir, by, bz))
            if shape == "seal" and fname == "middle":
                # the middle finger crosses in front of the index (Taishakuten seal)
                B = B @ Matrix.Translation((0, 0, -self.hands_def.get("seal_cross_depth", 0.5)))
            B = B @ Matrix.Rotation(math.radians(-pitch0), 4, "X") @ Matrix.Rotation(math.radians(-roll0) * mir, 4, "Z")
            if fname == "thumb":
                B = B @ Matrix.Rotation(math.radians(opp), 4, "Y" ) if mir > 0 else B @ Matrix.Rotation(math.radians(-opp), 4, "Y")
            B = B @ Matrix.Rotation(math.radians(-spl), 4, "X")
            M1 = B @ Matrix.Rotation(math.radians(-c1) * mir, 4, "Z")
            L1 = fd["segments"][0]
            M2 = M1 @ Matrix.Translation((0, L1, 0)) @ Matrix.Rotation(math.radians(-c2) * mir, 4, "Z")
            for seg, M in zip(self.fingers[side][fname], (M1, M2)):
                seg.parent = piv
                seg.matrix_parent_inverse = Matrix.Identity(4)
                seg.matrix_basis = M @ Matrix.Scale(max(weight, 1e-3), 4)
                seg.hide_render = not vis
                seg.hide_viewport = not vis

    # ---------------- apply a pose
    def apply(self, pose):
        for pn in PARTS:
            p = pose.get(pn, {})
            piv = self.pivots[pn]
            base = self.defs[pn][0]
            dp = p.get("pos", (0, 0, 0))
            piv.location = (base[0] + dp[0], base[1] + dp[1], base[2] + dp[2])
            piv.rotation_euler = Euler(p.get("rot", (0, 0, 0)), "XYZ")
            b = p.get("bend", (0.0, 0.0))
            self._bend_part(pn, b[0], b[1])
        r = pose.get("root", {})
        rp = r.get("pos", (0, 0, 0))
        rr = r.get("rot", (0, 0, 0))
        base = Matrix.Translation((0, 0, 24 / 16)) @ Matrix.Rotation(-math.pi / 2, 4, "X") @ Matrix.Scale(1 / 16, 4)
        # playerAnimator's whole-body transform, converted to Blender (character facing -y): pos = (x left, y up,
        # z forward) px; rotation about a pivot 0.7 blocks up: pitch + leans BACK, yaw + turns left, roll + tips right (verified in game)
        ent = Matrix.Translation((rp[0] / 16, -rp[2] / 16, rp[1] / 16))
        piv = Matrix.Translation((0, 0, 0.7))
        rot = Matrix.Rotation(rr[2], 4, "Y") @ Matrix.Rotation(rr[1], 4, "Z") @ Matrix.Rotation(-rr[0], 4, "X")
        # the game draws players at 15/16 scale about the feet (PlayerEntityRenderer.scale)
        self.root.matrix_world = self.place @ Matrix.Scale(0.9375, 4) @ ent @ piv @ rot @ piv.inverted() @ base
        hands = pose.get("hands", {})
        for side in ("right", "left"):
            h = hands.get(side, ("fist", 0.0))
            if len(h) == 2:
                h = (h[0], h[0], 0.0, h[1])
            a, b2, k, w = h
            b = pose.get(f"{side}_arm", {}).get("bend", (0.0, 0.0))
            self._pose_fingers(side, a if k < 0.5 else b2, w, b[0], b[1], blended=self._blend(a, b2, k))
