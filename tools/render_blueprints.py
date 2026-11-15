# Renders isometric previews of blueprint files for review. Not part of the mod.
# Usage: python3 tools/render_blueprints.py <out-dir> <file.txt>... [--crops]  (needs Pillow)
#   --crops shows ripe wheat on the farmland.
import sys, re, os
from PIL import Image, ImageDraw, ImageFont

def parse(path):
    pal, layers, size, sec = {}, [], None, ''
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
                if st not in ('minecraft:air', 'keep'): blocks.append((x, y, z, st))
    return size, blocks

PLANK = ((176, 142, 86), (150, 120, 72))
STONE = ((134, 134, 134), (106, 106, 106))
C = {
 'oak_log': ((150, 120, 74), (92, 70, 42)), 'oak_planks': PLANK, 'oak_stairs': PLANK, 'oak_slab': PLANK,
 'cobblestone': ((128, 128, 128), (100, 100, 100)), 'mossy_cobblestone': ((104, 126, 90), (86, 104, 76)),
 'stone_bricks': STONE, 'stone_brick_stairs': ((112, 116, 124), (86, 90, 98)), 'stone_brick_slab': ((112, 116, 124), (86, 90, 98)),
 'mossy_stone_bricks': ((104, 126, 92), (86, 104, 78)), 'cracked_stone_bricks': ((120, 118, 114), (96, 94, 90)),
 'chiseled_stone_bricks': ((146, 146, 146), (114, 114, 114)), 'andesite': ((140, 140, 136), (112, 112, 108)),
 'gravel': ((132, 124, 120), (108, 100, 96)), 'stone_brick_wall': STONE, 'mossy_stone_brick_wall': ((104, 126, 92), (86, 104, 78)),
 'cobblestone_stairs': ((128, 128, 128), (100, 100, 100)),
 'farmland': ((96, 62, 36), (110, 76, 48)), 'water': ((62, 98, 210), (52, 80, 180)),
 'dirt_path': ((166, 138, 78), (122, 94, 60)), 'coarse_dirt': ((122, 88, 60), (102, 74, 50)),
 'oak_fence': PLANK, 'oak_fence_gate': PLANK, 'torch': ((255, 214, 96), (200, 140, 50)), 'wall_torch': ((255, 214, 96), (200, 140, 50)),
 'hay_block': ((214, 182, 52), (176, 140, 30)), 'brown_bed': ((122, 76, 42), (100, 60, 34)),
 'oak_door': ((146, 112, 64), (124, 94, 54)), 'oak_trapdoor': ((140, 106, 60), (116, 86, 48)),
 'chest': ((168, 116, 44), (138, 92, 32)), 'barrel': ((150, 110, 62), (112, 80, 44)), 'composter': ((132, 98, 54), (104, 76, 40)),
 'moss_carpet': ((92, 130, 60), (80, 112, 52)), 'oak_leaves': ((68, 120, 44), (54, 98, 36)), 'cobweb': ((236, 236, 236), (210, 210, 210)),
 'lectern': ((182, 140, 84), (140, 104, 60)), 'wheat': ((206, 180, 70), (170, 146, 50)),
}
T = 30
def iso(x, y, z, front):
    if front: x, z = z, -x
    return ((x - z) * T * 0.866, (x + z) * T * 0.5 - y * T)

def draw_box(d, P, x0, x1, y0, y1, z0, z1, top, side, front):
    sh = lambda c, f: tuple(int(v * f) for v in c)
    d.polygon([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], fill=top, outline=sh(top, .78))
    if front:   # visible faces: south (z1) and west (x0)
        d.polygon([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], fill=sh(side, .72), outline=sh(side, .6))
        d.polygon([P(x0, y0, z0), P(x0, y0, z1), P(x0, y1, z1), P(x0, y1, z0)], fill=sh(side, .92), outline=sh(side, .75))
    else:       # visible faces: south (z1) and east (x1)
        d.polygon([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], fill=sh(side, .92), outline=sh(side, .75))
        d.polygon([P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0)], fill=sh(side, .72), outline=sh(side, .6))

