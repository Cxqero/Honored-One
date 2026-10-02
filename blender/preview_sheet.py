"""Tile VFX textures into one preview image (alpha textures shown over a sky-blue background)."""
import sys, os
from PIL import Image, ImageDraw
paths = sys.argv[2:]
out = sys.argv[1]
cell = 300
cols = 4
rows = (len(paths) + cols - 1) // cols
sheet = Image.new("RGB", (cols * cell, rows * (cell + 20)), (20, 20, 20))
d = ImageDraw.Draw(sheet)
for i, p in enumerate(paths):
    im = Image.open(p)
    has_alpha = im.mode == "RGBA"
    im.thumbnail((cell - 10, cell - 10))
    bg = Image.new("RGB", im.size, (135, 180, 230) if has_alpha else (0, 0, 0))
    if has_alpha:
        bg.paste(im, (0, 0), im)
    else:
        bg.paste(im.convert("RGB"))
    x, y = (i % cols) * cell + 5, (i // cols) * (cell + 20) + 5
    sheet.paste(bg, (x, y))
    d.text((x, y + cell - 8), os.path.basename(p), fill=(255, 255, 0))
sheet.save(out)
