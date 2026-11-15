# Draws the mod textures (Charter, Granary Seal, person). Usage: python3 tools/draw_textures.py <out-dir>  (needs Pillow)
import random, sys
from PIL import Image
out = sys.argv[1]
def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)
def noise_fill(img, box, base, rnd, amp=0.10):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(base, 1 + rnd.uniform(-amp, amp)))
def grid(rows, pal):
    h, w = len(rows), len(rows[0])
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != '.': img.putpixel((x, y), pal[ch])
    return img

# Charter: a rolled parchment with a red wax seal and gold ribbon
P = {'o': (74, 52, 30, 255), 'p': (226, 205, 160, 255), 'q': (203, 178, 130, 255), 'l': (244, 230, 196, 255),
     'i': (96, 72, 48, 255), 'r': (170, 34, 34, 255), 'R': (214, 62, 52, 255), 'd': (112, 20, 24, 255),
     'g': (222, 176, 54, 255), 'G': (160, 118, 28, 255)}
charter = [
 "................",
 "..oooooooooooo..",
 ".olpppppppppqqo.",
 ".oqqqqqqqqqqqqo.",
 "..olpppppppppo..",
 "..olpiiiiiippo..",
 "..olpppppppppo..",
 "..olpiiiippppo..",
 "..olpppppppppo..",
 "..olpiiiiiippo..",
 "..olppppgdddpo..",
 "..olpppgdRRrdo..",
 ".olppppGdRrrdqo.",
 ".oqqqqgGqdddqqo.",
 "..oooogGoooooo..",
 "......G.G.......",
]
grid(charter, P).save(f"{out}/item/charter.png")

# Granary Seal: oak-plank crate bound in iron, with a carved wheat-sheaf seal
rnd = random.Random(7)
def planks(seed):
    r = random.Random(seed); img = Image.new('RGBA', (16, 16))
    base = (166, 128, 76, 255)
    for y in range(16):
        row = shade(base, 1 + (0.06 if (y // 4) % 2 else -0.04))
        for x in range(16):
            c = shade(row, 1 + r.uniform(-0.07, 0.07))
            if y % 4 == 3: c = shade(base, 0.62)
            img.putpixel((x, y), c)
    return img
def band(img):
    iron, dark, lite = (92, 96, 104, 255), (58, 60, 68, 255), (140, 146, 156, 255)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)): img.putpixel((x, y), dark)
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            if 0 < i < 15: img.putpixel((x, y), iron)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)): img.putpixel((x, y), lite)
    return img
W = {'w': (232, 190, 72, 255), 'W': (250, 222, 120, 255), 's': (150, 110, 38, 255), 'k': (60, 44, 24, 255), 'r': (170, 34, 34, 255)}
sheaf = [
 "................","................","................",
 "......W.W.......",".....wWwWw......","....W.wWw.W.....","....wW.w.Ww.....",".....wWwWw......",
 "......wsw.......",".....rrrrr......","......wsw.......",".....w.s.w......","....s..s..s.....",
 "................","................","................"]
side = band(planks(3)); side.alpha_composite(grid(sheaf, W)); side.save(f"{out}/block/granary_seal_side.png")
top = band(planks(5))
ring = ["................","................","................","......kkkk......",".....kwWWwk.....","....kwWssWwk....",
        "...kwWsWWsWwk...","...kWsWrrWsWk...","...kWsWrrWsWk...","...kwWsWWsWwk...","....kwWssWwk....",".....kwWWwk.....",
        "......kkkk......","................","................","................"]
top.alpha_composite(grid(ring, W)); top.save(f"{out}/block/granary_seal_top.png")

# Person: Hollowcrown peasant, 64x64 player-skin layout
r = random.Random(11); sk = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
skin, hair, tunic, belt, trou, boot = (214, 164, 124, 255), (84, 56, 34, 255), (122, 104, 72, 255), (70, 46, 28, 255), (78, 70, 62, 255), (52, 38, 28, 255)
# head: top, bottom, right, front, left, back
noise_fill(sk, (8, 0, 16, 8), hair, r); noise_fill(sk, (16, 0, 24, 8), skin, r, .04)
for box in ((0, 8, 8, 16), (16, 8, 24, 16), (24, 8, 32, 16)): noise_fill(sk, box, hair, r)
noise_fill(sk, (8, 8, 16, 16), skin, r, .04)
noise_fill(sk, (8, 8, 16, 10), hair, r); 
for x in (8, 15): 
    for y in (10, 11): sk.putpixel((x, y), shade(hair, 1))
for box in ((0, 13, 8, 16), (16, 13, 24, 16)): noise_fill(sk, (box[0] + 2, box[1], box[2] - 2, box[3]), skin, r, .04)
for x in (10, 13): sk.putpixel((x, 12), (255, 255, 255, 255)); 
for x in (9, 14): sk.putpixel((x, 12), (58, 84, 110, 255))
sk.putpixel((9, 12), (255, 255, 255, 255)); sk.putpixel((10, 12), (58, 84, 110, 255)); sk.putpixel((13, 12), (58, 84, 110, 255)); sk.putpixel((14, 12), (255, 255, 255, 255))
for x in (11, 12): sk.putpixel((x, 13), shade(skin, .86)); sk.putpixel((x, 14), (150, 92, 80, 255))
# body
noise_fill(sk, (16, 16, 40, 32), tunic, r); noise_fill(sk, (16, 27, 40, 28), belt, r, .05)
sk.putpixel((23, 27), (190, 160, 70, 255)); sk.putpixel((24, 27), (190, 160, 70, 255))
for y in range(20, 24): sk.putpixel((23 + (y % 2), y), shade(tunic, .72))
for x in range(22, 26): sk.putpixel((x, 20), shade(skin, .95))
def limb(x0, y0, upper, lower, cuff):
    noise_fill(sk, (x0, y0, x0 + 16, y0 + 16), upper, r)
    noise_fill(sk, (x0, y0 + 12, x0 + 16, y0 + 16), lower, r, .05)
    noise_fill(sk, (x0 + 4, y0, x0 + 8, y0 + 4), upper, r); noise_fill(sk, (x0 + 8, y0, x0 + 12, y0 + 4), cuff, r, .05)
    for x in range(x0 + 12, x0 + 16):
        for y in range(y0, y0 + 4): sk.putpixel((x, y), (0, 0, 0, 0))
    for x in range(x0, x0 + 4):
        for y in range(y0, y0 + 4): sk.putpixel((x, y), (0, 0, 0, 0))
limb(40, 16, tunic, skin, skin); limb(32, 48, tunic, skin, skin)       # arms: sleeves, bare hands
limb(0, 16, trou, boot, boot); limb(16, 48, trou, boot, boot)           # legs: trousers, boots
sk.save(f"{out}/entity/person.png")