def boxes(x, y, z, st):
    n = re.sub(r'^minecraft:', '', st.split('[')[0])
    prop = dict(kv.split('=') for kv in re.findall(r'(\w+=\w+)', st))
    f = prop.get('facing')
    if n.endswith('_stairs'):
        top = prop.get('half') == 'top'
        lo = (x, x + 1, y + (.5 if top else 0), y + (1 if top else .5), z, z + 1)
        r = {'east': (x + .5, x + 1, z, z + 1), 'west': (x, x + .5, z, z + 1), 'south': (x, x + 1, z + .5, z + 1), 'north': (x, x + 1, z, z + .5)}[f]
        hi = (r[0], r[1], y + (0 if top else .5), y + (.5 if top else 1), r[2], r[3])
        return n, [lo, hi]
    if n.endswith('_slab'):
        return n, [(x, x + 1, y + (.5 if prop.get('type') == 'top' else 0), y + (1 if prop.get('type') == 'top' else .5), z, z + 1)]
    if n.endswith('_trapdoor') and prop.get('open') == 'true':
        t = 3 / 16
        r = {'north': (x, x + 1, z + 1 - t, z + 1), 'south': (x, x + 1, z, z + t), 'east': (x, x + t, z, z + 1), 'west': (x + 1 - t, x + 1, z, z + 1)}[f]
        return n, [(r[0], r[1], y, y + 1, r[2], r[3])]
    if n in ('oak_fence', 'oak_fence_gate', 'stone_brick_wall', 'mossy_stone_brick_wall'):
        w = .25 if n.startswith('oak') else .5
        return n, [(x + .5 - w / 2, x + .5 + w / 2, y, y + 1, z + .5 - w / 2, z + .5 + w / 2)]
    if n in ('torch', 'wall_torch'): return n, [(x + .42, x + .58, y + .1, y + .7, z + .42, z + .58)]
    if n == 'wheat': return n, [(x + .1 + i * .3, x + .2 + i * .3, y, y + .8, z + .1 + j * .3, z + .2 + j * .3) for i in range(3) for j in range(3)]
    if n == 'moss_carpet': return n, [(x, x + 1, y, y + .07, z, z + 1)]
    if n == 'cobweb': return n, [(x + .2, x + .8, y + .2, y + .8, z + .2, z + .8)]
    if n == 'brown_bed': return n, [(x, x + 1, y, y + .56, z, z + 1)]
    if n == 'farmland': return n, [(x, x + 1, y, y + .94, z, z + 1)]
    if n == 'water': return n, [(x, x + 1, y, y + .85, z, z + 1)]
    if n == 'dirt_path': return n, [(x, x + 1, y, y + .94, z, z + 1)]
    if n in ('chest',): return n, [(x + .06, x + .94, y, y + .88, z + .06, z + .94)]
    if n == 'lectern': return n, [(x + .1, x + .9, y, y + .9, z + .1, z + .9)]
    if n == 'oak_door': return n, [(x, x + 1, y, y + 1, z, z + .19)] if f == 'south' else [(x, x + 1, y, y + 1, z + .81, z + 1)]
    return n, [(x, x + 1, y, y + 1, z, z + 1)]

def render(path, crops, front):
    size, blocks = parse(path); sx, sy, sz = size; pad = 2
    if crops:
        blocks = blocks + [(x, y + 1, z, 'minecraft:wheat') for (x, y, z, s) in blocks if 'farmland' in s]
    W = int((sx + sz + 2 * pad) * T * 0.9) + 60; H = int((sx + sz + 2 * pad) * T * 0.5 + (sy + 2) * T) + 60
    img = Image.new('RGB', (W, H), (36, 40, 48)); d = ImageDraw.Draw(img)
    cx = (sx - sz) / 2; ox = W / 2 - (iso(sx / 2, 0, sz / 2, front)[0]); oy = (sy + 1.5) * T + 10
    P = lambda a, b, c: (ox + iso(a, b, c, front)[0], oy + iso(a, b, c, front)[1])
    key = (lambda b: (b[2] - b[0], b[1])) if front else (lambda b: (b[0] + b[2], b[1]))
    ground = [(x, -1, z) for x in range(-pad, sx + pad) for z in range(-pad, sz + pad)]
    for x, y, z in sorted(ground, key=lambda b: key((b[0], b[1], b[2]))):
        draw_box(d, P, x, x + 1, -1, 0, z, z + 1, (92, 140, 60), (110, 82, 52), front)
    for x, y, z, st in sorted(blocks, key=lambda b: (b[1], key(b), b[0])):
        n, bs = boxes(x, y, z, st)
        top, side = C.get(n, ((255, 0, 255), (200, 0, 200)))
        if n == 'brown_bed' and 'part=head' in st: top = (232, 232, 226)
        for b in bs: draw_box(d, P, b[0], b[1], b[2], b[3], b[4], b[5], top, side, front)
    return img

out = sys.argv[1]
args = [a for a in sys.argv[2:] if not a.startswith('--')]
crops, front = '--crops' in sys.argv, False
imgs = []
for path in args:
    name = os.path.basename(path)[:-4]
    im = render(path, crops, front); im.save(f'{out}/{name}.png'); imgs.append((name, im))
if len(imgs) > 1:
    Wt = sum(i.width for _, i in imgs) + 20 * (len(imgs) + 1); Ht = max(i.height for _, i in imgs) + 60
    sheet = Image.new('RGB', (Wt, Ht), (28, 31, 38)); d = ImageDraw.Draw(sheet); x = 20
    try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
    except Exception: font = ImageFont.load_default()
    for name, im in imgs:
        sheet.paste(im, (x, 50)); d.text((x + 10, 14), name.replace('_', ' ').capitalize(), fill=(230, 225, 210), font=font); x += im.width + 20
    sheet.save(f'{out}/sheet.png')
