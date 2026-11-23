#!/usr/bin/env python3
"""Draw the five M1 textures as reviewable 16x16 pixel grids (Python stdlib only).

Hollowcrown style: worn parchment and old-empire stone brick; blue blueprint sheets
with white line drawings. Every grid row must be exactly 16 characters.

  python3 tools/gen_textures.py            # write the five textures
  python3 tools/gen_textures.py --preview  # also write docs/review/m1-textures.png

Outputs (five textures; the seal's item form uses its block model):
  textures/item/charter.png
  textures/block/granary_seal.png
  textures/item/farm_blueprint.png
  textures/item/stable_blueprint.png
  textures/item/bunkhouse_blueprint.png
"""
import argparse
import os
import struct
import zlib

SIZE = 16
PREVIEW_SCALE = 8

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
        "assets", "riseofempires", "textures")
PREVIEW = os.path.join(os.path.dirname(__file__), "..", "docs", "review", "m1-textures.png")

# Palettes: 4-6 opaque colours per texture, plus transparency on items.
PARCHMENT = {
    'E': (0x6B, 0x4A, 0x2E, 0xFF),  # dark outline / rolled edges
    'P': (0xE8, 0xD5, 0xA8, 0xFF),  # worn parchment
    'p': (0xC9, 0xB0, 0x7E, 0xFF),  # parchment shade
    'L': (0x8A, 0x6A, 0x42, 0xFF),  # faded writing
    'R': (0x8E, 0x1F, 0x24, 0xFF),  # dark red wax
    'r': (0x5E, 0x12, 0x18, 0xFF),  # wax shadow
    '.': (0, 0, 0, 0),
}
STONE = {
    'M': (0x46, 0x41, 0x3D, 0xFF),  # mortar / outline
    'S': (0x7A, 0x74, 0x6C, 0xFF),  # stone brick
    'H': (0x8E, 0x88, 0x80, 0xFF),  # worn top edge
    'D': (0x60, 0x5A, 0x54, 0xFF),  # shaded bottom edge
    'W': (0xD8, 0xB8, 0x48, 0xFF),  # wheat sheaf, old gold
    'w': (0xA0, 0x7C, 0x2C, 0xFF),  # carved shadow / stalk
}
BLUEPRINT = {
    'E': (0x0A, 0x16, 0x30, 0xFF),  # dark outline
    'B': (0x1D, 0x3A, 0x66, 0xFF),  # blueprint paper
    'G': (0x2C, 0x4E, 0x7E, 0xFF),  # faint grid
    'W': (0xF2, 0xF6, 0xFF, 0xFF),  # white drawing
    '.': (0, 0, 0, 0),
}

# The Charter: a rolled parchment, faded lines of writing, dark red wax seal.
CHARTER = [
    "................",
    ".....EEEEEE.....",
    "....EPPPPPPE....",
    "...EPPPPPPPPE...",
    "...EPPLLLLPPE...",
    "...EPPPPPPPPE...",
    "...EPPLLLLPPE...",
    "...EPPPPPPPPE...",
    "...EPPLLLLPPE...",
    "...EPPPPPPPPE...",
    "...EPPLLLLPPE...",
    "...EPPPPPPRRE...",
    "...EPPPPPPRrE...",
    "...EPPPPPPRrE...",
    "....EppppPRrE...",
    ".....EEEEEEE....",
]

# Farm blueprint: three ploughed field rows over a faint survey grid.
FARM = [
    "EEEEEEEEEEEEEEEE",
    "EBBBBBBBBBBBBBBE",
    "EBBGBBBGBBBGBBBE",
    "EBBGBBBGBBBGBBBE",
    "EBWWWWWWWWWWWWBE",
    "EBBGBBBGBBBGBBBE",
    "EBBGBBBGBBBGBBBE",
    "EBWWWWWWWWWWWWBE",
    "EBBGBBBGBBBGBBBE",
    "EBBGBBBGBBBGBBBE",
    "EBWWWWWWWWWWWWBE",
    "EBBGBBBGBBBGBBBE",
    "EBBGBBBGBBBGBBBE",
    "EBBGBBBGBBBGBBBE",
    "EBBBBBBBBBBBBBBE",
    "EEEEEEEEEEEEEEEE",
]

