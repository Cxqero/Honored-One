#!/bin/bash
# One-time setup for a fresh cloud container. Installs what the Blender and audio pipelines need:
#   ffmpeg, numpy/scipy/pillow/nbtlib, and Blender 4.5 LTS as the official PyPI module (bpy 4.5.14, Python 3.11).
# Blender scripts then run through tools/blender (same command line as the real binary).
#
# Building and running the mod additionally needs network access to the Fabric and Mojang hosts listed in
# PROGRESS.md ("How to build / test").
set -e

if ! command -v ffmpeg >/dev/null 2>&1; then
    apt-get update -qq || true
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ffmpeg
fi

pip install -q numpy scipy pillow nbtlib "bpy==4.5.14"

# The PyPI bpy build bundles OSL's Python query module in its own site-packages, which module-mode Python does not
# search. Without it OSL script nodes never compile (they get no sockets). Append that directory to sys.path.
SITE=$(python3 -c "import os, bpy; print(os.path.dirname(os.path.dirname(bpy.__file__)))")
echo "$SITE/bpy/4.5/python/lib/python3.11/site-packages" > "$SITE/zz_blender_bundled.pth"

python3 -c "import bpy, oslquery, _cycles; print('Blender', bpy.app.version_string, '| OSL', _cycles.with_osl)"
