"""Full-screen inserts for the remote Hollow Purple ("the nuke"), following docs/storyboard/NUKE_STORYBOARD.md.

    nuke_red_sign  — (retired: the hand close-up is now shot in-engine on the player's own model and blocky fingers)
    nuke_collide   — Blue and Red as comets streaming toward each other; they meet in a white-hot core
    nuke_implode   — the merged mass implodes to a single point on black; violet sparks hang in the dark (silent beat)
    nuke_glint     — an inverted four-point star glint on white

Run: blender -b --factory-startup -P blender/inserts/build_nuke_inserts.py -- [--only a,b] [--preview --frames 0,10,20 --scale 50]
"""
import bpy, os, sys, math, random
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import importlib
import insertlib as IL
import handrig as HR
importlib.reload(IL); importlib.reload(HR)
from mathutils import Vector, Matrix, Euler, Quaternion

O = IL.cli()


def want(name):
    return O["only"] is None or name in O["only"]


def finish(sc, name, frames, fps=30, blend="alpha"):
    IL.render_insert(sc, name, frames, fps=fps, blend=blend, preview=O["preview"], only_frames=O["frames"],
                     scale=O["scale"])


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def world_tip(arm, bone):
    return arm.matrix_world @ arm.pose.bones[bone].tail


def orient_facing(origin, axis_x, cam_pos):
    """Matrix whose X points along axis_x and Z toward the camera (as far as possible)."""
    x = axis_x.normalized()
    to_cam = (cam_pos - origin).normalized()
    z = (to_cam - x * to_cam.dot(x)).normalized()
    y = z.cross(x).normalized()
    m = Matrix((x, y, z)).transposed().to_4x4()
    m.translation = origin
    return m


