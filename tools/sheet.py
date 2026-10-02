"""Contact sheet of AutoTest screenshots (Windows/any OS version of tools/mcsheet).
usage: .tools/venv/Scripts/python tools/sheet.py <out.jpg> <scenario> [name ...] [--cols 3] [--width 640]
With no names, every mod/run/screenshots/<scenario>_*.png goes in, sorted by time taken.
"""
import sys, os, glob
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "mod", "run", "screenshots")

args = sys.argv[1:]
cols, width = 3, 640
for flag in ("--cols", "--width"):
    if flag in args:
        i = args.index(flag)
        v = int(args[i + 1]); del args[i:i + 2]
        if flag == "--cols": cols = v
        else: width = v
out, pre, names = args[0], args[1], args[2:]
if names:
    paths = [os.path.join(SHOTS, f"{pre}_{n}.png") for n in names]
else:
    paths = sorted(glob.glob(os.path.join(SHOTS, f"{pre}_*.png")), key=os.path.getmtime)
height = width * 9 // 16
rows = (len(paths) + cols - 1) // cols
sheet = Image.new("RGB", (cols * width, rows * height))
d = ImageDraw.Draw(sheet)
for i, p in enumerate(paths):
    im = Image.open(p).convert("RGB").resize((width, height))
    x, y = (i % cols) * width, (i // cols) * height
    sheet.paste(im, (x, y))
    label = os.path.basename(p)[len(pre) + 1:-4]
    d.rectangle((x, y, x + 8 + 7 * len(label), y + 16), fill=(0, 0, 0))
    d.text((x + 4, y + 2), label, fill=(255, 230, 0))
sheet.save(out, quality=88)
print(out, len(paths), "shots")
