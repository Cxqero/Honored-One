"""The inside of Unlimited Void, built and rendered in Blender (Cycles), after S1E7's interior.

    void_pano   the space around you: a 360° equirectangular panorama — deep navy and teal nebula with depth, dense
                stars, faint violet dust lanes. One still frame (the void doesn't move; what's in it does).
    void_hole   the giant black hole as a seamless animated loop: a black sphere, a thick ring of blue-white smoke
                turning around it (a flat emissive ring for the S1E7 look + a volumetric shell for depth), the white
                limb and prismatic rim, and a volumetric smoke stream pouring off to one side. Rendered on black for
                additive blending; the game draws the black disk under it.
    void_wisps  a 4x4 atlas of volumetric smoke wisps that drift around you in the game (3D, in the world).

Loops: every animated procedural is evaluated at time t and at t - L and cross-faded (t / L), so frame L == frame 0.
Output goes to assets/gojolimitless/loops/<name>/NNN.jpg + meta.json (streamed by the game like the inserts).

Run: tools/blender -b -P blender/inserts/build_void_interior.py -- [--only void_pano,void_hole,void_wisps]
     [--preview] [--frames 0,12] [--scale 50] [--samples N]
"""
import bpy, os, sys, math, json, random
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import importlib
import insertlib as IL
importlib.reload(IL)
from mathutils import Vector

O = IL.cli()
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
LOOPS = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "gojolimitless", "loops")
VFX = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "gojolimitless", "textures", "vfx")
PREV = os.path.join(ROOT, "blender", "inserts", "previews", "void")

HOLE_FRAMES = 60            # 5 s at 12 fps
HOLE_FPS = 12


def want(n):
    return O["only"] is None or n in O["only"]


def scene(w, h, samples):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.shading_system = True          # OSL
    sc.cycles.samples = O["samples"] or samples
    sc.cycles.use_denoising = True
    try:
        sc.cycles.denoiser = "OPENIMAGEDENOISE"
    except Exception:
        pass
    sc.cycles.max_bounces = 2
    sc.cycles.volume_bounces = 0
    sc.cycles.volume_step_rate = 4.0
    sc.cycles.volume_max_steps = 256
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.adaptive_threshold = 0.02
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = O["scale"]
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.world = bpy.data.worlds.new("W")
    sc.world.use_nodes = True
    return sc


def out_dir(name):
    d = os.path.join(PREV if O["preview"] else LOOPS, name)
    os.makedirs(d, exist_ok=True)
    return d


def render_frames(sc, name, frames, fps, blend, loop=True):
    d = out_dir(name)
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = 88
    sc.render.image_settings.color_mode = "RGB"
    todo = O["frames"] if O["frames"] is not None else range(frames)
    for f in todo:
        sc.frame_set(f)
        sc.render.filepath = os.path.join(d, f"{f:03d}.jpg")
        bpy.ops.render.render(write_still=True)
        print(f"[void] {name} frame {f}/{frames}", flush=True)
    if not O["preview"]:
        with open(os.path.join(d, "meta.json"), "w") as fp:
            json.dump({"fps": fps, "frames": frames, "ext": "jpg", "blend": blend, "loop": loop}, fp)


def nodes(mat):
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    return nt, nt.nodes, nt.links


