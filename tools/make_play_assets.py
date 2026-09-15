#!/usr/bin/env python3
"""Generate the two required Play Store listing assets:

  store_assets/icon_512.png      -> 512x512 32-bit PNG app icon (max 1MB)
  store_assets/feature_graphic.png -> 1024x500 promotional banner
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.join("tools"))
from gen_icons import draw_icon  # noqa: E402

OUT = "store_assets"
os.makedirs(OUT, exist_ok=True)

GOLD = (255, 229, 119)
PINK_L = (255, 138, 190)
FONT_BOLD = "arialbd.ttf"
FONT_REG = "arial.ttf"


def load_font(name, size):
    for cand in (name, "C:/Windows/Fonts/" + name):
        try:
            return ImageFont.truetype(cand, size)
        except Exception:
            continue
    return ImageFont.load_default()


# ---------------------------------------------------------------- icon 512
icon = draw_icon(512)
icon.save(os.path.join(OUT, "icon_512.png"), "PNG", optimize=True)
print("wrote icon_512.png", icon.size, os.path.getsize(os.path.join(OUT, "icon_512.png")), "bytes")


# ---------------------------------------------------------------- feature graphic
W2, H2 = 1024, 500
img = Image.new("RGB", (W2, H2))
d = ImageDraw.Draw(img)
top, mid, bot = (26, 21, 80), (106, 63, 191), (255, 111, 181)
for y in range(H2):
    t = y / H2
    if t < 0.55:
        k = t / 0.55
        c = tuple(int(top[i] + (mid[i] - top[i]) * k) for i in range(3))
    else:
        k = (t - 0.55) / 0.45
        c = tuple(int(mid[i] + (bot[i] - mid[i]) * k) for i in range(3))
    d.line([(0, y), (W2, y)], fill=c)

# moon top-left
d.ellipse([40, -70, 170, 60], fill=(255, 245, 225))
d.ellipse([62, -52, 148, 34], fill=(252, 240, 218))

# soft glow behind Grace
glow = Image.new("RGBA", (W2, H2), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([600, 20, 1010, 430], fill=(255, 255, 255, 70))
glow = glow.filter(ImageFilter.GaussianBlur(60))
img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")

# Grace = the 512 icon pasted at right, slightly smaller
grace = draw_icon(400)
img.paste(grace, (630, 50), grace)

d = ImageDraw.Draw(img)

# sparkles, avoiding the text band (x < 600, y 60..240) and the icon area
random.seed(23)
def sparkle(cx, cy, r, color):
    wdt = max(2, int(r * 0.22))
    for ang in (0, 90, 180, 270):
        a = math.radians(ang)
        d.line([(cx, cy), (cx + math.cos(a) * r, cy + math.sin(a) * r)], fill=color, width=wdt)
    d.ellipse([cx - r * 0.12, cy - r * 0.12, cx + r * 0.12, cy + r * 0.12], fill=color)

placed = 0
while placed < 20:
    x = random.randint(16, W2 - 16)
    y = random.randint(16, H2 - 16)
    if 550 < x and 30 < y < 470:      # keep Grace zone clean
        continue
    if x < 600 and 40 < y < 260:      # keep title zone clean
        continue
    r = random.randint(4, 11)
    col = random.choice([GOLD, (255, 255, 255), PINK_L])
    sparkle(x, y, r, col)
    placed += 1

# title block (left side) — shrink font until it fits the 600px band
size = 92
while size > 40:
    f_big = load_font(FONT_BOLD, size)
    if d.textlength("SKY DREAMS", font=f_big) <= 560:
        break
    size -= 2
f_sub = load_font(FONT_REG, 34)
f_tag = load_font(FONT_REG, 27)

title = "SKY DREAMS"
tw = d.textlength(title, font=f_big)
d.text(((600 - tw) / 2 + 4, 104 + 4), title, font=f_big, fill=(30, 15, 60))
d.text(((600 - tw) / 2, 104), title, font=f_big, fill=(255, 255, 255))

sub = "Grace, la princesa de las nubes"
tw2 = d.textlength(sub, font=f_sub)
d.text(((600 - tw2) / 2, 224), sub, font=f_sub, fill=GOLD)

tag = "Salta entre nubes • Combos • Gemas • Música mágica"
tw3 = d.textlength(tag, font=f_tag)
d.text(((W2 - tw3) / 2, 448), tag, font=f_tag, fill=(255, 235, 250))

img.save(os.path.join(OUT, "feature_graphic.png"), "PNG", optimize=True)
print("wrote feature_graphic.png", img.size, os.path.getsize(os.path.join(OUT, "feature_graphic.png")), "bytes")