# Stable blueprint: a stall front with a rounded arch and a ground line.
STABLE = [
    "EEEEEEEEEEEEEEEE",
    "EBBBBBBBBBBBBBBE",
    "EBBBBBWWWWBBBBBE",
    "EBBBWWBBBBWWBBBE",
    "EBBBWBBBBBBWBBBE",
    "EBBBWBBBBBBWBBBE",
    "EBBBWBBBBBBWBBBE",
    "EBBBWBBBBBBWBBBE",
    "EBBBWBBBBBBWBBBE",
    "EBBBWBBBBBBWBBBE",
    "EBBWWWWWWWWWWBBE",
    "EBBBBBBBBBBBBBBE",
    "EBWWWWWWWWWWWWBE",
    "EBBBBBBBBBBBBBBE",
    "EBBBBBBBBBBBBBBE",
    "EEEEEEEEEEEEEEEE",
]

# Bunkhouse blueprint: a cabin with a pitched roof and a door.
BUNKHOUSE = [
    "EEEEEEEEEEEEEEEE",
    "EBBBBBBBBBBBBBBE",
    "EBBBBBBWWBBBBBBE",
    "EBBBBBWWWWBBBBBE",
    "EBBBBWWBBWWBBBBE",
    "EBBBWWBBBBWWBBBE",
    "EBBWWWWWWWWWWBBE",
    "EBBWBBBBBBBBWBBE",
    "EBBWBBBBBBBBWBBE",
    "EBBWBBBWWBBBWBBE",
    "EBBWBBBWWBBBWBBE",
    "EBBWBBBWWBBBWBBE",
    "EBBWWWWWWWWWWBBE",
    "EBBBBBBBBBBBBBBE",
    "EBBBBBBBBBBBBBBE",
    "EEEEEEEEEEEEEEEE",
]

# Granary Seal: stone-brick courses, then the carved wheat sheaf stamped on top.
BRICK_MORTAR_ROWS = (0, 4, 8, 12, 15)
# A wheat sheaf: grain pairs either side of a two-pixel stalk, tied at the base.
WHEAT = {
    3: {6: 'W', 9: 'W'},
    4: {5: 'W', 6: 'w', 7: 'W', 8: 'W', 9: 'w', 10: 'W'},
    5: {6: 'W', 7: 'w', 8: 'w', 9: 'W'},
    6: {5: 'W', 6: 'w', 7: 'W', 8: 'W', 9: 'w', 10: 'W'},
    7: {6: 'W', 7: 'w', 8: 'w', 9: 'W'},
    8: {7: 'W', 8: 'W'},
    9: {7: 'w', 8: 'w'},
    10: {7: 'w', 8: 'w'},
    11: {7: 'w', 8: 'w'},
    12: {6: 'w', 7: 'w', 8: 'w', 9: 'w'},
}


def granary_grid():
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if y in BRICK_MORTAR_ROWS or x in (0, SIZE - 1):
                row.append('M')
                continue
            # Staggered vertical joints, one per brick course.
            course = (y - 1) // 4
            joint = 7 if course % 2 == 0 else 11
            if x == joint:
                row.append('M')
            elif y % 4 == 1 or x == 1:
                row.append('H')  # worn light on the top/left of each brick
            elif y % 4 == 3 or x == SIZE - 2:
                row.append('D')
            else:
                row.append('S')
        rows.append(row)
    for y, cells in WHEAT.items():
        for x, ch in cells.items():
            rows[y][x] = ch
    return [''.join(r) for r in rows]


def pixels(grid, palette):
    assert len(grid) == SIZE, f"grid has {len(grid)} rows"
    out = []
    for row in grid:
        assert len(row) == SIZE, f"row is {len(row)} wide: {row!r}"
        out.append([palette[ch] for ch in row])
    return out