# =====================================================================================================================
def red_sign():
    """1.2 s @ 30 fps. Low angle on the raised hand; Red ignites between the fingertips."""
    N = 36
    sc = IL.new_scene(1280, 720, 30, samples=O["samples"] or 20)
    cam, tgt = IL.camera(sc, lens=38)

    H = HR.build_hand(skin_params=dict(Base=(0.80, 0.82, 0.90), Shade=(0.40, 0.42, 0.51), Deep=(0.19, 0.20, 0.27),
                                       KeyDir=(-0.55, 0.35, 0.75), KeyStr=0.9, Band1=0.35, Band2=-0.1),
                      nail_params=dict(KeyDir=(-0.55, 0.35, 0.75)),
                      sleeve_params=dict(KeyDir=(-0.55, 0.35, 0.75)))
    arm = H["arm"]
    arm.rotation_euler = (math.radians(-8), math.radians(4), math.radians(-16))
    # the fingers straighten and press together as the technique ignites
    HR.set_pose(arm, "relaxed", frame=0)
    HR.set_pose(arm, "sword", frame=7)
    HR.set_pose(arm, "sword", frame=N)
    bpy.context.view_layer.update()

    sc.frame_set(12)
    tip = (world_tip(arm, "index.2") + world_tip(arm, "middle.2")) * 0.5
    orb = tip + Vector((0.004, -0.004, 0.021))

    # camera: low, from the palm/thumb side, slow push-in, a tremor as Red peaks
    IL.key_cam(cam, tgt, 0, (0.20, -0.33, 0.035), orb + Vector((-0.045, 0.0, -0.055)), lens=36)
    IL.key_cam(cam, tgt, 26, (0.165, -0.285, 0.05), orb + Vector((-0.035, 0.0, -0.045)), lens=39)
    rnd = random.Random(7)
    for f in range(27, N + 1):
        k = (f - 26) / 10
        j = 0.0022 * k
        IL.key_cam(cam, tgt, f, (0.165 - 0.0012 * (f - 26) + rnd.uniform(-j, j), -0.285 + 0.0016 * (f - 26) + rnd.uniform(-j, j),
                                 0.05 + rnd.uniform(-j, j)), orb + Vector((-0.035, 0.0, -0.045)), lens=39 + 0.1 * (f - 26))

    # backdrop: storm sky far behind, facing the camera
    bg_mat, bg, _ = IL.emission_material("Sky", "storm.osl", dict(aspect=1.7778, scale=0.8, cx=0.1, cy=0.35,
                                                                  horizon=-0.62))
    sky = IL.plane("Sky", bg_mat, size=1.0, loc=(-1.8, 3.6, 1.4), billboard_to=cam, sx=3.6, sy=2.03)

    # the orb: glow core, turbulent swirl, rays
    glow_mat, glow, _ = IL.additive_material("OrbGlow", "orb_glow.osl", dict(core_w=0.1, core_i=9.0, halo_w=0.4, halo_i=1.1))
    g = IL.plane("OrbGlow", glow_mat, size=0.001, loc=orb, billboard_to=cam)
    sw_mat, swirl, sw_em = IL.additive_material("Swirl", "red_swirl.osl", dict(objspace=1, gain=1.4, rays=11.0))
    piv = bpy.data.objects.new("SwirlPivot", None); sc.collection.objects.link(piv); piv.location = orb
    c = piv.constraints.new("TRACK_TO"); c.target = cam; c.track_axis = "TRACK_Z"; c.up_axis = "UP_Y"
    s = IL.plane("SwirlA", sw_mat, size=1.0); s.parent = piv
    s2 = IL.plane("SwirlB", sw_mat, size=1.0); s2.parent = piv
    for f in range(0, N + 1):
        k = f / N
        # growth: a spark at 3, swelling fast after the fingers lock (7), full by ~26, then a breathing pulse
        size = 0.0 if f < 3 else 0.004 + 0.031 * smooth(3, 26, f)
        size *= 1.0 + 0.08 * math.sin(f * 1.9) * smooth(20, 30, f)
        g.scale = (size * 3.0,) * 3
        g.keyframe_insert("scale", frame=f)
        s.scale = (size * 2.3,) * 3; s.rotation_euler = (0, 0, -f * 0.21)
        s2.scale = (size * 1.7,) * 3; s2.rotation_euler = (0, 0, f * 0.33 + 1.3)
        for o in (s, s2):
            o.keyframe_insert("scale", frame=f); o.keyframe_insert("rotation_euler", frame=f)
        IL.key_socket(swirl.inputs["seed"], f, 3.0 + f * 0.09)
        IL.key_socket(glow.inputs["gain"], f, 0.0 if f < 3 else 0.6 + 0.7 * smooth(3, 26, f) + 0.25 * math.sin(f * 2.3) * smooth(18, 30, f))
        # light on the hand and the flood across the sky
        ls = 0.0 if f < 3 else 5.0 * smooth(3, 28, f) + 0.4 * math.sin(f * 2.3) * smooth(18, 30, f)
        HR.set_light(H["scripts"], frame=f, LightPos=tuple(orb), LightCol=(1.0, 0.1, 0.06), LightStr=ls,
                     LightRange=0.035 + 0.08 * smooth(3, 30, f), RimCol=(1.0, 0.28, 0.2), RimStr=1.6 * smooth(6, 26, f),
                     KeyStr=0.95 - 0.45 * smooth(8, 30, f),
                     Tint=(1.0, 1.0 - 0.28 * smooth(10, 32, f), 1.0 - 0.3 * smooth(10, 32, f)))
        IL.key_socket(bg.inputs["flood"], f, 0.9 * smooth(8, 32, f))
        IL.key_socket(bg.inputs["glow"], f, 0.35 * smooth(4, 20, f))
        IL.key_socket(bg.inputs["tm"], f, f * 0.08)

    # rays lancing out, one after another (the incantation Red's pillars of light)
    rnd = random.Random(11)
    ray_mat, ray, _ = IL.additive_material("Ray", "ray.osl", dict(objspace=1, tint=(1.0, 0.1, 0.05)))
    cam_mid = Vector((0.17, -0.29, 0.045))
    for i in range(9):
        th = rnd.uniform(0, 2 * math.pi)
        d = Vector((math.cos(th), rnd.uniform(-0.35, 0.35), math.sin(th) * 0.8 + 0.15)).normalized()
        e = bpy.data.objects.new(f"RayPivot{i}", None); sc.collection.objects.link(e)
        e.matrix_world = orient_facing(orb, d, cam_mid)
        r = IL.plane(f"Ray{i}", ray_mat, size=1.0)
        r.parent = e; r.location = (1.0, 0, 0)
        L = rnd.uniform(0.35, 1.3)
        w = rnd.uniform(0.0016, 0.0032)
        t0 = 9 + i * 2.2 + rnd.uniform(-1, 1)
        for f in range(0, N + 1):
            k = smooth(t0, t0 + 4, f)
            flick = 0.85 + 0.15 * math.sin(f * 3.1 + i)
            e.scale = (max(1e-4, L * 0.5 * k), 1.0, 1.0)
            r.scale = (1.0, w * (0.4 + 0.6 * k) * flick * (1.0 + 0.6 * smooth(26, 36, f)), 1.0)
            e.keyframe_insert("scale", frame=f); r.keyframe_insert("scale", frame=f)

    IL.cam_vignette(cam, strength=0.8)
    IL.compositor(sc, fog=(0.9, 8, 0.0), streaks=(3.0, 4, 18, 0.92, -0.35), dispersion=0.012)
    finish(sc, "nuke_red_sign", N)


