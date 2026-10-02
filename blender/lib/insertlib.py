"""Shared scaffolding for full-screen cutscene inserts (Blender 4.5, Cycles CPU + OSL).

An insert is a short pre-rendered clip shown over the whole screen on the HUD layer (so shaderpacks never touch
it): frames go to assets/gojolimitless/inserts/<name>/NNN.jpg with meta.json {fps, frames, ext, blend}.
"""
import bpy, os, sys, json, math
from mathutils import Vector

LIB = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(LIB)
OSL_DIR = os.path.join(ROOT, "osl")
ASSETS = os.path.join(ROOT, "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "inserts")


def cli():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    o = {"only": None, "preview": False, "frames": None, "scale": 100, "samples": None}
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--only": o["only"] = argv[i + 1].split(","); i += 2
        elif a == "--preview": o["preview"] = True; i += 1
        elif a == "--frames": o["frames"] = [int(x) for x in argv[i + 1].split(",")]; i += 2
        elif a == "--scale": o["scale"] = int(argv[i + 1]); i += 2
        elif a == "--samples": o["samples"] = int(argv[i + 1]); i += 2
        else: i += 1
    return o


def new_scene(w=1280, h=720, fps=30, samples=24):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.shading_system = True
    sc.cycles.samples = samples
    sc.cycles.use_denoising = False
    sc.cycles.max_bounces = 0
    sc.cycles.transparent_max_bounces = 24
    sc.cycles.pixel_filter_type = "BLACKMAN_HARRIS"
    sc.cycles.filter_width = 1.5
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = 100
    sc.render.fps = fps
    sc.world = bpy.data.worlds.new("W")
    sc.world.use_nodes = True
    sc.world.node_tree.nodes["Background"].inputs[0].default_value = (0, 0, 0, 1)
    sc.world.node_tree.nodes["Background"].inputs[1].default_value = 0.0
    return sc


def camera(sc, lens=50, clip=(0.005, 200)):
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.lens = lens
    cam.data.clip_start, cam.data.clip_end = clip
    tgt = bpy.data.objects.new("CamTarget", None)
    sc.collection.objects.link(tgt)
    tr = cam.constraints.new("TRACK_TO")
    tr.target = tgt; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"
    return cam, tgt


def key_cam(cam, tgt, frame, loc, look, lens=None, roll=None):
    cam.location = loc; tgt.location = look
    cam.keyframe_insert("location", frame=frame)
    tgt.keyframe_insert("location", frame=frame)
    if lens is not None:
        cam.data.lens = lens
        cam.data.keyframe_insert("lens", frame=frame)
    if roll is not None:
        # roll via the up vector of the constraint isn't keyable; rotate the target's child instead: use delta
        cam.delta_rotation_euler = (0, 0, math.radians(roll))
        cam.keyframe_insert("delta_rotation_euler", frame=frame)


def osl_node(nt, file, params=None):
    s = nt.nodes.new("ShaderNodeScript")
    s.mode = "EXTERNAL"
    s.filepath = os.path.join(OSL_DIR, file)
    s.update()
    for k, v in (params or {}).items():
        sock = s.inputs[k]
        if sock.type == "RGBA" and len(v) == 3:
            v = (*v, 1.0)
        sock.default_value = v
    return s


def additive_material(name, osl_file, params=None, out="Color"):
    """Emission added on top of whatever is behind (transparent + emission)."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    s = osl_node(nt, osl_file, params)
    em = nt.nodes.new("ShaderNodeEmission")
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    add = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(s.outputs[out], em.inputs["Color"])
    nt.links.new(tr.outputs[0], add.inputs[0])
    nt.links.new(em.outputs[0], add.inputs[1])
    nt.links.new(add.outputs[0], o.inputs["Surface"])
    return mat, s, em


def emission_material(name, osl_file, params=None, out="Color"):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    s = osl_node(nt, osl_file, params)
    em = nt.nodes.new("ShaderNodeEmission")
    nt.links.new(s.outputs[out], em.inputs["Color"])
    nt.links.new(em.outputs[0], o.inputs["Surface"])
    return mat, s, em


def plane(name, mat, size=1.0, loc=(0, 0, 0), billboard_to=None, sx=1.0, sy=1.0):
    me = bpy.data.meshes.new(name)
    h = 1.0
    me.from_pydata([(-h, -h, 0), (h, -h, 0), (h, h, 0), (-h, h, 0)], [], [(0, 1, 2, 3)])
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = loc
    ob.scale = (size * sx, size * sy, size)
    me.materials.append(mat)
    if billboard_to is not None:
        c = ob.constraints.new("TRACK_TO")
        c.target = billboard_to; c.track_axis = "TRACK_Z"; c.up_axis = "UP_Y"
    return ob


def cam_vignette(cam, strength=0.7, dist=0.02, inner=0.32, outer=0.85, aspect=1.7778):
    """A black card in front of the lens whose opacity is a radial function of *screen* position (Window coords),
    so the falloff is exact whatever the lens does."""
    mat = bpy.data.materials.new("Vignette")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sub = nt.nodes.new("ShaderNodeVectorMath"); sub.operation = "SUBTRACT"; sub.inputs[1].default_value = (0.5, 0.5, 0.0)
    scl = nt.nodes.new("ShaderNodeVectorMath"); scl.operation = "MULTIPLY"; scl.inputs[1].default_value = (aspect, 1.12, 0.0)
    ln = nt.nodes.new("ShaderNodeVectorMath"); ln.operation = "LENGTH"
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.interpolation_type = "SMOOTHSTEP"
    mr.inputs["From Min"].default_value = inner; mr.inputs["From Max"].default_value = outer
    mr.inputs["To Min"].default_value = 0.0; mr.inputs["To Max"].default_value = strength
    nt.links.new(tc.outputs["Window"], sub.inputs[0]); nt.links.new(sub.outputs[0], scl.inputs[0])
    nt.links.new(scl.outputs[0], ln.inputs[0]); nt.links.new(ln.outputs["Value"], mr.inputs["Value"])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (0, 0, 0, 1)
    mx = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(mr.outputs["Result"], mx.inputs["Fac"])
    nt.links.new(tr.outputs[0], mx.inputs[1]); nt.links.new(em.outputs[0], mx.inputs[2])
    nt.links.new(mx.outputs[0], o.inputs["Surface"])
    card = plane("VignetteCard", mat, size=dist * 3.0)      # far larger than the view
    card.parent = cam
    card.location = (0, 0, -dist)
    return card, mr


def key(obj, path, frame, value=None, interp=None):
    if value is not None:
        if isinstance(path, str) and path.startswith("["):
            obj[path[2:-2]] = value
        else:
            setattr(obj, path, value)
    obj.keyframe_insert(path, frame=frame)
    if interp:
        ad = obj.animation_data if hasattr(obj, "animation_data") else None
        if ad and ad.action:
            for fc in ad.action.fcurves:
                for kp in fc.keyframe_points:
                    if abs(kp.co.x - frame) < 0.5:
                        kp.interpolation = interp


def key_socket(sock, frame, value):
    if sock.type == "RGBA" and hasattr(value, "__len__") and len(value) == 3:
        value = (*value, 1.0)
    sock.default_value = value
    sock.keyframe_insert("default_value", frame=frame)


def compositor(sc, fog=(1.0, 8, 0.0), streaks=None, dispersion=0.0, vignette=0.0, exposure=0.0):
    vignette = 0.0   # vignettes are camera cards now (see cam_vignette)
    """Glare bloom (+ optional star streaks), lens dispersion, vignette."""
    sc.use_nodes = True
    nt = sc.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    rl = nt.nodes.new("CompositorNodeRLayers")
    comp = nt.nodes.new("CompositorNodeComposite")
    src = rl.outputs["Image"]
    if exposure:
        ex = nt.nodes.new("CompositorNodeExposure"); ex.inputs["Exposure"].default_value = exposure
        nt.links.new(src, ex.inputs["Image"]); src = ex.outputs["Image"]
    if fog:
        g = nt.nodes.new("CompositorNodeGlare")
        g.glare_type = "FOG_GLOW"; g.quality = "HIGH"; g.threshold = fog[0]; g.size = fog[1]; g.mix = fog[2]
        nt.links.new(src, g.inputs["Image"]); src = g.outputs["Image"]
    if streaks:
        g = nt.nodes.new("CompositorNodeGlare")
        g.glare_type = "STREAKS"; g.quality = "HIGH"; g.threshold = streaks[0]; g.streaks = streaks[1]
        g.angle_offset = math.radians(streaks[2]); g.fade = streaks[3]; g.mix = streaks[4] if len(streaks) > 4 else 0.0
        nt.links.new(src, g.inputs["Image"]); src = g.outputs["Image"]
    if dispersion:
        ld = nt.nodes.new("CompositorNodeLensdist")
        ld.inputs["Dispersion"].default_value = dispersion
        ld.use_fit = True
        nt.links.new(src, ld.inputs["Image"]); src = ld.outputs["Image"]
    if vignette:
        em = nt.nodes.new("CompositorNodeEllipseMask")
        em.width, em.height = 0.95, 0.92
        bl = nt.nodes.new("CompositorNodeBlur")
        bl.filter_type = "FAST_GAUSS"; bl.use_relative = True; bl.factor_x = bl.factor_y = 30
        nt.links.new(em.outputs["Mask"], bl.inputs["Image"])
        mx = nt.nodes.new("CompositorNodeMixRGB"); mx.blend_type = "MULTIPLY"
        mx.inputs["Fac"].default_value = vignette
        ramp = nt.nodes.new("CompositorNodeMapRange")
        ramp.inputs["From Min"].default_value = 0; ramp.inputs["From Max"].default_value = 1
        ramp.inputs["To Min"].default_value = 0.25; ramp.inputs["To Max"].default_value = 1
        nt.links.new(bl.outputs["Image"], ramp.inputs["Value"])
        nt.links.new(src, mx.inputs[1]); nt.links.new(ramp.outputs["Value"], mx.inputs[2])
        src = mx.outputs["Image"]
    nt.links.new(src, comp.inputs["Image"])
    return nt


def render_insert(sc, name, frames, fps=30, blend="alpha", preview=False, only_frames=None, quality=88, scale=100):
    """Render frames [0, frames) to the mod's insert folder (or blender/inserts/previews when previewing)."""
    if preview:
        out = os.path.join(ROOT, "inserts", "previews", name)
    else:
        out = os.path.join(ASSETS, name)
    os.makedirs(out, exist_ok=True)
    sc.render.resolution_percentage = scale
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = quality
    sc.render.image_settings.color_mode = "RGB"
    todo = only_frames if only_frames is not None else range(frames)
    for f in todo:
        sc.frame_set(f)
        sc.render.filepath = os.path.join(out, f"{f:03d}.jpg")
        bpy.ops.render.render(write_still=True)
        print(f"[insert] {name} frame {f}/{frames}", flush=True)
    if not preview:
        with open(os.path.join(out, "meta.json"), "w") as fp:
            json.dump({"fps": fps, "frames": frames, "ext": "jpg", "blend": blend}, fp)
    return out
