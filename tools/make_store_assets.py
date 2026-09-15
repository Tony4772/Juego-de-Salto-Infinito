#!/usr/bin/env python3
"""Compose Play Store ready assets from shots_raw/.

Outputs (into store_assets/):
  screenshot_1..5.png  -> 1080x1920, game art cropped/fit + title banner + phone bezel effect
  feature_graphic.png  -> 1024x500 promotional banner
"""
import os
from PIL import Image, ImageDraw, ImageFont

RAW = "shots_raw"
OUT = "store_assets"
os.makedirs(OUT, exist_ok=True)

W, H = 1080, 1920
PINK = (255, 104, 166)
GOLD = (255, 229, 119)
NAVY = (26, 21, 80)

FONT_BOLD = "arialbd.ttf"
FONT_REG = "arial.ttf"


def load_font(name, size):
    for cand in (name, "C:/Windows/Fonts/" + name, "seguisb.ttf", "segoeui.ttf"):
        try:
            return ImageFont.truetype(cand, size)
        except Exception:
            continue
    return ImageFont.load_default()


def make_screenshot(src_path, dst_path, title=None, subtitle=None):
    """Game art fills the full frame (crop-to-fill 9:16), optional bottom banner."""
    src = Image.open(src_path).convert("RGB")

    # crop-to-fill from 1080x2160 (1:2) to 9:16: keep width, crop height
    target_ratio = H / W            # 1.777
    src_ratio = src.height / src.width  # 2.0
    if src_ratio > target_ratio:
        # source too tall -> crop centered slightly toward the top (HUD/character area)
        new_h = int(src.width * target_ratio)
        top = int((src.height - new_h) * 0.40)
        src = src.crop((0, top, src.width, top + new_h))
    src = src.resize((W, H), Image.LANCZOS)

    img = src.copy()

    if title:
        banner_h = 150
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        # bottom gradient banner
        for i in range(banner_h):
            a = int(215 * (i / banner_h) ** 0.8)
            od.line([(0, H - banner_h + i), (W, H - banner_h + i)], fill=NAVY + (a,))
        img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
        d = ImageDraw.Draw(img)
        f1 = load_font(FONT_BOLD, 58)
        f2 = load_font(FONT_REG, 34)
        tw = d.textlength(title, font=f1)
        d.text(((W - tw) / 2, H - banner_h + 28), title, font=f1, fill=(255, 255, 255))
        if subtitle:
            tw = d.textlength(subtitle, font=f2)
            d.text(((W - tw) / 2, H - banner_h + 98), subtitle, font=f2, fill=(255, 224, 246))

    img.save(dst_path, "PNG", optimize=True)
    print("wrote", dst_path, img.size)


def make_feature_graphic():
    W2, H2 = 1024, 500
    img = Image.new("RGB", (W2, H2))
    d = ImageDraw.Draw(img)
    # diagonal dreamy gradient
    top = (26, 21, 80)
    mid = (106, 63, 191)
    bot = (255, 111, 181)
    for y in range(H2):
        t = y / H2
        if t < 0.55:
            k = t / 0.55
            c = tuple(int(top[i] + (mid[i] - top[i]) * k) for i in range(3))
        else:
            k = (t - 0.55) / 0.45
            c = tuple(int(mid[i] + (bot[i] - mid[i]) * k) for i in range(3))
        d.line([(0, y), (W2, y)], fill=c)

    # decorative sparkles
    import math, random
    random.seed(11)
    def sparkle(cx, cy, r, color):
        for ang in range(0, 360, 90):
            a = math.radians(ang)
            x2 = cx + math.cos(a) * r
            y2 = cy + math.sin(a) * r
            d.line([(cx, cy), (x2, y2)], fill=color, width=max(2, int(r * 0.22)))
        d.ellipse([cx - r*0.12, cy - r*0.12, cx + r*0.12, cy + r*0.12], fill=color)
    for _ in range(16):
        x = random.randint(20, W2 - 20); y = random.randint(20, H2 - 20)
        r = random.randint(4, 12)
        col = random.choice([GOLD, (255, 255, 255), (255, 138, 190)])
        sparkle(x, y, r, col + (255,) if len(col) == 3 else col)

    # moon
    d.ellipse([W2 - 220, -80, W2 - 60, 80], fill=(255, 245, 225))
    d.ellipse([W2 - 195, -60, W2 - 85, 50], fill=tuple(int(c * 0.96) for c in (255, 245, 225)))

    # title
    f_big = load_font(FONT_BOLD, 96)
    f_sub = load_font(FONT_REG, 36)
    title = "SKY DREAMS"
    tw = d.textlength(title, font=f_big)
    # soft shadow
    d.text(((W2 - tw) / 2 + 4, 96 + 4), title, font=f_big, fill=(30, 15, 60))
    d.text(((W2 - tw) / 2, 96), title, font=f_big, fill=(255, 255, 255))
    sub = "Grace, la princesa de las nubes"
    tw2 = d.textlength(sub, font=f_sub)
    d.text(((W2 - tw2) / 2, 212), sub, font=f_sub, fill=GOLD)

    tag = "Salta entre nubes  •  Combos  •  Gemas  •  Música mágica"
    f_tag = load_font(FONT_REG, 28)
    tw3 = d.textlength(tag, font=f_tag)
    d.text(((W2 - tw3) / 2, 435), tag, font=f_tag, fill=(255, 235, 250))

    img.save(os.path.join(OUT, "feature_graphic.png"), "PNG", optimize=True)
    print("wrote feature_graphic.png", img.size)


shots = [
    ("01_menu.png", None, None),
    ("02_gameplay_comboBox.png", "Combos y estrellas doradas", "Recoge en cadena para multiplicar puntos"),
    ("03_gameplay_impulso.png", "¡Plataformas de impulso!", "Vuela más alto que nunca"),
    ("04_nuevo_record.png", "Bate tu propio récord", "Confeti y fanfarria en cada logro"),
    ("05_creditos.png", "Una aventura que sueña alto", "Creado por EBYZOM E.I.R.L."),
]
for i, (fn, t, s) in enumerate(shots, 1):
    make_screenshot(os.path.join(RAW, fn), os.path.join(OUT, f"screenshot_{i}.png"), t, s)

make_feature_graphic()
print("done")