def looped_noise(nd, lk, vec_socket, scale, detail, rough, name, t_offset=0.0, motion=None):
    """Two 4D noises at time t and t - L, mixed by t / L, so the loop closes (keyed per frame by key_loop).
    motion: None, ("rotate", axis, radians per loop) or ("move", (x, y, z) per loop) — applied to the sample point."""
    a = nd.new("ShaderNodeTexNoise"); a.noise_dimensions = "4D"
    b = nd.new("ShaderNodeTexNoise"); b.noise_dimensions = "4D"
    movers = []
    for n in (a, b):
        n.inputs["Scale"].default_value = scale
        n.inputs["Detail"].default_value = detail
        n.inputs["Roughness"].default_value = rough
        if motion is None:
            lk.new(vec_socket, n.inputs["Vector"])
            movers.append(None)
        elif motion[0] == "rotate":
            vr = nd.new("ShaderNodeVectorRotate"); vr.rotation_type = "AXIS_ANGLE"
            vr.inputs["Axis"].default_value = motion[1]
            lk.new(vec_socket, vr.inputs["Vector"]); lk.new(vr.outputs[0], n.inputs["Vector"])
            movers.append(vr.inputs["Angle"])
        else:
            mp = nd.new("ShaderNodeMapping")
            lk.new(vec_socket, mp.inputs["Vector"]); lk.new(mp.outputs[0], n.inputs["Vector"])
            movers.append(mp.inputs["Location"])
    mx = nd.new("ShaderNodeMix"); mx.data_type = "FLOAT"
    lk.new(a.outputs["Fac"], mx.inputs[2]); lk.new(b.outputs["Fac"], mx.inputs[3])
    LOOPED.append((a, b, mx, t_offset, motion, movers))
    return mx.outputs[0]


LOOPED = []
LOOP_SOCKETS = []          # extra (socket, value_at_k(k)) pairs keyed with the loop


def key_loop(frames, speed=1.0):
    """Key every looped noise: W and the motion run with time; the second copy one loop behind; the mix fades
    across the loop. Linear keys, so the seam has no ease."""
    for f in range(frames + 1):
        k = f / frames
        for a, b, mx, off, motion, movers in LOOPED:
            for n, kk, mv in ((a, k, movers[0]), (b, k - 1.0, movers[1])):
                n.inputs["W"].default_value = off + kk * speed
                n.inputs["W"].keyframe_insert("default_value", frame=f)
                if mv is not None:
                    if motion[0] == "rotate":
                        mv.default_value = motion[2] * kk
                    else:
                        mv.default_value = tuple(c * kk for c in motion[1])
                    mv.keyframe_insert("default_value", frame=f)
            mx.inputs["Factor"].default_value = k
            mx.inputs["Factor"].keyframe_insert("default_value", frame=f)
        for sock, fn in LOOP_SOCKETS:
            sock.default_value = fn(k)
            sock.keyframe_insert("default_value", frame=f)
    for m in bpy.data.materials:
        ad = m.node_tree.animation_data if m.node_tree else None
        if ad and ad.action:
            for fc in ad.action.fcurves:
                for kp in fc.keyframe_points:
                    kp.interpolation = "LINEAR"


