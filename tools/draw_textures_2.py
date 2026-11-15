# Draws the barbarian skin and the Plan item. Usage: python3 tools/draw_textures_2.py <out-dir>  (needs Pillow)
import random, sys
from PIL import Image
out = sys.argv[1]
def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)
def fill(img, box, base, rnd, amp=0.10):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1): img.putpixel((x, y), shade(base, 1 + rnd.uniform(-amp, amp)))
def grid(rows, pal):
    img = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != '.': img.putpixel((x, y), pal[ch])
    return img

# Barbarian from the Wilds: weathered skin, wild rust hair and beard, blue war paint, hide tunic.
r = random.Random(23); sk = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
skin, hair, hide, fur, trou, boot, paint = (176, 128, 92), (128, 58, 28), (104, 78, 52), (150, 128, 100), (62, 52, 44), (44, 32, 24), (52, 86, 160)
fill(sk, (8, 0, 16, 8), hair, r); fill(sk, (16, 0, 24, 8), skin, r, .05)
for box in ((0, 8, 8, 16), (16, 8, 24, 16), (24, 8, 32, 16)): fill(sk, box, hair, r)
fill(sk, (8, 8, 16, 16), skin, r, .06)
fill(sk, (8, 8, 16, 9), hair, r)                       # brow line of hair
for x in (8, 15):
    for y in range(9, 16): sk.putpixel((x, y), shade(hair, 1 + r.uniform(-.1, .1)))
for x in range(9, 15):                                 # war paint band across the eyes
    sk.putpixel((x, 11), shade(paint, 1 + r.uniform(-.1, .1)))
sk.putpixel((10, 11), (230, 220, 200, 255)); sk.putpixel((13, 11), (230, 220, 200, 255))
sk.putpixel((9, 11), (30, 30, 30, 255)); sk.putpixel((14, 11), (30, 30, 30, 255))
fill(sk, (9, 13, 15, 16), hair, r, .12)                # beard
sk.putpixel((11, 13), (90, 50, 40, 255)); sk.putpixel((12, 13), (90, 50, 40, 255))
fill(sk, (16, 16, 40, 32), hide, r, .12)
for x in range(16, 40): sk.putpixel((x, 20), shade(fur, 1 + r.uniform(-.1, .1)))
for y in range(20, 32, 3): sk.putpixel((24, y), shade(hide, .6))
def limb(x0, y0, upper, lower):
    fill(sk, (x0, y0, x0 + 16, y0 + 16), upper, r); fill(sk, (x0, y0 + 12, x0 + 16, y0 + 16), lower, r, .05)
    fill(sk, (x0 + 4, y0, x0 + 8, y0 + 4), upper, r); fill(sk, (x0 + 8, y0, x0 + 12, y0 + 4), lower, r, .05)
    for x in list(range(x0, x0 + 4)) + list(range(x0 + 12, x0 + 16)):
        for y in range(y0, y0 + 4): sk.putpixel((x, y), (0, 0, 0, 0))
limb(40, 16, skin, skin); limb(32, 48, skin, skin)
for (x0, y0) in ((40, 16), (32, 48)):                  # paint stripes on the bare arms
    for x in range(x0, x0 + 16): sk.putpixel((x, y0 + 8), shade(paint, 1 + r.uniform(-.1, .1)))
limb(0, 16, trou, boot); limb(16, 48, trou, boot)
sk.save(f"{out}/entity/barbarian.png")

# Plan: a builder's plan on blue paper, tied with twine, a house sketched in white.
P = {'o': (34, 52, 92, 255), 'b': (70, 112, 178, 255), 'B': (56, 94, 156, 255), 'w': (226, 236, 248, 255),
     't': (186, 150, 96, 255), 'T': (138, 106, 62, 255)}
plan = [
 "................",
 ".oooooooooooooo.",
 ".obbbbbbbbbbbbo.",
 ".obbbbbwbbbbbbo.",
 ".obbbbwbwbbbbbo.",
 ".obbbwbbbwbbbbo.",
 ".obbwwwwwwwbbbo.",
 ".obbwbbbbbwbbbo.",
 ".obbwbwwbbwbbbo.",
 ".obbwbwwbbwbbbo.",
 ".obbwwwwwwwbbbo.",
 ".oBBBBBBBBBBBBo.",
 ".ooooottoooooo..",
 "......tT........",
 ".....t..T.......",
 "................",
]
grid(plan, P).save(f"{out}/item/plan.png")