def write_png(path, data):
    """Write an RGBA PNG. data is a list of rows of (r, g, b, a) tuples."""
    height = len(data)
    width = len(data[0])
    raw = bytearray()
    for row in data:
        assert len(row) == width
        raw.append(0)  # filter: none, deterministic
        for rgba in row:
            raw.extend(rgba)

    def chunk(kind, payload):
        return (struct.pack('>I', len(payload)) + kind + payload
                + struct.pack('>I', zlib.crc32(kind + payload) & 0xFFFFFFFF))

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', ihdr))
        f.write(chunk(b'IDAT', idat))
        f.write(chunk(b'IEND', b''))


# A tiny 3x5 font for preview labels (uppercase letters, digits, underscore).
FONT = {
    'A': ["010", "101", "111", "101", "101"],
    'B': ["110", "101", "110", "101", "110"],
    'C': ["011", "100", "100", "100", "011"],
    'D': ["110", "101", "101", "101", "110"],
    'E': ["111", "100", "110", "100", "111"],
    'F': ["111", "100", "110", "100", "100"],
    'G': ["011", "100", "101", "101", "011"],
    'H': ["101", "101", "111", "101", "101"],
    'I': ["111", "010", "010", "010", "111"],
    'K': ["101", "101", "110", "101", "101"],
    'L': ["100", "100", "100", "100", "111"],
    'M': ["101", "111", "111", "101", "101"],
    'N': ["101", "111", "111", "111", "101"],
    'O': ["010", "101", "101", "101", "010"],
    'P': ["110", "101", "110", "100", "100"],
    'R': ["110", "101", "110", "101", "101"],
    'S': ["011", "100", "010", "001", "110"],
    'T': ["111", "010", "010", "010", "010"],
    'U': ["101", "101", "101", "101", "111"],
    'Y': ["101", "101", "010", "010", "010"],
    '_': ["000", "000", "000", "000", "111"],
}


def preview(textures):
    """One row of 8x nearest-neighbour textures with labels beneath, left to right."""
    cell = SIZE * PREVIEW_SCALE
    label_h = 7
    sheet_w = cell * len(textures)
    sheet_h = cell + label_h
    background = (0x20, 0x20, 0x24, 0xFF)
    sheet = [[background] * sheet_w for _ in range(sheet_h)]
    for i, (name, data) in enumerate(textures):
        for y in range(SIZE):
            for x in range(SIZE):
                for dy in range(PREVIEW_SCALE):
                    for dx in range(PREVIEW_SCALE):
                        sheet[y * PREVIEW_SCALE + dy][i * cell + x * PREVIEW_SCALE + dx] = data[y][x]
        # Centre the label under its texture; labels are drawn in two lines if long.
        words = name.upper().split('_')
        lines = ['_'.join(words)] if len(name) * 4 <= cell else ['_'.join(words[:-1]), words[-1]]
        for li, line in enumerate(lines):
            lx = i * cell + max(0, (cell - len(line) * 4) // 2)
            ly = cell + li * 6
            for ch in line:
                glyph = FONT.get(ch, FONT['_'])
                for gy, row in enumerate(glyph):
                    for gx, bit in enumerate(row):
                        if bit == '1' and ly + gy < sheet_h and lx + gx < sheet_w:
                            sheet[ly + gy][lx + gx] = (0xF0, 0xF0, 0xF0, 0xFF)
                lx += 4
    return sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true', help='write the review sheet as well')
    args = parser.parse_args()

    textures = [
        ('charter', 'item', pixels(CHARTER, PARCHMENT)),
        ('granary_seal', 'block', pixels(granary_grid(), STONE)),
        ('farm_blueprint', 'item', pixels(FARM, BLUEPRINT)),
        ('stable_blueprint', 'item', pixels(STABLE, BLUEPRINT)),
        ('bunkhouse_blueprint', 'item', pixels(BUNKHOUSE, BLUEPRINT)),
    ]
    for name, kind, data in textures:
        folder = os.path.join(ROOT, kind)
        os.makedirs(folder, exist_ok=True)
        write_png(os.path.join(folder, name + '.png'), data)
        print(f"{kind}/{name}.png")
    if args.preview:
        os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
        write_png(PREVIEW, preview([(name, data) for name, _, data in textures]))
        print("docs/review/m1-textures.png (left to right: " + ", ".join(n for n, _, _ in textures) + ")")


if __name__ == '__main__':
    main()
