"""An original Gojo-style Minecraft skin (64x64, wide arms), drawn procedurally as pixel art.

Used for Blender previews and headless test shots (so they look like the players who'll use this mod), and offered
as an optional skin. White spiky hair falling over the forehead, cyan eyes, the black high-collar Jujutsu High
uniform with its spiral button, skin-coloured hands (the blocky fingers take their colour from the hand pixels).

    python3 tools/make_gojo_skin.py <out.png>
"""
import sys
from PIL import Image

W = 64
img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
px = img.load()

SKIN, SKIN_S, SKIN_D = (242, 214, 196, 255), (226, 190, 172, 255), (205, 164, 148, 255)
HAIR, HAIR_S, HAIR_D = (246, 247, 252, 255), (214, 218, 232, 255), (182, 186, 206, 255)
EYE, EYE_D, EYE_W = (95, 222, 245, 255), (40, 150, 200, 255), (255, 255, 255, 255)
CLOTH, CLOTH_S, CLOTH_L = (24, 24, 32, 255), (16, 16, 22, 255), (40, 42, 56, 255)
GOLD = (214, 176, 80, 255)
PANTS, PANTS_S = (22, 22, 30, 255), (15, 15, 20, 255)
SHOE, SHOE_L = (12, 12, 14, 255), (40, 40, 44, 255)
MOUTH = (190, 130, 120, 255)


def rect(x0, y0, w, h, c):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = c


def box(u, v, sx, sy, sz, fill):
    """Fill all six faces of a box's UV layout with fill(face, x, y) (face in top/bottom/right/front/left/back)."""
    faces = {
        "top": (u + sz, v, sx, sz), "bottom": (u + sz + sx, v, sx, sz),
        "right": (u, v + sz, sz, sy), "front": (u + sz, v + sz, sx, sy),
        "left": (u + sz + sx, v + sz, sz, sy), "back": (u + sz + sx + sz, v + sz, sx, sy),
    }
    for name, (x0, y0, w, h) in faces.items():
        for y in range(h):
            for x in range(w):
                c = fill(name, x, y, w, h)
                if c:
                    px[x0 + x, y0 + y] = c


# ------------------------------------------------------------------ head (8x8x8 at 0,0) + hat layer (32,0)
def head(face, x, y, w, h):
    if face == "top":
        return HAIR if (x + y) % 3 else HAIR_S
    if face == "bottom":
        return SKIN_D
    if face == "front":
        # hair: full top two rows, bangs falling in spikes over the forehead
        bang = [3, 2, 3, 4, 3, 2, 3, 3][x]
        if y < bang:
            return HAIR if (x % 3) else HAIR_S
        if y == bang and x in (2, 5):
            return HAIR_S
        # eyes on row 4: two cyan pixels each with a white highlight, lashes above in hair shade
        if y == 4 and x in (1, 2, 5, 6):
            return EYE_W if x in (1, 6) else EYE
        if y == 5 and x in (1, 2, 5, 6):
            return EYE_D if x in (2, 5) else SKIN_S
        if y == 7 and x in (3, 4):
            return MOUTH
        return SKIN
    if face in ("right", "left"):
        if y < 4 or (y < 6 and ((x < 3) if face == "left" else (x > 4))):
            return HAIR if (x + y) % 2 else HAIR_S
        return SKIN_S
    if face == "back":
        return HAIR if (x + y) % 3 else (HAIR_S if y < 6 else HAIR_D)


box(0, 0, 8, 8, 8, head)


def hat(face, x, y, w, h):
    # spiky volume: a ragged fringe of hair on the outer layer
    if face == "top":
        return HAIR if (x * 7 + y * 3) % 5 else HAIR_S
    if face == "front":
        spikes = [1, 0, 1, 2, 1, 0, 1, 1]
        if y <= spikes[x]:
            return HAIR
        if y == spikes[x] + 1 and x in (0, 3, 7):
            return HAIR_S
        return None
    if face in ("right", "left", "back"):
        edge = [3, 5, 4, 6, 3, 5, 4, 5][x]
        if y < edge:
            return HAIR if (x + y) % 3 else HAIR_S
        return None
    return None


box(32, 0, 8, 8, 8, hat)


# ------------------------------------------------------------------ body (16,16) + jacket (16,32)
def body(face, x, y, w, h):
    if face == "top":
        return CLOTH_L if y < 2 else CLOTH
    if face == "front":
        # high collar: the top two rows, with the gold spiral button at the throat
        if y < 2:
            if x in (3, 4) and y == 1:
                return GOLD
            return CLOTH_L
        if x in (3, 4) and y in (5, 9):
            return CLOTH_L         # buttons / seam
        return CLOTH if x not in (0, 7) else CLOTH_S
    return CLOTH if (face != "back" or y > 1) else CLOTH_L


box(16, 16, 8, 12, 4, body)


def jacket(face, x, y, w, h):
    # the collar stands out a little from the neck
    if face == "front" and y < 2:
        return CLOTH_L if not (x in (3, 4) and y == 1) else GOLD
    if face in ("right", "left", "back") and y < 2:
        return CLOTH_L
    return None


box(16, 32, 8, 12, 4, jacket)


# ------------------------------------------------------------------ arms: sleeves with skin-coloured hands (last 3 rows)
def arm(face, x, y, w, h):
    if face == "top":
        return CLOTH_L
    if face == "bottom":
        return SKIN_S
    if y >= h - 3:
        return SKIN if face != "back" else SKIN_S
    if y == h - 4:
        return CLOTH_L          # cuff
    return CLOTH if (x + y) % 5 else CLOTH_S


SLIM = "--slim" in sys.argv
AW = 3 if SLIM else 4
box(40, 16, AW, 12, 4, arm)
box(32, 48, AW, 12, 4, arm)


# ------------------------------------------------------------------ legs (0,16) and (16,48)
def leg(face, x, y, w, h):
    if face == "bottom":
        return SHOE
    if y >= h - 2:
        return SHOE if y == h - 1 else SHOE_L
    return PANTS if (x + y) % 4 else PANTS_S


box(0, 16, 4, 12, 4, leg)
box(16, 48, 4, 12, 4, leg)

args = [a for a in sys.argv[1:] if not a.startswith("--")]
img.save(args[0] if args else "gojo_skin.png")
