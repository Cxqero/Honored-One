"""Shared helpers for headless VFX texture rendering (Blender 4.5, Cycles + OSL).

Every asset script builds its scene from scratch so renders are reproducible:
    blender -b --factory-startup -P assets/<script>.py -- --out <dir>
"""
import bpy, os, sys, math

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OSL_DIR = os.path.join(ROOT, "osl")


def args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out = {"out": os.path.join(ROOT, "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "textures", "vfx"),
           "preview": False}
    i = 0
    while i < len(argv):
        if argv[i] == "--out":
            out["out"] = argv[i + 1]; i += 2
        elif argv[i] == "--preview":
            out["preview"] = True; i += 1
        else:
            i += 1
    os.makedirs(out["out"], exist_ok=True)
    return out


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    import device; device.use_best(sc)          # CPU; the GPU with GOJO_BLENDER_DEVICE=GPU
    sc.cycles.shading_system = True          # OSL
    sc.cycles.use_denoising = False
    sc.cycles.max_bounces = 0
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.view_settings.exposure = 0.0
    sc.view_settings.gamma = 1.0
    sc.render.film_transparent = False
    sc.world = bpy.data.worlds.new("World")
    sc.world.use_nodes = True
    bg = sc.world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0, 0, 0, 1)
    bg.inputs[1].default_value = 0.0
    return sc


def ortho_camera(sc, aspect=1.0):
    cam_data = bpy.data.cameras.new("Cam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = 2.0 * max(1.0, aspect)
    cam = bpy.data.objects.new("Cam", cam_data)
    sc.collection.objects.link(cam)
    cam.location = (0, 0, 5)
    sc.camera = cam
    return cam


def osl_plane(sc, osl_file, params=None, aspect=1.0, name="Plane"):
    """A plane covering the ortho view (x in [-aspect,aspect], y in [-1,1]) with an OSL emission shader.
    The OSL shader receives P in object space; we pass uv in [0,1]^2 via 'UV'."""
    bpy.ops.mesh.primitive_plane_add(size=2.0)
    pl = bpy.context.active_object
    pl.name = name
    pl.scale = (aspect, 1.0, 1.0)
    mat = bpy.data.materials.new(name + "Mat")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    script = nt.nodes.new("ShaderNodeScript")
    script.mode = "EXTERNAL"
    script.filepath = os.path.join(OSL_DIR, osl_file)
    script.update()
    emit = nt.nodes.new("ShaderNodeEmission")
    nt.links.new(script.outputs["Color"], emit.inputs["Color"])
    emit.inputs["Strength"].default_value = 1.0
    nt.links.new(emit.outputs[0], out.inputs["Surface"])
    if params:
        for k, v in params.items():
            if k in script.inputs:
                script.inputs[k].default_value = v
            else:
                raise KeyError(f"OSL input '{k}' not found in {osl_file}: {[i.name for i in script.inputs]}")
    pl.data.materials.append(mat)
    return pl, script


def resolution(sc, w, h, samples=16):
    sc.render.resolution_x = w
    sc.render.resolution_y = h
    sc.render.resolution_percentage = 100
    sc.cycles.samples = samples
    sc.cycles.pixel_filter_type = "BLACKMAN_HARRIS"
    sc.cycles.filter_width = 1.5


def glare(sc, kind="FOG_GLOW", threshold=1.0, size=8, mix=0.0, streaks=4, angle_offset=0.0, fade=0.9, quality="HIGH"):
    """Real compositor bloom/streaks on top of the render."""
    sc.use_nodes = True
    nt = sc.node_tree
    rl = nt.nodes.get("Render Layers") or nt.nodes.new("CompositorNodeRLayers")
    comp = nt.nodes.get("Composite") or nt.nodes.new("CompositorNodeComposite")
    g = nt.nodes.new("CompositorNodeGlare")
    g.glare_type = kind
    g.quality = quality
    g.threshold = threshold
    if kind == "FOG_GLOW":
        g.size = size
    if kind == "STREAKS":
        g.streaks = streaks
        g.angle_offset = angle_offset
        g.fade = fade
    g.mix = mix
    # chain: whatever currently feeds the composite becomes this glare's input
    src = rl.outputs["Image"]
    if comp.inputs["Image"].is_linked:
        src = comp.inputs["Image"].links[0].from_socket
    nt.links.new(src, g.inputs["Image"])
    nt.links.new(g.outputs["Image"], comp.inputs["Image"])
    return g


def render(sc, path, color_mode="RGB", depth="8"):
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = color_mode
    sc.render.image_settings.color_depth = depth
    sc.render.image_settings.compression = 90
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print("[vfxlib] wrote", path)
