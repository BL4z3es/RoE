# Renders isometric previews of data/roe/blueprint/*.txt for review. Not part of the mod.
# Usage: python3 tools/render_blueprints.py src/main/resources/data/roe/blueprint docs/previews  (needs Pillow)
import sys, re
from PIL import Image, ImageDraw, ImageFont
BP = sys.argv[1]; OUT = sys.argv[2]
def parse(path):
    pal, layers, size = {}, [], None; sec = ''
    for line in open(path):
        line = line.strip()
        if not line or line.startswith('#'): continue
        if line.startswith('size:'): size = list(map(int, line[5:].split()))
        elif line == 'palette:': sec = 'p'
        elif line == 'layer:': sec = 'l'; layers.append([])
        elif line.startswith('name:'): pass
        elif sec == 'p': k, st = line.split()[:2]; pal[k] = st
        elif sec == 'l': layers[-1].append(line)
    blocks = []
    for y, rows in enumerate(layers):
        for z, row in enumerate(rows):
            for x, ch in enumerate(row):
                st = pal[ch]
                if st != 'minecraft:air': blocks.append((x, y, z, st))
    return size, blocks

C = {  # name: (top, side) colours
 'oak_log': ((154, 125, 77), (104, 81, 48)), 'oak_planks': ((176, 142, 86), (150, 120, 72)),
 'cobblestone': ((132, 132, 132), (104, 104, 104)), 'farmland': ((96, 62, 36), (110, 76, 48)),
 'water': ((62, 98, 210), (52, 80, 180)), 'dirt_path': ((160, 132, 72), (120, 92, 58)),
 'coarse_dirt': ((122, 88, 60), (104, 74, 50)), 'oak_fence': ((176, 142, 86), (146, 116, 70)),
 'oak_fence_gate': ((176, 142, 86), (146, 116, 70)), 'torch': ((255, 210, 90), (220, 150, 50)),
 'wall_torch': ((255, 210, 90), (220, 150, 50)), 'hay_block': ((214, 182, 52), (176, 140, 30)),
 'stone_brick_slab': ((128, 128, 128), (104, 104, 104)), 'brown_bed': ((122, 76, 42), (100, 60, 34)),
 'oak_door': ((146, 112, 64), (124, 94, 54)), 'oak_trapdoor': ((146, 112, 64), (120, 90, 50)),
 'chest': ((168, 116, 44), (138, 92, 32)), 'oak_stairs': ((176, 142, 86), (150, 120, 72)),
 'oak_slab': ((176, 142, 86), (150, 120, 72)),
 'stone_brick_stairs': ((128, 128, 128), (104, 104, 104)),
 'mossy_stone_bricks': ((104, 124, 92), (88, 104, 78)), 'cracked_stone_bricks': ((118, 116, 112), (96, 94, 90)),
 'chiseled_stone_bricks': ((140, 140, 140), (112, 112, 112)), 'mossy_cobblestone': ((100, 120, 90), (84, 100, 76)),
 'gravel': ((132, 124, 120), (112, 104, 100)), 'stone_brick_wall': ((128, 128, 128), (104, 104, 104)),
 'cobweb': ((235, 235, 235), (210, 210, 210)), 'lectern': ((180, 140, 84), (140, 104, 60)), 'stone_bricks': ((128, 128, 128), (104, 104, 104)),
}
def shape(st):
    n = re.sub(r'^minecraft:', '', st.split('[')[0])
    if n in ('oak_fence', 'oak_fence_gate', 'stone_brick_wall'): return n, 0.5, 1.0, 0.0
    if n == 'cobweb': return n, 0.6, 0.6, 0.0
    if n == 'lectern': return n, 0.8, 0.85, 0.0
    if n in ('torch', 'wall_torch'): return n, 0.15, 0.6, 0.0
    if n in ('oak_trapdoor',): return n, 0.2, 1.0, 0.0
    if n in ('stone_brick_slab', 'oak_slab', 'brown_bed'): return n, 1.0, 0.5, 0.0
    if n == 'farmland': return n, 1.0, 0.94, 0.0
    if n == 'water': return n, 1.0, 0.88, 0.0
    if n == 'dirt_path': return n, 1.0, 0.94, 0.0
    if n == 'chest': return n, 0.85, 0.85, 0.0
    if n == 'oak_door': return n, 0.3, 1.0, 0.0
    return n, 1.0, 1.0, 0.0
