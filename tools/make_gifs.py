"""Showcase GIFs small enough for GitHub (its image proxy drops files over ~5 MB), stored in the repo.
Each source GIF gets the largest width that fits under LIMIT; clips too long for that are split into parts
(nothing cut out). Sources: .tools/gifs/<name>.gif (the imgur originals). Output: wiki/images/gifs/.
    .tools/venv/Scripts/python tools/make_gifs.py
"""
import os, subprocess, math, json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, ".tools", "gifs")
OUT = os.path.join(ROOT, "wiki", "images", "gifs")
FF = os.path.join(ROOT, ".tools", "ffmpeg", "bin", "ffmpeg.exe")
FP = os.path.join(ROOT, ".tools", "ffmpeg", "bin", "ffprobe.exe")
LIMIT = 4.7 * 1024 * 1024
WIDTHS = [560, 520, 480, 440, 400]          # never smaller than 400: split into parts instead
NAMES = ["blue", "red", "purple", "purple_200", "nuke", "domain", "domain_instant"]
os.makedirs(OUT, exist_ok=True)


def duration(path):
    r = subprocess.run([FP, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True)
    return float(r.stdout.strip())


def encode(src, dst, width, start=None, length=None):
    cut = []
    if start is not None:
        cut = ["-ss", f"{start:.2f}", "-t", f"{length:.2f}"]
    vf = (f"scale={width}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];"
          f"[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle")
    subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y", *cut, "-i", src, "-vf", vf, "-loop", "0", dst], check=True)
    return os.path.getsize(dst)


def best(src, dst, start=None, length=None):
    for w in WIDTHS:
        size = encode(src, dst, w, start, length)
        if size <= LIMIT:
            return w, size
    return None, size


report = {}
for name in NAMES:
    src = os.path.join(SRC, name + ".gif")
    dur = duration(src)
    parts = 1
    while True:
        seg = dur / parts
        outs = []
        ok = True
        for i in range(parts):
            dst = os.path.join(OUT, f"{name}.gif" if parts == 1 else f"{name}_{i + 1}.gif")
            w, size = best(src, dst, None if parts == 1 else i * seg, None if parts == 1 else seg + 0.05)
            if w is None:
                ok = False
                break
            outs.append((os.path.basename(dst), w, round(size / 1048576, 2)))
        if ok:
            break
        for f in os.listdir(OUT):                      # failed split: clear its files and try more parts
            if f.startswith(name + "_") or f == name + ".gif":
                os.remove(os.path.join(OUT, f))
        parts += 1
    report[name] = {"seconds": round(dur, 1), "parts": outs}
    print(name, json.dumps(report[name]), flush=True)
