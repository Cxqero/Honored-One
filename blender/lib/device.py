"""Render device for every script. The CPU by default: it renders exactly what the earlier checkpoints did. The NVIDIA
GPU (OptiX) is opt-in with GOJO_BLENDER_DEVICE=GPU: simple textures come out identical, but OSL on OptiX cut the
filaments of the nuke_collide insert off at a hard edge, and every run first spends ~8 minutes compiling the OSL
kernels, so it only pays off for long renders that have been checked against a CPU frame.
Call it after read_factory_settings (that resets the preferences).
"""
import bpy, os


def use_best(sc):
    sc.cycles.device = "CPU"
    if os.environ.get("GOJO_BLENDER_DEVICE", "").upper() != "GPU":
        return "CPU"
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        if not any(d.type == "OPTIX" for d in prefs.devices):
            return "CPU"
        for d in prefs.devices:
            d.use = d.type == "OPTIX"
        sc.cycles.device = "GPU"
        return "OPTIX"
    except Exception:
        return "CPU"