# =====================================================================================================================
def ribbon_mesh(name, pts, widths, mat):
    """Flat ribbon in the XZ plane (faces the camera looking along +Y) with UV.x along the length."""
    import bmesh
    n = len(pts)
    verts, faces, uvs = [], [], []
    for i, p in enumerate(pts):
        t = (pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)])
        t.y = 0
        t.normalize()
        nrm = Vector((-t.z, 0, t.x))
        verts.append(p + nrm * widths[i]); verts.append(p - nrm * widths[i])
    for i in range(n - 1):
        faces.append((2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2))
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts], [], faces)
    uvl = me.uv_layers.new(name="UVMap")
    for poly in me.polygons:
        for li in poly.loop_indices:
            vi = me.loops[li].vertex_index
            uvl.data[li].uv = ((vi // 2) / (n - 1), float(vi % 2))
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    me.materials.append(mat)
    return ob


def filament_material(name, col, hot=(1, 1, 1), gain=1.0, dashes=7.0, fadepow=1.6):
    mat, s, em = IL.additive_material(name, "filament.osl", dict(col=col, hot=hot, gain=gain, dashes=dashes, fadepow=fadepow))
    nt = mat.node_tree
    uv = nt.nodes.new("ShaderNodeUVMap"); uv.uv_map = "UVMap"
    nt.links.new(uv.outputs["UV"], s.inputs["uv"])
    return mat, s


def comet(sc, name, col, sign, rnd, count=40):
    """A core glow with a crescent of swept-back filaments (tail toward -sign*X). Returns (root, scripts)."""
    root = bpy.data.objects.new(name, None); sc.collection.objects.link(root)
    scripts = []
    mats = [filament_material(f"{name}Fil{k}", col, gain=2.6 + 1.0 * k, dashes=4 + 2.5 * k, fadepow=1.2) for k in range(3)]
    for i in range(count):
        # bow shock: streaks start around the front and sides of the core and sweep back into a crescent tail
        phi = rnd.uniform(-1.0, 1.0) * math.radians(150)
        r0 = rnd.uniform(0.06, 0.13) * (1.0 + 0.4 * abs(phi) / math.radians(150))
        start = Vector((-sign * r0 * math.cos(phi), 0, r0 * math.sin(phi)))
        L = rnd.uniform(0.35, 0.95) * (0.6 + 0.4 * abs(phi) / math.radians(150))
        d = Vector((-sign * math.cos(phi * 0.55), 0, math.sin(phi) * 1.3)).normalized()
        back = Vector((-sign, 0, 0.2 * math.copysign(1, phi)))
        pts, p = [], start.copy()
        steps = 18
        for k in range(steps):
            pts.append(p.copy())
            f = k / (steps - 1)
            dd = d.lerp(back, min(1.0, f * 1.6)).normalized()
            p = p + dd * (L / (steps - 1))
        w0 = rnd.uniform(0.009, 0.03)
        widths = [w0 * (1.0 - 0.6 * (k / (steps - 1))) for k in range(steps)]
        mat, sc_ = mats[i % 3]
        ob = ribbon_mesh(f"{name}F{i}", pts, widths, mat)
        ob.parent = root
    for m, s_ in mats:
        s_.inputs["seed"].default_value = rnd.uniform(0, 50)
        scripts.append(s_)
    return root, scripts


def collide():
    """1.2 s @ 30 fps on deep space: Blue (left) and Red (right) streak in as comets and meet in a white-hot core,
    which turns into the violet imaginary mass."""
    N = 36
    HIT = 24
    sc = IL.new_scene(1280, 720, 30, samples=O["samples"] or 24)
    sc.render.use_motion_blur = True
    sc.render.motion_blur_shutter = 0.6
    sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0.004, 0.004, 0.012, 1)
    sc.world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    cam, tgt = IL.camera(sc, lens=40)
    rnd = random.Random(5)
    for f in (0, HIT - 2):
        IL.key_cam(cam, tgt, f, (0, -3.3 + 0.3 * f / HIT, 0.02), (0, 0, 0), lens=40)
    for f in range(HIT - 1, N + 1):
        j = 0.03 * max(0.0, 1.0 - (f - HIT) / 10.0)
        IL.key_cam(cam, tgt, f, (rnd.uniform(-j, j), -2.98 + 0.012 * (f - HIT), rnd.uniform(-j, j)), (0, 0, 0), lens=40)

    blue_col, red_col = (0.1, 0.62, 1.0), (1.0, 0.06, 0.035)
    blue, bs = comet(sc, "Blue", blue_col, +1, rnd, count=85)
    red, rs = comet(sc, "Red", red_col, -1, rnd, count=85)
    # glows ride with the comets
    def glow(name, parent, col, core_w, halo_w, halo_i, size):
        m, s_, _ = IL.additive_material(name, "orb_glow.osl", dict(halo=col, core_w=core_w, halo_w=halo_w, halo_i=halo_i, core_i=9.0))
        pl = IL.plane(name, m, size=size, billboard_to=cam)
        pl.parent = parent
        return pl, s_
    bg_, bgs = glow("BlueGlow", blue, blue_col, 0.14, 0.26, 1.1, 0.55)
    rg_, rgs = glow("RedGlow", red, red_col, 0.14, 0.26, 1.1, 0.55)
    for f in range(0, N + 1):
        k = min(1.0, f / HIT)
        x = 1.22 - 1.08 * (k ** 2.0)                       # accelerating into each other
        blue.location = (-x, 0, 0.02 * math.sin(f * 0.7)); red.location = (x, 0, -0.02 * math.sin(f * 0.6))
        after = max(0, f - HIT)
        sq = 1.0 if f < HIT else max(0.0, 1.0 - after / 7.0)
        blue.scale = red.scale = (sq, sq, sq) if sq > 0 else (1e-4,) * 3
        for o in (blue, red):
            o.keyframe_insert("location", frame=f); o.keyframe_insert("scale", frame=f)
        for s_ in bs + rs:
            IL.key_socket(s_.inputs["flow"], f, f * 0.55)
        for s_ in (bgs, rgs):
            IL.key_socket(s_.inputs["gain"], f, 1.0 + 0.15 * math.sin(f * 2.1) + 0.8 * smooth(HIT - 6, HIT, f))

    # the collision: a white-hot elongated core with a blue left rim and a red right rim → the violet mass
    def flat_glow(name, loc, col, size, sx=1.0):
        m, s_, _ = IL.additive_material(name, "orb_glow.osl", dict(halo=col, core_w=0.16, halo_w=0.24, halo_i=1.0, core_i=5.0))
        pl = IL.plane(name, m, size=size, loc=loc, sx=sx)
        pl.rotation_euler = (math.radians(90), 0, 0)      # faces -Y (the camera)
        return pl, s_
    core, cs = flat_glow("Core", (0, 0, 0), (1.0, 0.8, 1.0), 0.42, sx=1.45)
    lrim, ls_ = flat_glow("RimL", (-0.2, 0, 0), blue_col, 0.36)
    rrim, rs_ = flat_glow("RimR", (0.2, 0, 0), red_col, 0.36)
    # violet mass
    om = bpy.data.materials.new("Mass"); om.use_nodes = True
    nt = om.node_tree
    for n_ in list(nt.nodes): nt.nodes.remove(n_)
    o_ = nt.nodes.new("ShaderNodeOutputMaterial")
    fs_ = IL.osl_node(nt, "fresnel_orb.osl", dict(rim=(1.0, 0.2, 0.8), core_i=1.05, rim_i=2.6, rim_pow=1.6))
    em = nt.nodes.new("ShaderNodeEmission"); tr = nt.nodes.new("ShaderNodeBsdfTransparent"); ad = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(fs_.outputs["Color"], em.inputs["Color"]); nt.links.new(tr.outputs[0], ad.inputs[0]); nt.links.new(em.outputs[0], ad.inputs[1])
    nt.links.new(ad.outputs[0], o_.inputs["Surface"])
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=1.0)
    mass = bpy.context.active_object; mass.data.materials.append(om)
    for poly in mass.data.polygons: poly.use_smooth = True
    mhalo, mhs = flat_glow("MassHalo", (0, 0.05, 0), (0.7, 0.14, 1.0), 0.8)
    for f in range(0, N + 1):
        a = smooth(HIT - 1, HIT + 1, f)
        fade_rims = 1.0 - smooth(HIT + 3, HIT + 9, f)
        grow = smooth(HIT - 1, HIT + 3, f)
        IL.key_socket(cs.inputs["gain"], f, 1.3 * a * (1.0 - 0.8 * smooth(HIT + 4, N, f)))
        core.scale = (0.42 * 1.45 * (0.5 + 0.6 * grow), 0.42, 0.42 * (0.5 + 0.6 * grow)); core.keyframe_insert("scale", frame=f)
        for pl, s_ in ((lrim, ls_), (rrim, rs_)):
            IL.key_socket(s_.inputs["gain"], f, 1.3 * a * fade_rims)
        m = smooth(HIT + 4, HIT + 10, f)
        r = 0.001 + 0.26 * m * (1.0 + 0.04 * math.sin(f * 2.2))
        mass.scale = (r, r, r); mass.keyframe_insert("scale", frame=f)
        IL.key_socket(fs_.inputs["gain"], f, m)
        IL.key_socket(mhs.inputs["gain"], f, 0.55 * m + 0.8 * a * (1 - m))

    # sparks thrown out by the impact
    sm, ss, _ = IL.additive_material("Spark", "orb_glow.osl", dict(halo=(1.0, 0.6, 1.0), core_w=0.25, core_i=6.0, halo_w=0.6, halo_i=0.8))
    for i in range(46):
        th = rnd.uniform(0, 2 * math.pi)
        d = Vector((math.cos(th), 0, math.sin(th) * 0.8))
        sp = rnd.uniform(0.9, 2.6)
        sz = rnd.uniform(0.006, 0.018)
        pl = IL.plane(f"Sp{i}", sm, size=sz)
        pl.rotation_euler = (math.radians(90), 0, 0)
        for f in range(0, N + 1):
            t = max(0.0, (f - HIT) / 30.0)
            pl.location = d * (0.15 + sp * t * (1.0 - 0.4 * t))
            s_ = sz * (1.0 if f >= HIT else 0.0) * max(0.0, 1.0 - t * 2.2)
            pl.scale = (max(1e-5, s_),) * 3
            pl.keyframe_insert("location", frame=f); pl.keyframe_insert("scale", frame=f)

    # faint star field
    stm, sts, _ = IL.additive_material("Star", "orb_glow.osl", dict(halo=(0.6, 0.7, 1.0), core_w=0.3, core_i=1.5, halo_w=0.5, halo_i=0.2))
    for i in range(140):
        pl = IL.plane(f"St{i}", stm, size=rnd.uniform(0.004, 0.012), loc=(rnd.uniform(-3, 3), rnd.uniform(1.5, 4), rnd.uniform(-1.8, 1.8)))
        pl.rotation_euler = (math.radians(90), 0, 0)

    IL.cam_vignette(cam, strength=0.6)
    IL.compositor(sc, fog=(0.8, 9, 0.0), streaks=(2.5, 4, 0, 0.9, -0.5), dispersion=0.018)
    finish(sc, "nuke_collide", N)


