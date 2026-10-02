"""Showcase GIFs stored in the repository, one per technique. The imgur originals (640 px, 4.5-45 MB) didn't show on
GitHub: images from other sites go through GitHub's image proxy, which drops files over ~5 MB. Images in the repository
itself are served directly, so the only goal here is a reasonable page weight: 480 px, a palette per GIF, and only the
changed part of each frame stored. Sources: .tools/gifs/<name>.gif. Output: wiki/images/gifs/<name>.gif.
    .tools/venv/Scripts/python tools/make_gifs.py
"""
import os, subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, ".tools", "gifs")
OUT = os.path.join(ROOT, "wiki", "images", "gifs")
FF = os.path.join(ROOT, ".tools", "ffmpeg", "bin", "ffmpeg.exe")
WIDTH = 480
NAMES = ["blue", "red", "purple", "purple_200", "nuke", "domain", "domain_instant"]

os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):                                   # one GIF per technique: nothing else stays in here
    if f.endswith(".gif") and f[:-4] not in NAMES:
        os.remove(os.path.join(OUT, f))
for name in NAMES:
    dst = os.path.join(OUT, name + ".gif")
    vf = (f"scale={WIDTH}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];"
          f"[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle")
    subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y", "-i", os.path.join(SRC, name + ".gif"), "-vf", vf,
                    "-loop", "0", dst], check=True)
    print(f"{name:16s} {os.path.getsize(dst) / 1048576:5.1f} MB", flush=True)
