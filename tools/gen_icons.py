#!/usr/bin/env python3
"""Render legacy launcher PNG icons for Sky Dreams (API < 26)."""
import os
from PIL import Image, ImageDraw

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
RES = os.path.join("app", "src", "main", "res")


def draw_icon(size):
    s = size / 48.0  # scale factor relative to 48px

    def px(v):
        return v * s

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # vertical gradient background (rounded)
    top = (26, 21, 80)
    mid1 = (106, 63, 191)
    mid2 = (255, 111, 181)
    bot = (255, 177, 214)
    grad = Image.new("RGBA", (size, size))
    gd = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / max(1, size - 1)
        if t < 0.45:
            k = t / 0.45
            c = tuple(int(top[i] + (mid1[i] - top[i]) * k) for i in range(3))
        elif t < 0.78:
            k = (t - 0.45) / 0.33
            c = tuple(int(mid1[i] + (mid2[i] - mid1[i]) * k) for i in range(3))
        else:
            k = (t - 0.78) / 0.22
            c = tuple(int(mid2[i] + (bot[i] - mid2[i]) * k) for i in range(3))
        gd.line([(0, y), (size, y)], fill=c + (255,))

    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=px(9), fill=255)
    img.paste(grad, (0, 0), mask)

    # glow behind cloud
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gld = ImageDraw.Draw(glow)
    gld.ellipse([px(24), px(28), px(84), px(88)], fill=(255, 255, 255, 50))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)

    # sparkles (4-point stars)
    def sparkle(cx, cy, r, color):
        d.polygon(
            [
                (cx, cy - r), (cx + r * 0.28, cy - r * 0.28),
                (cx + r, cy), (cx + r * 0.28, cy + r * 0.28),
                (cx, cy + r), (cx - r * 0.28, cy + r * 0.28),
                (cx - r, cy), (cx - r * 0.28, cy - r * 0.28),
            ],
            fill=color,
        )

    sparkle(px(12), px(18), px(5), (255, 229, 119, 255))
    sparkle(px(38), px(12), px(3.5), (255, 255, 255, 255))
    sparkle(px(40), px(33), px(3), (209, 236, 255, 255))

    # cloud
    d.ellipse([px(8), px(27), px(20), px(39)], fill=(255, 255, 255, 255))
    d.ellipse([px(16), px(24), px(31), px(39)], fill=(255, 255, 255, 255))
    d.ellipse([px(27), px(26), px(39), px(39)], fill=(255, 255, 255, 255))
    d.rectangle([px(8), px(33), px(39), px(39)], fill=(255, 255, 255, 255))

    # princess
    d.polygon([(px(24), px(21)), (px(29), px(31)), (px(19), px(31))], fill=(255, 104, 166, 255))  # dress
    d.ellipse([px(21), px(13.5), px(27), px(19.5)], fill=(255, 208, 180, 255))  # head
    d.pieslice([px(20.7), px(13), px(27.3), px(19.6)], 180, 360, fill=(122, 63, 160, 255))  # hair
    d.polygon(                                                                  # crown
        [(px(22), px(13)), (px(23), px(15)), (px(24), px(13.6)),
         (px(25), px(15)), (px(26), px(13)), (px(25.8), px(16)), (px(22.2), px(16))],
        fill=(255, 229, 119, 255),
    )
    # wand
    d.line([(px(29), px(26)), (px(33), px(20))], fill=(255, 229, 119, 255), width=max(1, int(px(1.6))))
    sparkle(px(34.5), px(18.5), px(2.8), (255, 243, 173, 255))

    return img


for dpi, sz in SIZES.items():
    out_dir = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(out_dir, exist_ok=True)
    icon = draw_icon(sz)
    icon.save(os.path.join(out_dir, "ic_launcher.png"))
    icon.save(os.path.join(out_dir, "ic_launcher_round.png"))
    print("wrote", dpi, sz)

print("done")