# =====================================================================================================================
def implode():
    """0.5 s @ 30 fps (T380-390): the white-hot mass collapses to a single point on black; sparks burst out into the
    dark and hang there (the clip's 18.0-18.5 s). The in-engine shot that follows picks up the point."""
    N = 15
    COL = 7                                                    # the frame it becomes a point
    sc = IL.new_scene(1280, 720, 30, samples=O["samples"] or 24)
    sc.render.use_motion_blur = True
    sc.render.motion_blur_shutter = 0.5
    cam, tgt = IL.camera(sc, lens=50)
    IL.key_cam(cam, tgt, 0, (0, -3.62, 0.0), (0, 0, 0), lens=50)
    IL.key_cam(cam, tgt, N, (0, -3.45, 0.0), (0, 0, 0), lens=52)
    rnd = random.Random(9)
    om = bpy.data.materials.new("Mass"); om.use_nodes = True
    nt = om.node_tree
    for n_ in list(nt.nodes): nt.nodes.remove(n_)
    o_ = nt.nodes.new("ShaderNodeOutputMaterial")
    fs_ = IL.osl_node(nt, "fresnel_orb.osl", dict(rim=(1.0, 0.2, 0.8), core_i=1.1, rim_i=2.8, rim_pow=1.5))
    em = nt.nodes.new("ShaderNodeEmission"); tr = nt.nodes.new("ShaderNodeBsdfTransparent"); ad = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(fs_.outputs["Color"], em.inputs["Color"]); nt.links.new(tr.outputs[0], ad.inputs[0]); nt.links.new(em.outputs[0], ad.inputs[1])
    nt.links.new(ad.outputs[0], o_.inputs["Surface"])
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=1.0)
    mass = bpy.context.active_object; mass.data.materials.append(om)
    for poly in mass.data.polygons: poly.use_smooth = True
    hm, hs, _ = IL.additive_material("Halo", "orb_glow.osl", dict(halo=(0.8, 0.14, 1.0), core_w=0.12, core_i=4.0, halo_w=0.24, halo_i=1.0))
    halo = IL.plane("Halo", hm, size=1.1, loc=(0, 0.05, 0)); halo.rotation_euler = (math.radians(90), 0, 0)
    pm, ps, _ = IL.additive_material("Point", "orb_glow.osl", dict(halo=(1.0, 0.3, 1.0), core_w=0.08, core_i=14.0, halo_w=0.35, halo_i=1.4))
    pt = IL.plane("Point", pm, size=0.12, loc=(0, -0.05, 0)); pt.rotation_euler = (math.radians(90), 0, 0)
    for f in range(0, N + 1):
        c = smooth(0, COL, f) ** 1.8                           # collapse, accelerating
        r = 0.26 * (1.0 + 0.05 * math.sin(f * 2.4)) * (1.0 - c) + 0.003
        mass.scale = (r, r, r); mass.keyframe_insert("scale", frame=f)
        IL.key_socket(fs_.inputs["gain"], f, (1.0 + 2.2 * c) * (1.0 - smooth(COL - 1, COL + 1, f)))
        halo.scale = (1.1 * (1.0 - 0.9 * c),) * 3; halo.keyframe_insert("scale", frame=f)
        IL.key_socket(hs.inputs["gain"], f, 0.9 * (1.0 - smooth(COL - 2, COL + 1, f)))
        pulse = smooth(COL - 1, COL + 1, f) * (1.0 + 0.8 * math.exp(-(f - COL) ** 2 / 2.0) if f >= COL - 1 else 0.0) * (1.0 + 0.2 * math.sin(f * 3.0))
        IL.key_socket(ps.inputs["gain"], f, pulse)
    # streaks sucked in during the collapse
    fm, fs2 = filament_material("Suck", (1.0, 0.3, 0.95), gain=1.4, dashes=4.0, fadepow=1.0)
    for i in range(32):
        th = rnd.uniform(0, 2 * math.pi)
        d = Vector((math.cos(th), 0, math.sin(th)))
        L = rnd.uniform(0.3, 0.9)
        pts = [d * (0.02 + L * k / 11) for k in range(12)]
        ob = ribbon_mesh(f"Suck{i}", pts, [0.004 * (1 - 0.5 * k / 11) for k in range(12)], fm)
        for f in range(0, N + 1):
            vis = smooth(0, 2, f) * (1.0 - smooth(COL - 1, COL + 1, f))
            sc_ = 0.25 + 1.4 * (1.0 - smooth(0, COL, f))
            ob.scale = (sc_ * max(vis, 1e-4),) * 3
            ob.keyframe_insert("scale", frame=f)
    for f in range(0, N + 1):
        IL.key_socket(fs2.inputs["flow"], f, -f * 1.2)
    # the burst: fine violet sparks flung out from the point, slowing and hanging in the dark
    sm, ss, _ = IL.additive_material("Spark", "orb_glow.osl", dict(halo=(1.0, 0.25, 0.95), core_w=0.22, core_i=7.0, halo_w=0.55, halo_i=0.9))
    for i in range(90):
        th = rnd.uniform(0, 2 * math.pi)
        rr = rnd.uniform(0.15, 1.1) * (1.0 if rnd.random() < 0.7 else 0.5)
        d = Vector((math.cos(th), rnd.uniform(-0.2, 0.2), math.sin(th)))
        sz = rnd.uniform(0.004, 0.014)
        tw = rnd.uniform(0, 6.28)
        pl = IL.plane(f"S{i}", sm, size=sz); pl.rotation_euler = (math.radians(90), 0, 0)
        for f in range(0, N + 1):
            k = max(0.0, (f - COL) / (N - COL))
            out_ = 1.0 - (1.0 - k) ** 3                        # fast out, then hanging
            a = smooth(COL, COL + 1, f) * (0.6 + 0.4 * math.sin(f * 1.9 + tw))
            pl.location = d * (0.02 + rr * out_)
            # stretched along the flight at first (a streak), a point once it hangs
            st = 1.0 + 6.0 * (1.0 - out_) * (1.0 if f >= COL else 0.0)
            pl.scale = (max(1e-5, sz * a * st), max(1e-5, sz * a), max(1e-5, sz * a))
            pl.rotation_euler = (math.radians(90), -th, 0)
            pl.keyframe_insert("location", frame=f); pl.keyframe_insert("scale", frame=f); pl.keyframe_insert("rotation_euler", frame=f)
    IL.compositor(sc, fog=(0.7, 9, 0.0), streaks=(2.0, 4, 45, 0.88, -0.6), dispersion=0.01)
    finish(sc, "nuke_implode", N)