T = 34  # px per block edge
def iso(x, y, z):
    return ((x - z) * T * 0.866, (x + z) * T * 0.5 - y * T)
def box(d, ox, oy, x0, x1, y0, y1, z0, z1, top, side):
    P = lambda a, b, c: (ox + iso(a, b, c)[0], oy + iso(a, b, c)[1])
    sh = lambda c, f: tuple(int(v * f) for v in c)
    d.polygon([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], fill=top, outline=sh(top, .8))
    d.polygon([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], fill=sh(side, .92), outline=sh(side, .75))
    d.polygon([P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0)], fill=sh(side, .72), outline=sh(side, .6))
def stairs(d, ox, oy, x, y, z, facing, top, side):
    box(d, ox, oy, x, x + 1, y, y + .5, z, z + 1, top, side)
    r = {'east': (x + .5, x + 1, z, z + 1), 'west': (x, x + .5, z, z + 1),
         'south': (x, x + 1, z + .5, z + 1), 'north': (x, x + 1, z, z + .5)}[facing]
    box(d, ox, oy, r[0], r[1], y + .5, y + 1, r[2], r[3], top, side)
def cube(d, ox, oy, x, y, z, w, h, top, side):
    m = (1 - w) / 2; x0, x1, z0, z1 = x + m, x + 1 - m, z + m, z + 1 - m; y0, y1 = y, y + h
    P = lambda a, b, c: (ox + iso(a, b, c)[0], oy + iso(a, b, c)[1])
    sh = lambda c, f: tuple(int(v * f) for v in c)
    d.polygon([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], fill=top, outline=sh(top, .8))
    d.polygon([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], fill=sh(side, .92), outline=sh(side, .75))
    d.polygon([P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0)], fill=sh(side, .72), outline=sh(side, .6))
def render(path):
    size, blocks = parse(path); sx, sy, sz = size; pad = 2
    W = int((sx + sz + 2 * pad) * T * 0.9) + 40; H = int((sx + sz + 2 * pad) * T * 0.5 + (sy + 2) * T) + 40
    img = Image.new('RGB', (W, H), (36, 40, 48)); d = ImageDraw.Draw(img)
    ox = W / 2 - (sx - sz) * T * 0.866 / 2; oy = (sy + 1.5) * T
    ground = [(x, -1, z, None) for x in range(-pad, sx + pad) for z in range(-pad, sz + pad)]
    for x, y, z, _ in sorted(ground, key=lambda b: (b[0] + b[2], b[0])):
        cube(d, ox, oy, x, y, z, 1, 1, (92, 140, 60), (110, 82, 52))
    for x, y, z, st in sorted(blocks, key=lambda b: (b[1], b[0] + b[2], b[0])):
        n, w, h, _ = shape(st); top, side = C.get(n, ((255, 0, 255), (200, 0, 200)))
        if n == 'brown_bed' and 'part=head' in st: top = (230, 230, 225)
        if n.endswith('_stairs'):
            stairs(d, ox, oy, x, y, z, re.search(r'facing=(\w+)', st).group(1), top, side); continue
        cube(d, ox, oy, x, y, z, w, h, top, side)
    return img
imgs = []
import os
for name in [a for a in sys.argv[3:]] or ('farm', 'stable', 'bunkhouse'):
    path = name if name.endswith('.txt') else f'{BP}/{name}.txt'; name = os.path.basename(name)[:-4] if name.endswith('.txt') else name
    im = render(path); im.save(f'{OUT}/{name}.png'); imgs.append((name, im))
Wt = sum(i.width for _, i in imgs) + 20 * (len(imgs) + 1); Ht = max(i.height for _, i in imgs) + 60
sheet = Image.new('RGB', (Wt, Ht), (28, 31, 38)); d = ImageDraw.Draw(sheet); x = 20
try: f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
except Exception: f = ImageFont.load_default()
for name, im in imgs:
    sheet.paste(im, (x, 50)); d.text((x + 10, 14), name.replace('_', ' ').capitalize(), fill=(230, 225, 210), font=f); x += im.width + 20
sheet.save(f'{OUT}/' + ('starter-buildings.png' if len(sys.argv) <= 3 else 'preview.png')); print(sheet.size)