# ===================================================================================================== the black hole
def hole():
    """The camera looks at the hole from 60 units; the ring faces it. Frame: 2:1, the hole left of centre, the
    stream pouring off to the right (as in S1E7)."""
    W, H = 1280, 640
    sc = scene(W, H, 16)
    sc.render.fps = HOLE_FPS
    sc.world.node_tree.nodes["Background"].inputs[1].default_value = 0.0
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); sc.collection.objects.link(cam); sc.camera = cam
    cam.data.sensor_fit = "HORIZONTAL"; cam.data.sensor_width = 36.0
    cam.data.lens = 22.0                                    # ~78° horizontal
    cam.location = (0, 0, 0)
    cam.rotation_euler = (math.radians(90), 0, 0)           # looking +y
    C = Vector((-17.0, 60.0, 0.0))                          # the hole sits left of centre
    R_HOLE, R_OUT = 11.0, 20.0

    # the hole: pure black, occludes what is behind it
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=R_HOLE, location=C)
    hole_ob = bpy.context.active_object
    m = bpy.data.materials.new("Hole"); nt, nd, lk = nodes(m)
    o = nd.new("ShaderNodeOutputMaterial"); em = nd.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (0, 0, 0, 1); lk.new(em.outputs[0], o.inputs["Surface"])
    hole_ob.data.materials.append(m)

    # the flat ring (the S1E7 look): an annulus facing the camera, shaded by blackhole.osl, turning with time
    bpy.ops.mesh.primitive_circle_add(vertices=256, radius=R_OUT * 1.25, fill_type="NGON", location=C)
    ring = bpy.context.active_object
    ring.rotation_euler = (math.radians(90) + math.radians(8), 0, math.radians(-6))
    m = bpy.data.materials.new("Ring"); nt, nd, lk = nodes(m)
    o = nd.new("ShaderNodeOutputMaterial")
    osl = IL.osl_node(nt, "blackhole.osl", dict(hole=R_HOLE / (R_OUT * 1.25) * 0.98, outer=R_OUT / (R_OUT * 1.25), seed=4.0,
                                                gain=1.15, objspace=1, scale=1.0 / (R_OUT * 1.25), span=0.9))
    em = nd.new("ShaderNodeEmission"); tr = nd.new("ShaderNodeBsdfTransparent"); ad = nd.new("ShaderNodeAddShader")
    lk.new(osl.outputs["Color"], em.inputs["Color"]); lk.new(tr.outputs[0], ad.inputs[0]); lk.new(em.outputs[0], ad.inputs[1])
    lk.new(ad.outputs[0], o.inputs["Surface"])
    ring.data.materials.append(m)
    LOOP_SOCKETS.append((osl.inputs["spin"], lambda k: 0.9 * k))
    LOOP_SOCKETS.append((osl.inputs["loopk"], lambda k: k))

    # the volumetric shell: smoke swirling round the hole, giving the ring depth
    bpy.ops.mesh.primitive_torus_add(major_radius=(R_HOLE + R_OUT) * 0.5, minor_radius=(R_OUT - R_HOLE) * 0.62,
                                     major_segments=96, minor_segments=32, location=C)
    shell = bpy.context.active_object
    shell.rotation_euler = ring.rotation_euler
    shell.scale = (1.0, 1.0, 0.45)
    m = bpy.data.materials.new("Shell"); nt, nd, lk = nodes(m)
    o = nd.new("ShaderNodeOutputMaterial"); vol = nd.new("ShaderNodeVolumePrincipled")
    tc = nd.new("ShaderNodeTexCoord")
    # swirl: rotate the sampling point about the torus axis by an angle that depends on radius (inner turns faster)
    sep = nd.new("ShaderNodeSeparateXYZ"); lk.new(tc.outputs["Object"], sep.inputs[0])
    noise = looped_noise(nd, lk, tc.outputs["Object"], 0.16, 8.0, 0.62, "shell", motion=("rotate", (0, 0, 1), -0.9))
    ramp = nd.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.42; ramp.color_ramp.elements[1].position = 0.78
    lk.new(noise, ramp.inputs[0])
    dens = nd.new("ShaderNodeMath"); dens.operation = "MULTIPLY"; dens.inputs[1].default_value = 0.05
    lk.new(ramp.outputs["Color"], dens.inputs[0])
    lk.new(dens.outputs[0], vol.inputs["Density"])
    vol.inputs["Color"].default_value = (0.55, 0.68, 0.95, 1)
    vol.inputs["Emission Color"].default_value = (0.62, 0.74, 1.0, 1)
    emis = nd.new("ShaderNodeMath"); emis.operation = "MULTIPLY"; emis.inputs[1].default_value = 0.16
    lk.new(ramp.outputs["Color"], emis.inputs[0]); lk.new(emis.outputs[0], vol.inputs["Emission Strength"])
    lk.new(vol.outputs[0], o.inputs["Volume"])
    shell.data.materials.append(m)

    # the stream: smoke pouring off the ring to the right, dispersing
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=C + Vector((R_OUT + 26.0, 8.0, 1.0)))
    stream = bpy.context.active_object
    stream.scale = (64.0, 36.0, 30.0)
    stream.rotation_euler = (0, math.radians(-4), math.radians(-8))
    m = bpy.data.materials.new("Stream"); nt, nd, lk = nodes(m)
    o = nd.new("ShaderNodeOutputMaterial"); vol = nd.new("ShaderNodeVolumePrincipled")
    tc = nd.new("ShaderNodeTexCoord")
    sep = nd.new("ShaderNodeSeparateXYZ"); lk.new(tc.outputs["Generated"], sep.inputs[0])
    # flow along x: stretch the noise along the stream so it reads as streaks
    mp = nd.new("ShaderNodeMapping"); mp.inputs["Scale"].default_value = (0.35, 1.6, 1.6)
    lk.new(tc.outputs["Object"], mp.inputs["Vector"])
    noise = looped_noise(nd, lk, mp.outputs["Vector"], 3.2, 10.0, 0.66, "stream", t_offset=3.0, motion=("move", (-0.9, 0.0, 0.0)))
    ramp = nd.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.5; ramp.color_ramp.elements[1].position = 0.8
    lk.new(noise, ramp.inputs[0])
    # a funnel: thin near the ring (x≈0), fanning out; fades at the far end
    fx = nd.new("ShaderNodeFloatCurve"); lk.new(sep.outputs["X"], fx.inputs["Value"])
    cm = fx.mapping; cm.curves[0].points[0].location = (0.0, 1.0); cm.curves[0].points[1].location = (1.0, 0.0)
    cm.curves[0].points.new(0.35, 0.8)
    yz = nd.new("ShaderNodeVectorMath"); yz.operation = "DISTANCE"
    cyz = nd.new("ShaderNodeCombineXYZ"); lk.new(sep.outputs["Y"], cyz.inputs["Y"]); lk.new(sep.outputs["Z"], cyz.inputs["Z"])
    yz.inputs[1].default_value = (0.0, 0.5, 0.5)
    lk.new(cyz.outputs[0], yz.inputs[0])
    width = nd.new("ShaderNodeMath"); width.operation = "MULTIPLY_ADD"
    lk.new(sep.outputs["X"], width.inputs[0]); width.inputs[1].default_value = 0.34; width.inputs[2].default_value = 0.14
    core = nd.new("ShaderNodeMath"); core.operation = "DIVIDE"; lk.new(yz.outputs["Value"], core.inputs[0]); lk.new(width.outputs[0], core.inputs[1])
    fall = nd.new("ShaderNodeMapRange"); fall.inputs["From Min"].default_value = 0.4; fall.inputs["From Max"].default_value = 1.0
    fall.inputs["To Min"].default_value = 1.0; fall.inputs["To Max"].default_value = 0.0
    lk.new(core.outputs[0], fall.inputs["Value"])
    d1 = nd.new("ShaderNodeMath"); d1.operation = "MULTIPLY"; lk.new(ramp.outputs["Color"], d1.inputs[0]); lk.new(fx.outputs[0], d1.inputs[1])
    d2 = nd.new("ShaderNodeMath"); d2.operation = "MULTIPLY"; lk.new(d1.outputs[0], d2.inputs[0]); lk.new(fall.outputs["Result"], d2.inputs[1])
    dd = nd.new("ShaderNodeMath"); dd.operation = "MULTIPLY"; dd.inputs[1].default_value = 0.08
    lk.new(d2.outputs[0], dd.inputs[0]); lk.new(dd.outputs[0], vol.inputs["Density"])
    ee = nd.new("ShaderNodeMath"); ee.operation = "MULTIPLY"; ee.inputs[1].default_value = 0.35
    lk.new(d2.outputs[0], ee.inputs[0]); lk.new(ee.outputs[0], vol.inputs["Emission Strength"])
    vol.inputs["Color"].default_value = (0.5, 0.65, 0.9, 1)
    vol.inputs["Emission Color"].default_value = (0.55, 0.72, 1.0, 1)
    lk.new(vol.outputs[0], o.inputs["Volume"])
    stream.data.materials.append(m)

    key_loop(HOLE_FRAMES, speed=0.9)
    hide = sys.argv[sys.argv.index("--hide") + 1].split(",") if "--hide" in sys.argv else []
    for nm, ob in (("ring", ring), ("shell", shell), ("stream", stream)):
        if nm in hide:
            ob.hide_render = True
    IL.compositor(sc, fog=(1.2, 7, 0.0), streaks=None, dispersion=0.004)
    render_frames(sc, "void_hole", HOLE_FRAMES, HOLE_FPS, "add")