# =====================================================================================================================
def glint():
    """0.5 s @ 30 fps (T462): a hard cut to white; a black concave four-point star snaps open, its long arms reaching
    for the top and bottom of the frame, and melts into the white (the clip's 23.0 s)."""
    N = 15
    sc = IL.new_scene(1280, 720, 30, samples=O["samples"] or 16)
    sc.world.node_tree.nodes["Background"].inputs[0].default_value = (1, 1, 1, 1)
    sc.world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    sc.view_settings.view_transform = "Standard"
    cam, tgt = IL.camera(sc, lens=50)
    IL.key_cam(cam, tgt, 0, (0, -4, 0), (0, 0, 0), lens=50)
    mat = bpy.data.materials.new("Star"); mat.use_nodes = True
    nt = mat.node_tree
    for n_ in list(nt.nodes): nt.nodes.remove(n_)
    o_ = nt.nodes.new("ShaderNodeOutputMaterial")
    st = IL.osl_node(nt, "astroid.osl", dict(ax=0.5, ay=0.8, p=0.3, soft=0.09))
    mul = nt.nodes.new("ShaderNodeMath"); mul.operation = "MULTIPLY"
    nt.links.new(st.outputs["Fac"], mul.inputs[0])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (0.0, 0.0, 0.0, 1)
    mx = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(mul.outputs[0], mx.inputs["Fac"]); nt.links.new(tr.outputs[0], mx.inputs[1]); nt.links.new(em.outputs[0], mx.inputs[2])
    nt.links.new(mx.outputs[0], o_.inputs["Surface"])
    # the plane spans the frame height (the camera sees ~2.9 units vertically at 4 units with a 50 mm lens)
    pl = IL.plane("Star", mat, size=3.2); pl.rotation_euler = (math.radians(90), 0, 0)
    for f in range(0, N + 1):
        open_ = 1.0 - (1.0 - smooth(0, 4, f)) ** 2
        grow = 1.0 + 0.06 * f / N
        IL.key_socket(st.inputs["ax"], f, (0.04 + 0.36 * open_) * grow)
        IL.key_socket(st.inputs["ay"], f, (0.06 + 0.66 * open_) * grow)
        mul.inputs[1].default_value = 1.0 - smooth(7, 14, f)
        mul.inputs[1].keyframe_insert("default_value", frame=f)
        pl.rotation_euler = (math.radians(90), math.radians(4 * f / N), 0)
        pl.keyframe_insert("rotation_euler", frame=f)
    IL.compositor(sc, fog=None, dispersion=0.004)
    finish(sc, "nuke_glint", N)


# =====================================================================================================================
if O["only"] is not None and "nuke_red_sign" in O["only"]:
    red_sign()
if want("nuke_collide"):
    collide()
if want("nuke_implode"):
    implode()
if want("nuke_glint"):
    glint()
