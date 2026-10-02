"""Builds the wiki's banners and screenshots from in-game recordings (frames in .tools/caps/wiki_cand/, taken from
tools/record.ps1 clips of the mod running with Bliss). Output: wiki/images/.
    .tools/venv/Scripts/python tools/make_wiki_art.py
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FR = os.path.join(ROOT, ".tools", "caps", "wiki_cand")
OUT = os.path.join(ROOT, "wiki", "images")
FONTS = os.path.join(ROOT, "blender", "fonts")
SERIF = os.path.join(FONTS, "NotoSerifJP-Bold-subset.otf")
SANS_M = os.path.join(FONTS, "Poppins-Medium.ttf")
SANS_L = os.path.join(FONTS, "Poppins-Light.ttf")
JP_FALLBACK = r"C:\Windows\Fonts\YuGothB.ttc"
PANO = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "gojolimitless", "loops", "void_pano", "000.jpg")
os.makedirs(OUT, exist_ok=True)

CYAN, RED, VIOLET, WHITE = (120, 220, 255), (255, 70, 60), (190, 90, 255), (245, 245, 255)


def frame(name, box=None):
    """A recorded frame; letterbox bars cut off; optional crop box in 0..1 of the remaining picture."""
    im = Image.open(os.path.join(FR, name + ".png")).convert("RGB")
    w, h = im.size
    gray = im.convert("L")
    top = 0
    while top < h // 4 and gray.crop((0, top, w, top + 1)).getextrema()[1] < 12:
        top += 1
    bot = h
    while bot > h * 3 // 4 and gray.crop((0, bot - 1, w, bot)).getextrema()[1] < 12:
        bot -= 1
    im = im.crop((0, top, w, bot))
    if box:
        W, H = im.size
        im = im.crop((int(box[0] * W), int(box[1] * H), int(box[2] * W), int(box[3] * H)))
    return im


def cover(im, w, h, focus=(0.5, 0.5)):
    """Scale to cover w×h and crop around a focus point."""
    s = max(w / im.width, h / im.height)
    im = im.resize((int(im.width * s + 0.5), int(im.height * s + 0.5)), Image.LANCZOS)
    x = int(min(max(focus[0] * im.width - w / 2, 0), im.width - w))
    y = int(min(max(focus[1] * im.height - h / 2, 0), im.height - h))
    return im.crop((x, y, x + w, y + h))


def gradient_mask(w, h, x0, x1, a0=255, a1=0):
    """Horizontal alpha ramp from x0 (a0) to x1 (a1)."""
    m = Image.new("L", (w, h), a0)
    d = ImageDraw.Draw(m)
    for x in range(w):
        k = 0 if x <= x0 else 1 if x >= x1 else (x - x0) / (x1 - x0)
        k = k * k * (3 - 2 * k)
        d.line((x, 0, x, h), fill=int(a0 + (a1 - a0) * k))
    return m


def glow_text(base, xy, text, font, fill, glow, radius=14, strength=2, spacing=0, anchor="la"):
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw_spaced(ImageDraw.Draw(shadow), (xy[0] + 3, xy[1] + 4), text, font, (0, 0, 0, 230), spacing, anchor)
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(4, radius // 2)))
    base.alpha_composite(shadow)
    base.alpha_composite(shadow)
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    draw_spaced(d, xy, text, font, glow + (255,), spacing, anchor)
    g = layer.filter(ImageFilter.GaussianBlur(radius))
    for _ in range(strength):
        base.alpha_composite(g)
    d2 = ImageDraw.Draw(base)
    draw_spaced(d2, xy, text, font, fill + (255,) if len(fill) == 3 else fill, spacing, anchor)


def shadowed(base, xy, text, font, fill, spacing, anchor="la"):
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw_spaced(ImageDraw.Draw(sh), (xy[0] + 2, xy[1] + 2), text, font, (0, 0, 0, 255), spacing, anchor)
    sh = sh.filter(ImageFilter.GaussianBlur(3))
    base.alpha_composite(sh)
    base.alpha_composite(sh)
    draw_spaced(ImageDraw.Draw(base), xy, text, font, fill, spacing, anchor)


def draw_spaced(d, xy, text, font, fill, spacing, anchor="la"):
    if not spacing:
        d.text(xy, text, font=font, fill=fill, anchor=anchor)
        return
    x, y = xy
    if anchor[0] == "r":
        x -= text_width(d, text, font, spacing)
    for ch in text:
        d.text((x, y), ch, font=font, fill=fill, anchor="l" + anchor[1])
        x += d.textlength(ch, font=font) + spacing


def text_width(d, text, font, spacing):
    return sum(d.textlength(c, font=font) for c in text) + spacing * (len(text) - 1)


def vertical(base, x, y, text, font, fill, glow, step):
    for i, ch in enumerate(text):
        glow_text(base, (x, y + i * step), ch, font, fill, glow, radius=10, strength=1, anchor="mt")


def vignette(im, strength=0.65):
    w, h = im.size
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).ellipse((-w * 0.25, -h * 0.6, w * 1.25, h * 1.6), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(min(w, h) * 0.25))
    dark = Image.new("RGB", (w, h), (0, 0, 0))
    return Image.composite(im, Image.blend(im, dark, strength), m)


def accent(base, x, y, w, c0, c1, thick=4):
    d = ImageDraw.Draw(base)
    for i in range(w):
        k = i / max(1, w - 1)
        col = tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3)) + (255,)
        d.line((x + i, y, x + i, y + thick), fill=col)


# ------------------------------------------------------------------ home banner
def home():
    W, H = 1600, 640
    void = cover(Image.open(PANO).convert("RGB"), W, H, (0.32, 0.5))
    void = ImageEnhance.Brightness(void).enhance(0.8)
    face = cover(frame("eyes2"), 900, H, (0.45, 0.55))
    base = void.convert("RGBA")
    base.paste(face, (W - 900, 0), gradient_mask(900, H, 0, 260, 0, 255))
    base = vignette(base.convert("RGB"), 0.55).convert("RGBA")
    shade = Image.new("RGBA", (W, H), (0, 0, 8, 255))
    base = Image.composite(base, shade, gradient_mask(W, H, 0, 760, 120, 255).point(lambda v: v))
    title = ImageFont.truetype(SANS_M, 118)
    glow_text(base, (92, 190), "HONORED ONE", title, WHITE, CYAN, radius=22, strength=2, spacing=10)
    accent(base, 96, 335, 640, CYAN, VIOLET, 4)
    sub = ImageFont.truetype(SANS_L, 30)
    draw_spaced(ImageDraw.Draw(base), (98, 372), "GOJO MOVESET MOD  ·  MINECRAFT 1.21.1  ·  FABRIC", sub, (210, 220, 240, 255), 3)
    kj = ImageFont.truetype(SERIF, 54)
    row = ImageFont.truetype(SANS_L, 22)
    x = 98
    for k, name, col in (("蒼", "BLUE", CYAN), ("赫", "RED", RED), ("茈", "PURPLE", VIOLET), ("無量空処", "UNLIMITED VOID", (210, 230, 255))):
        glow_text(base, (x, 450), k, kj, WHITE, col, radius=12, strength=2)
        wk = ImageDraw.Draw(base).textlength(k, font=kj)
        draw_spaced(ImageDraw.Draw(base), (x, 525), name, row, col + (255,), 2)
        x += int(max(wk, text_width(ImageDraw.Draw(base), name, row, 2))) + 46
    q = ImageFont.truetype(JP_FALLBACK, 38, index=0)
    vertical(base, W - 52, 70, "天上天下唯我独尊", q, (235, 245, 255), CYAN, 62)
    by = ImageFont.truetype(SANS_L, 22)
    ImageDraw.Draw(base).text((W - 92, H - 42), "by Cxqero", font=by, fill=(200, 210, 230, 220), anchor="rm")
    base.convert("RGB").save(os.path.join(OUT, "banner.png"), optimize=True)


# ------------------------------------------------------------------ technique banners
def technique(out, src, focus, kanji, name, hint, col, box=None, kanji_size=190):
    W, H = 1600, 440
    pic = cover(frame(src, box), W, H, focus)
    pic = vignette(pic, 0.45).convert("RGBA")
    shade = Image.new("RGBA", (W, H), (0, 0, 6, 255))
    base = Image.composite(pic, shade, gradient_mask(W, H, 380, 1250, 95, 255))
    kf = ImageFont.truetype(SERIF, kanji_size)
    glow_text(base, (80, H // 2 - 10), kanji, kf, WHITE, col, radius=26, strength=3, anchor="lm")
    kw = ImageDraw.Draw(base).textlength(kanji, font=kf)
    x = 80 + int(kw) + 50
    nf = ImageFont.truetype(SANS_M, 58 if len(name) < 22 else 46)
    glow_text(base, (x, H // 2 - 34), name, nf, WHITE, col, radius=14, strength=1, spacing=4, anchor="ls")
    accent(base, x + 2, H // 2 - 8, 380, col, (255, 255, 255), 3)
    hf = ImageFont.truetype(SANS_L, 26)
    shadowed(base, (x + 2, H // 2 + 38), hint, hf, (225, 230, 245, 255), 3)
    base.convert("RGB").save(os.path.join(OUT, out + ".png"), optimize=True)


def infinity_banner():
    W, H = 1600, 440
    base = cover(Image.open(PANO).convert("RGB"), W, H, (0.7, 0.5))
    face = cover(frame("eyes2"), 760, H, (0.45, 0.55))
    base = base.convert("RGBA")
    base.paste(face, (W - 760, 0), gradient_mask(760, H, 0, 220, 0, 245))
    base = vignette(base.convert("RGB"), 0.5).convert("RGBA")
    kf = ImageFont.truetype(SERIF, 150)
    glow_text(base, (80, H // 2 - 10), "無下限", kf, WHITE, CYAN, radius=26, strength=3, anchor="lm")
    kw = ImageDraw.Draw(base).textlength("無下限", font=kf)
    x = 80 + int(kw) + 50
    nf = ImageFont.truetype(SANS_M, 58)
    glow_text(base, (x, H // 2 - 34), "INFINITY", nf, WHITE, CYAN, radius=14, strength=1, spacing=6, anchor="ls")
    accent(base, x + 2, H // 2 - 8, 380, CYAN, (255, 255, 255), 3)
    shadowed(base, (x + 2, H // 2 + 38), "ALWAYS ON  ·  N TO TOGGLE", ImageFont.truetype(SANS_L, 26), (225, 230, 245, 255), 3)
    base.convert("RGB").save(os.path.join(OUT, "banner_infinity.png"), optimize=True)


def shots():
    for name, box in (("void2", None), ("silhouette", None), ("collide", None), ("tunnel", None), ("p200_mass", (0, 0, 1, 0.82)),
                      ("red_rays", (0, 0, 1, 0.84)), ("converge", (0, 0, 1, 0.84)), ("seal", (0, 0, 1, 0.84)), ("blue_orbit", None),
                      ("eyes2", None), ("air", (0, 0, 1, 0.84)), ("redsign", (0, 0, 1, 0.84))):
        im = frame(name, box)
        im.thumbnail((1280, 720), Image.LANCZOS)
        im.save(os.path.join(OUT, "shot_" + name + ".jpg"), quality=90)


if __name__ == "__main__":
    home()
    technique("banner_blue", "blue_orbit", (0.45, 0.45), "蒼", "LAPSE: BLUE", "TAP R  ·  HOLD R FOR MAXIMUM OUTPUT", CYAN)
    technique("banner_red", "red_rays", (0.5, 0.42), "赫", "REVERSAL: RED", "TAP G  ·  HOLD G FOR THE INCANTATION", RED, box=(0, 0, 1, 0.84))
    technique("banner_purple", "purple_form", (0.55, 0.5), "茈", "HOLLOW PURPLE", "TAP V  ·  HOLD V FOR 200%", VIOLET, box=(0, 0, 1, 0.84))
    technique("banner_nuke", "converge", (0.5, 0.45), "虚式", "REMOTE HOLLOW PURPLE", "TAP B  ·  AIM AT YOUR TARGET", VIOLET, box=(0, 0, 1, 0.84), kanji_size=160)
    technique("banner_domain", "void2", (0.5, 0.5), "無量空処", "UNLIMITED VOID", "TAP Z: 0.2 SECONDS  ·  HOLD Z: FULL DOMAIN", (200, 225, 255), kanji_size=130)
    infinity_banner()
    shots()
    print("wiki art written to", OUT)
