#!/usr/bin/env python3
# Usage: python3 docs/make_icon.py <out-dir>  (needs Pillow). Regenerates the launcher icon drawables and previews.
"""Ironlog icon: the letter I drawn as a standing barbell. Emits vector drawables and PNG previews."""
import sys
from PIL import Image, ImageDraw, ImageFilter

LIME, LIME_DARK = "#D9FF4F", "#A9CC2E"
STEEL_LIGHT, STEEL_DARK, COLLAR = "#F4F6EE", "#C9CDBF", "#8E9484"
BG_IN, BG_OUT = "#262B1D", "#0B0D09"

def mirror(x, y, w, h):  # mirror a top-half shape to the bottom half around y = 54
    return (x, 108 - y - h, w, h)

top = [  # (x, y, w, h, r, color)
    (38, 26, 32, 5.5, 2, LIME_DARK),   # outer plate
    (31, 32.5, 46, 9.5, 2.5, LIME),    # main plate
    (45.5, 42, 17, 3.5, 1, COLLAR),    # collar
]
shapes = []
for x, y, w, h, r, c in top:
    shapes.append((x, y, w, h, r, c))
    mx, my, mw, mh = mirror(x, y, w, h)
    shapes.append((mx, my, mw, mh, r, c))
# Bar (the stem of the I), shaded like a steel cylinder.
shapes.append((49.5, 45.5, 4.5, 17, 0, STEEL_LIGHT))
shapes.append((54, 45.5, 4.5, 17, 0, STEEL_DARK))
# Scale the mark around the centre so it sits comfortably inside every launcher mask.
SCALE = 0.86
shapes = [(54 + (x - 54) * SCALE, 54 + (y - 54) * SCALE, w * SCALE, h * SCALE, r * SCALE, c) for x, y, w, h, r, c in shapes]

def rr(x, y, w, h, r):
    if r == 0:
        return f"M{x:g},{y:g}h{w:g}v{h:g}h{-w:g}z"
    return (f"M{x+r:g},{y:g}h{w-2*r:g}a{r:g},{r:g} 0 0 1 {r:g},{r:g}v{h-2*r:g}"
            f"a{r:g},{r:g} 0 0 1 {-r:g},{r:g}h{-(w-2*r):g}a{r:g},{r:g} 0 0 1 {-r:g},{-r:g}"
            f"v{-(h-2*r):g}a{r:g},{r:g} 0 0 1 {r:g},{-r:g}z")

HEAD = '<vector xmlns:android="http://schemas.android.com/apk/res/android"%s android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
def fg():
    s = HEAD % ""
    s += "    <!-- The letter I as a standing barbell: plates are the serifs, the bar is the stem. -->\n"
    for x, y, w, h, r, c in shapes:
        s += f'    <path android:fillColor="{c}" android:pathData="{rr(round(x,2), round(y,2), round(w,2), round(h,2), round(r,2))}"/>\n'
    return s + "</vector>\n"
def mono():
    s = HEAD % ""
    s += "    <!-- Single-colour silhouette for Android 13+ themed icons. -->\n"
    d = "".join(rr(round(x,2), round(y,2), round(w,2), round(h,2), round(r,2)) for x, y, w, h, r, c in shapes)
    s += f'    <path android:fillColor="#FFFFFFFF" android:pathData="{d}"/>\n'
    return s + "</vector>\n"
def bg():
    s = HEAD % ' xmlns:aapt="http://schemas.android.com/aapt"'
    for cx, cy, rad, a, b in [(54, 38, 80, "#FF" + BG_IN[1:], "#FF" + BG_OUT[1:]), (54, 54, 34, "#2ED9FF4F", "#00D9FF4F")]:
        s += ('    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
              f'            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{rad}" android:startColor="{a}" android:endColor="{b}"/>\n'
              '        </aapt:attr>\n    </path>\n')
    return s + "</vector>\n"

def render(size, mask):
    S = 8; N = size * S; k = N / 108
    def hexc(h, a=255): return tuple(int(h[i:i+2], 16) for i in (1, 3, 5)) + (a,)
    img = Image.new("RGBA", (N, N))
    px = img.load()
    import math
    c0, c1 = hexc(BG_IN), hexc(BG_OUT)
    small = Image.new("RGBA", (108, 108)); sp = small.load()
    for yy in range(108):
        for xx in range(108):
            t = min(1, math.hypot(xx - 54, yy - 38) / 80)
            col = [c0[i] * (1 - t) + c1[i] * t for i in range(3)]
            g = max(0, 1 - math.hypot(xx - 54, yy - 54) / 34) * 0x2E / 255
            col = [col[i] * (1 - g) + hexc(LIME)[i] * g for i in range(3)]
            sp[xx, yy] = tuple(int(v) for v in col) + (255,)
    img = small.resize((N, N), Image.BICUBIC)
    d = ImageDraw.Draw(img)
    for x, y, w, h, r, c in shapes:
        d.rounded_rectangle([x*k, y*k, (x+w)*k, (y+h)*k], radius=r*k, fill=hexc(c))
    m = Image.new("L", (N, N), 0); md = ImageDraw.Draw(m)
    # Launchers show the 72dp centre of the 108dp canvas.
    lo, hi = 18 * k, 90 * k
    if mask == "circle": md.ellipse([lo, lo, hi, hi], fill=255)
    else: md.rounded_rectangle([lo, lo, hi, hi], radius=16 * k, fill=255)
    img.putalpha(m)
    img = img.crop((int(lo), int(lo), int(hi), int(hi)))
    return img.resize((size, size), Image.LANCZOS)

out = sys.argv[1]
open(f"{out}/ic_launcher_foreground.xml", "w").write(fg())
open(f"{out}/ic_launcher_monochrome.xml", "w").write(mono())
open(f"{out}/ic_launcher_background.xml", "w").write(bg())
prev = Image.new("RGBA", (3 * 300 + 80, 340), (236, 238, 232, 255))
for i, (m, s) in enumerate([("circle", 260), ("squircle", 260), ("circle", 96)]):
    im = render(s, m); prev.alpha_composite(im, (20 + i * 320 + (260 - s) // 2, 40 + (260 - s) // 2))
prev.save(f"{out}/preview.png")
render(512, "squircle").save(f"{out}/icon-512.png")