# ===================================================================================================== the void around you
def pano():
    """A 360° equirectangular panorama from the centre of the void: deep navy space with teal and violet nebula
    in depth (three layers at different scales), dark dust lanes, a dense star field in two sizes, a few bright
    stars with glints. The black hole is not in it (it is its own animated layer)."""
    W, H = 3072, 1536
    sc = scene(W, H, 12)
    sc.cycles.use_denoising = False
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); sc.collection.objects.link(cam); sc.camera = cam
    cam.data.type = "PANO"
    cam.data.panorama_type = "EQUIRECTANGULAR"
    cam.rotation_euler = (math.radians(90), 0, math.radians(-90))     # centre of the image = +y
    nt = sc.world.node_tree; nd = nt.nodes; lk = nt.links
    for n in list(nd): nd.remove(n)
    out = nd.new("ShaderNodeOutputWorld"); bg = nd.new("ShaderNodeBackground")
    lk.new(bg.outputs[0], out.inputs["Surface"])
    tc = nd.new("ShaderNodeTexCoord")
    d = tc.outputs["Generated"]                                        # the view direction for a world

    def noise(scale, detail, rough, w, dist=0.0):
        n = nd.new("ShaderNodeTexNoise"); n.noise_dimensions = "4D"
        n.inputs["Scale"].default_value = scale; n.inputs["Detail"].default_value = detail
        n.inputs["Roughness"].default_value = rough; n.inputs["W"].default_value = w
        n.inputs["Distortion"].default_value = dist
        lk.new(d, n.inputs["Vector"])
        return n.outputs["Fac"]

    def ramp(src, stops):
        r = nd.new("ShaderNodeValToRGB")
        el = r.color_ramp.elements
        el[0].position, el[0].color = stops[0][0], (*stops[0][1], 1)
        el[1].position, el[1].color = stops[-1][0], (*stops[-1][1], 1)
        for pos, col in stops[1:-1]:
            e = el.new(pos); e.color = (*col, 1)
        lk.new(src, r.inputs[0])
        return r.outputs["Color"]

    def add(a, b):
        m = nd.new("ShaderNodeMix"); m.data_type = "RGBA"; m.blend_type = "ADD"; m.inputs["Factor"].default_value = 1.0
        lk.new(a, m.inputs[6]); lk.new(b, m.inputs[7]); return m.outputs[2]

    def mul(a, b):
        m = nd.new("ShaderNodeMix"); m.data_type = "RGBA"; m.blend_type = "MULTIPLY"; m.inputs["Factor"].default_value = 1.0
        lk.new(a, m.inputs[6]); lk.new(b, m.inputs[7]); return m.outputs[2]

    base = ramp(noise(1.2, 3.0, 0.5, 1.0), [(0.3, (0.003, 0.006, 0.016)), (0.7, (0.008, 0.02, 0.045))])
    neb1 = ramp(noise(1.6, 8.0, 0.62, 3.0, 0.4), [(0.58, (0, 0, 0)), (0.7, (0.012, 0.04, 0.07)), (0.84, (0.05, 0.13, 0.2))])
    neb2 = ramp(noise(2.4, 8.0, 0.6, 7.0, 0.8), [(0.62, (0, 0, 0)), (0.74, (0.03, 0.015, 0.06)), (0.88, (0.1, 0.05, 0.18))])
    lanes = ramp(noise(5.0, 6.0, 0.65, 11.0, 1.2), [(0.35, (0.35, 0.35, 0.4)), (0.6, (1, 1, 1))])
    sky = mul(add(add(base, neb1), neb2), lanes)
    # stars: voronoi cells, a point at each feature, two sizes
    for scale, size, bright, col in ((260.0, 0.16, 5.0, (0.8, 0.88, 1.0)), (70.0, 0.06, 16.0, (1.0, 0.95, 0.9)), (620.0, 0.22, 1.6, (0.7, 0.8, 1.0))):
        v = nd.new("ShaderNodeTexVoronoi"); v.voronoi_dimensions = "3D"; v.feature = "F1"
        v.inputs["Scale"].default_value = scale; v.inputs["Randomness"].default_value = 1.0
        lk.new(d, v.inputs["Vector"])
        mr = nd.new("ShaderNodeMapRange"); mr.inputs["From Min"].default_value = size; mr.inputs["From Max"].default_value = 0.0
        mr.inputs["To Min"].default_value = 0.0; mr.inputs["To Max"].default_value = 1.0
        lk.new(v.outputs["Distance"], mr.inputs["Value"])
        pw = nd.new("ShaderNodeMath"); pw.operation = "POWER"; pw.inputs[1].default_value = 3.0
        lk.new(mr.outputs["Result"], pw.inputs[0])
        # only some cells hold a star
        keep = nd.new("ShaderNodeMath"); keep.operation = "GREATER_THAN"; keep.inputs[1].default_value = 0.55
        c2 = nd.new("ShaderNodeSeparateColor"); lk.new(v.outputs["Color"], c2.inputs[0]); lk.new(c2.outputs[0], keep.inputs[0])
        m1 = nd.new("ShaderNodeMath"); m1.operation = "MULTIPLY"; lk.new(pw.outputs[0], m1.inputs[0]); lk.new(keep.outputs[0], m1.inputs[1])
        m2 = nd.new("ShaderNodeMath"); m2.operation = "MULTIPLY"; m2.inputs[1].default_value = bright; lk.new(m1.outputs[0], m2.inputs[0])
        cc = nd.new("ShaderNodeMix"); cc.data_type = "RGBA"; cc.blend_type = "MULTIPLY"; cc.inputs["Factor"].default_value = 1.0
        cc.inputs[7].default_value = (*col, 1)
        cv = nd.new("ShaderNodeCombineColor"); lk.new(m2.outputs[0], cv.inputs[0]); lk.new(m2.outputs[0], cv.inputs[1]); lk.new(m2.outputs[0], cv.inputs[2])
        lk.new(cv.outputs[0], cc.inputs[6])
        sky = add(sky, cc.outputs[2])
    lk.new(sky, bg.inputs["Color"]); bg.inputs["Strength"].default_value = 1.0
    IL.compositor(sc, fog=(1.0, 5, 0.0), streaks=None, dispersion=0.0)
    d_ = out_dir("void_pano")
    sc.render.image_settings.file_format = "JPEG"; sc.render.image_settings.quality = 90
    sc.render.filepath = os.path.join(d_, "000.jpg")
    bpy.ops.render.render(write_still=True)
    if not O["preview"]:
        with open(os.path.join(d_, "meta.json"), "w") as fp:
            json.dump({"fps": 1, "frames": 1, "ext": "jpg", "blend": "opaque", "loop": True}, fp)
    print("[void] pano", flush=True)


# ===================================================================================================== wisps
def wisps():
    """16 volumetric smoke wisps (blue-white, emissive, on black) in a 4x4 atlas, for the 3D drift around you."""
    from PIL import Image
    tiles = []
    for i in range(16):
        sc = scene(512, 512, 24)
        sc.world.node_tree.nodes["Background"].inputs[1].default_value = 0.0
        cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); sc.collection.objects.link(cam); sc.camera = cam
        cam.data.type = "ORTHO"; cam.data.ortho_scale = 2.2
        cam.location = (0, -5, 0); cam.rotation_euler = (math.radians(90), 0, 0)
        rnd = random.Random(i * 7 + 3)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, segments=32, ring_count=16)
        ob = bpy.context.active_object
        ob.scale = (rnd.uniform(0.8, 1.0), rnd.uniform(0.5, 0.8), rnd.uniform(0.35, 0.6))
        ob.rotation_euler = (0, rnd.uniform(-0.6, 0.6), 0)
        m = bpy.data.materials.new("W"); nt, nd, lk = nodes(m)
        o = nd.new("ShaderNodeOutputMaterial"); vol = nd.new("ShaderNodeVolumePrincipled")
        tc = nd.new("ShaderNodeTexCoord")
        n = nd.new("ShaderNodeTexNoise"); n.noise_dimensions = "4D"
        n.inputs["Scale"].default_value = rnd.uniform(1.8, 2.8); n.inputs["Detail"].default_value = 10.0
        n.inputs["Roughness"].default_value = 0.62; n.inputs["W"].default_value = i * 3.7; n.inputs["Distortion"].default_value = 1.4
        lk.new(tc.outputs["Object"], n.inputs["Vector"])
        # fade to the edge of the sphere
        ln = nd.new("ShaderNodeVectorMath"); ln.operation = "LENGTH"; lk.new(tc.outputs["Object"], ln.inputs[0])
        edge = nd.new("ShaderNodeMapRange"); edge.inputs["From Min"].default_value = 0.15; edge.inputs["From Max"].default_value = 1.0
        edge.inputs["To Min"].default_value = 1.0; edge.inputs["To Max"].default_value = 0.0
        lk.new(ln.outputs["Value"], edge.inputs["Value"])
        r = nd.new("ShaderNodeValToRGB"); r.color_ramp.elements[0].position = 0.5; r.color_ramp.elements[1].position = 0.8
        lk.new(n.outputs["Fac"], r.inputs[0])
        # a second, broad noise eats into the silhouette so it reads as a wisp, not an egg
        n2 = nd.new("ShaderNodeTexNoise"); n2.noise_dimensions = "4D"
        n2.inputs["Scale"].default_value = 0.9; n2.inputs["Detail"].default_value = 3.0; n2.inputs["W"].default_value = i * 1.9 + 40
        lk.new(tc.outputs["Object"], n2.inputs["Vector"])
        r2 = nd.new("ShaderNodeValToRGB"); r2.color_ramp.elements[0].position = 0.36; r2.color_ramp.elements[1].position = 0.62
        lk.new(n2.outputs["Fac"], r2.inputs[0])
        dm0 = nd.new("ShaderNodeMath"); dm0.operation = "MULTIPLY"; lk.new(r.outputs["Color"], dm0.inputs[0]); lk.new(r2.outputs["Color"], dm0.inputs[1])
        dm = nd.new("ShaderNodeMath"); dm.operation = "MULTIPLY"; lk.new(dm0.outputs[0], dm.inputs[0]); lk.new(edge.outputs["Result"], dm.inputs[1])
        d2 = nd.new("ShaderNodeMath"); d2.operation = "MULTIPLY"; d2.inputs[1].default_value = 4.0; lk.new(dm.outputs[0], d2.inputs[0])
        lk.new(d2.outputs[0], vol.inputs["Density"])
        e2 = nd.new("ShaderNodeMath"); e2.operation = "MULTIPLY"; e2.inputs[1].default_value = 2.4; lk.new(dm.outputs[0], e2.inputs[0])
        lk.new(e2.outputs[0], vol.inputs["Emission Strength"])
        vol.inputs["Emission Color"].default_value = (0.6, 0.74, 1.0, 1)
        vol.inputs["Color"].default_value = (0.5, 0.62, 0.9, 1)
        lk.new(vol.outputs[0], o.inputs["Volume"])
        ob.data.materials.append(m)
        path = os.path.join(PREV, f"_wisp_{i:02d}.png")
        os.makedirs(PREV, exist_ok=True)
        sc.render.image_settings.file_format = "PNG"; sc.render.image_settings.color_mode = "RGB"
        sc.render.filepath = path
        bpy.ops.render.render(write_still=True)
        tiles.append(path)
    atlas = Image.new("RGB", (2048, 2048))
    for i, p in enumerate(tiles):
        atlas.paste(Image.open(p).convert("RGB"), ((i % 4) * 512, (i // 4) * 512))
    dst = os.path.join(PREV if O["preview"] else VFX, "void_wisps.png")
    atlas.save(dst, optimize=True)
    if not O["preview"]:
        with open(dst + ".mcmeta", "w") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f)
    print("[void] wisps", dst, flush=True)


if want("void_pano"):
    pano()
if want("void_wisps"):
    wisps()
if want("void_hole"):
    hole()
