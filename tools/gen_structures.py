#!/usr/bin/env python3
"""Generates the opening structures as data-pack JSON (rule E11).

Run from the repository root:  python3 tools/gen_structures.py

Writes src/main/resources/data/riseofempires/structures/{hollow_ruin,founders_tent}.json.
Format: {"name": ..., "blocks": [[dx, dy, dz, "blockstate"], ...]}. dy = 0 is the first air block
above the ground; dy = -1 is the ground surface the structure replaces. +z is south. Output is
deterministic (seeded), so rerunning gives identical files.
"""
import json
import os
import random

OUT = os.path.join("src", "main", "resources", "data", "riseofempires", "structures")

AIR, SOLID, ATTACHED = 0, 1, 2


class Grid:
	"""Blocks keyed by (x, y, z); later writes win. Each carries a build phase."""

	def __init__(self):
		self.cells = {}

	def put(self, x, y, z, state, phase=SOLID):
		self.cells[(x, y, z)] = (state, phase)

	def get(self, x, y, z):
		c = self.cells.get((x, y, z))
		return c[0] if c else None

	def solid_at(self, x, y, z):
		s = self.get(x, y, z)
		return s is not None and s != "minecraft:air" and not any(
			k in s for k in ("vine", "cobweb", "carpet", "banner", "[type=bottom", "fence"))

	def blocks(self):
		items = sorted(self.cells.items(), key=lambda kv: (kv[0][1], kv[1][1], kv[0][2], kv[0][0]))
		return [[x, y, z, s] for (x, y, z), (s, _) in items]


def write(name, display, grid):
	os.makedirs(OUT, exist_ok=True)
	path = os.path.join(OUT, name + ".json")
	blocks = grid.blocks()
	with open(path, "w") as f:
		f.write('{\n\t"name": %s,\n\t"blocks": [\n' % json.dumps(display))
		f.write(",\n".join("\t\t" + json.dumps(b, separators=(",", ":")) for b in blocks))
		f.write("\n\t]\n}\n")
	print(path, len(blocks), "blocks")


def pick(rng, table):
	r = rng.random()
	acc = 0.0
	for p, v in table:
		acc += p
		if r < acc:
			return v
	return table[-1][1]


COBBLE = [(0.65, "minecraft:cobblestone"), (0.35, "minecraft:mossy_cobblestone")]
BRICK = [(0.55, "minecraft:stone_bricks"), (0.22, "minecraft:cracked_stone_bricks"), (0.23, "minecraft:mossy_stone_bricks")]
FLOOR = [(0.40, "minecraft:stone_bricks"), (0.20, "minecraft:cracked_stone_bricks"), (0.17, "minecraft:mossy_stone_bricks"),
		(0.10, "minecraft:cobblestone"), (0.06, "minecraft:moss_block"), (0.04, "minecraft:coarse_dirt"), (0.03, "minecraft:gravel")]
SLAB_OF = {"minecraft:cobblestone": "minecraft:cobblestone_slab", "minecraft:mossy_cobblestone": "minecraft:mossy_cobblestone_slab",
		"minecraft:stone_bricks": "minecraft:stone_brick_slab", "minecraft:cracked_stone_bricks": "minecraft:stone_brick_slab",
		"minecraft:mossy_stone_bricks": "minecraft:mossy_stone_brick_slab", "minecraft:oak_planks": "minecraft:oak_slab"}
LOG_Y = "minecraft:oak_log[axis=y]"
SLAB = "[type=bottom]"


def hollow_ruin():
	rng = random.Random(1101)
	g = Grid()
	R = 5  # the walls stand on |x| == 5 or |z| == 5: an 11 x 11 footprint

	# Clear the site: air from the floor line up, plus the approach.
	for x in range(-6, 7):
		for z in range(-6, 7):
			for y in range(0, 10):
				g.put(x, y, z, "minecraft:air", AIR)
	for x in range(-2, 3):
		for z in range(7, 12):
			for y in range(0, 4):
				g.put(x, y, z, "minecraft:air", AIR)

	# Foundation: solid under the whole footprint, deeper under the walls so slopes do not leave gaps.
	for x in range(-R, R + 1):
		for z in range(-R, R + 1):
			ring = abs(x) == R or abs(z) == R
			for y in range(-2, -7 if ring else -4, -1):
				g.put(x, y, z, "minecraft:cobblestone")
			g.put(x, -1, z, pick(rng, COBBLE) if ring else pick(rng, FLOOR))

	# Wall heights, in blocks above the floor line.
	north = {-5: 6, -4: 5, -3: 4, -2: 4, -1: 4, 0: 5, 1: 4, 2: 3, 3: 3, 4: 2, 5: 4}
	west = {-4: 4, -3: 3, -2: 3, -1: 3, 0: 1, 1: 0, 2: 2, 3: 3, 4: 3}
	east = {-4: 3, -3: 2, -2: 1, -1: 1, 0: 0, 1: 1, 2: 2, 3: 1, 4: 1}
	south = {-5: 3, -4: 3, -3: 2, -2: 5, -1: 0, 0: 0, 1: 0, 2: 3, 3: 1, 4: 2, 5: 1}
	posts_north = {-5, -1, 2, 5}
	posts_west = {-2, 2}

	def wall_column(x, z, h, timber, post):
		for y in range(h):
			if y < 2:
				state = pick(rng, COBBLE)
			elif post:
				state = LOG_Y
			elif timber:
				state = "minecraft:oak_planks"
			else:
				state = pick(rng, BRICK)
			g.put(x, y, z, state)
		# Crumbled top: a slab or a missing block, never on the posts.
		if h >= 3 and not post:
			top = g.get(x, h - 1, z)
			r = rng.random()
			if r < 0.35 and top in SLAB_OF:
				g.put(x, h - 1, z, SLAB_OF[top] + SLAB)
			elif r < 0.50:
				g.put(x, h - 1, z, "minecraft:air", AIR)

	for x, h in north.items():
		wall_column(x, -R, h, True, x in posts_north)
	for z, h in west.items():
		wall_column(-R, z, h, True, z in posts_west)
	for z, h in east.items():
		wall_column(R, z, h, False, False)
	for x, h in south.items():
		wall_column(x, R, h, False, x in (-2, 2))

	# Corner posts: cobble to waist height, an oak log above, so the old timber frame shows.
	for (x, z, h) in [(-R, -R, 6), (R, -R, 4), (-R, R, 3), (R, R, 1)]:
		for y in range(h):
			g.put(x, y, z, pick(rng, COBBLE) if y < 2 else LOG_Y)
	# Doorway: three wide, with a broken lintel of two logs and a missing third.
	for x in (-1, 0, 1):
		for y in range(0, 5):
			g.put(x, y, R, "minecraft:air", AIR)
	g.put(-1, 3, R, "minecraft:oak_log[axis=x]")
	g.put(0, 3, R, "minecraft:oak_log[axis=x]")
	# Windows in the north wall: one block each, lintel left standing.
	for x in (-3, 3):
		if north[x] >= 4:
			g.put(x, 2, -R, "minecraft:air", AIR)
	g.put(-3, 2, -R, "minecraft:air", AIR)
	# A hole in the west wall where it fell in.
	for y in range(0, 2):
		g.put(-R, y, 1, "minecraft:air", AIR)

	# Raised dais at the north end, with steps: where the old officers kept their records.
	for x in range(-3, 4):
		g.put(x, 0, -4, pick(rng, BRICK))
	for x in (-1, 0, 1):
		g.put(x, 0, -3, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]")
	g.put(0, 1, -4, "minecraft:chest[facing=south,type=single]")
	g.put(-2, 1, -4, "minecraft:lectern[facing=south,has_book=false]")
	g.put(3, 1, -4, "minecraft:mossy_cobblestone")
	g.put(2, 1, -4, "minecraft:cobweb", ATTACHED)
	g.put(0, 3, -4, "minecraft:red_wall_banner[facing=south]", ATTACHED)

	# Partly fallen roof: beams across the north-west bay, planks on some of it, nothing over the rest.
	for z in (-4, -2, 0):
		for x in range(-4, 2):
			if (z, x) in ((0, 1), (-2, 1)):
				continue
			g.put(x, 4, z, "minecraft:oak_log[axis=x]")
	for z in (-3, -1):
		for x in range(-4, 0):
			if (z, x) in ((-1, -1), (-3, -4)):
				continue
			g.put(x, 4, z, "minecraft:oak_planks" if rng.random() < 0.8 else "minecraft:oak_slab" + SLAB)
	for x in (-4, -3, -2):
		g.put(x, 5, -4, "minecraft:oak_stairs[facing=south,half=bottom,shape=straight]")
	# Beam stubs poking out of the north wall over the dais.
	for x in (2, 3, 4):
		g.put(x, 4, -4, "minecraft:oak_log[axis=x]")

	# Fallen roof and masonry on the floor.
	for (x, y, z, s) in [
		(-3, 0, -2, "minecraft:oak_planks"), (-2, 0, -1, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight]"),
		(-4, 0, 1, "minecraft:oak_log[axis=x]"), (-3, 0, 1, "minecraft:oak_log[axis=x]"), (-2, 0, 1, "minecraft:oak_log[axis=x]"),
		(-4, 0, 2, "minecraft:oak_slab" + SLAB), (2, 0, 0, "minecraft:oak_slab" + SLAB),
		(3, 0, 1, "minecraft:mossy_cobblestone"), (4, 0, 1, "minecraft:cobblestone"), (4, 0, 2, "minecraft:mossy_cobblestone_slab" + SLAB),
		(3, 0, 2, "minecraft:cobblestone_slab" + SLAB), (3, 0, 3, "minecraft:cracked_stone_bricks"),
		(-1, 0, 3, "minecraft:stone_brick_slab" + SLAB), (4, 0, -3, "minecraft:mossy_cobblestone"),
		(-4, 0, 4, "minecraft:cobblestone"), (-3, 0, 4, "minecraft:mossy_cobblestone_slab" + SLAB)]:
		g.put(x, y, z, s)
	# Rubble spilled outside the broken walls.
	for (x, y, z, s) in [(6, 0, -2, "minecraft:cobblestone"), (6, 0, -1, "minecraft:mossy_cobblestone"),
			(6, 0, 1, "minecraft:cobblestone_slab" + SLAB), (-6, 0, 0, "minecraft:mossy_cobblestone"), (-6, 0, 1, "minecraft:cobblestone"),
			(-6, 0, 2, "minecraft:stone_brick_slab" + SLAB), (1, 0, 6, "minecraft:cracked_stone_bricks"),
			(-3, 0, 6, "minecraft:mossy_cobblestone_slab" + SLAB)]:
		g.put(x, y, z, s)

	# A little moss and wet: carpet on the floor, cobwebs in the high corners, vines on the walls.
	for (x, z) in [(-4, -3), (-1, -2), (1, -1), (-3, 3), (2, 3), (4, -1), (0, 2), (-2, 4), (3, 0), (1, 4)]:
		if g.get(x, 0, z) in (None, "minecraft:air"):
			g.put(x, 0, z, "minecraft:moss_carpet", ATTACHED)
	for (x, y, z) in [(-4, 3, -3), (-4, 3, -1), (-4, 3, -4), (4, 3, -4), (-4, 2, 4), (4, 2, -4), (-4, 2, 0), (3, 2, 4)]:
		if g.get(x, y, z) in (None, "minecraft:air"):
			g.put(x, y, z, "minecraft:cobweb", ATTACHED)
	vines = []
	for x in (-4, -3, 1, 3):  # inside the north wall, facing south
		for y in (2, 3):
			if g.solid_at(x, y, -R) and g.get(x, y, -4) in (None, "minecraft:air"):
				vines.append((x, y, -4, "south"))
	for z in (-3, 3, 4):  # outside the west wall, facing east
		for y in (1, 2):
			if g.solid_at(-R, y, z):
				vines.append((-6, y, z, "east"))
	for x in (3, 4):  # outside the south wall, facing north
		for y in (1, 2):
			if g.solid_at(x, y, R):
				vines.append((x, y, 6, "north"))
	for (x, y, z, face) in vines:
		g.put(x, y, z, "minecraft:vine[%s=true]" % face, ATTACHED)

	# The approach: a worn cobble path from the doorway, with gaps.
	x = 0
	for z in range(6, 12):
		x += rng.choice([-1, 0, 0, 1]) if z > 7 else 0
		x = max(-2, min(2, x))
		for dx in (-1, 0):
			if rng.random() < 0.75:
				g.put(x + dx, -1, z, pick(rng, [(0.45, "minecraft:cobblestone"), (0.3, "minecraft:mossy_cobblestone"), (0.25, "minecraft:gravel")]))
	g.put(0, -1, 6, "minecraft:stone_bricks")
	g.put(-1, -1, 6, "minecraft:cracked_stone_bricks")
	return g


def founders_tent():
	"""The founder's camp: an A-frame tent with its entrance to the north, and a campfire in front.

	After common Minecraft tent builds: one upright course of canvas, then a true slope of pale stairs
	(wool cannot slope) up to a peaked ridge, framed at both ends in spruce with stripped-spruce corner
	poles; the back is closed in the same canvas. Inside, a bedroll, a barrel of supplies and a lantern
	hanging from the ridge. In front, a campfire with spruce benches facing it, and a barrel with a
	lantern on it. Footprint: x -2..2, z -5 (bench) .. 4 (back); dy 0..3.
	"""
	g = Grid()
	canvas_block, canvas, canvas_slab = "minecraft:smooth_quartz", "minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz_slab"
	frame_block, frame, frame_slab = "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_slab"
	pole = "minecraft:stripped_spruce_log[axis=y]"

	def st(block, facing):
		return block + "[facing=%s,half=bottom,shape=straight,waterlogged=false]" % facing

	for x in range(-2, 3):
		for z in range(-5, 5):
			for y in range(0, 5):
				g.put(x, y, z, "minecraft:air", AIR)
	for z in range(0, 5):
		end = z in (0, 4)
		block, stairs, slab = (frame_block, frame, frame_slab) if end else (canvas_block, canvas, canvas_slab)
		# An upright course, then the slope; the high side of each stair points up toward the ridge.
		g.put(-2, 0, z, pole if end else block)
		g.put(2, 0, z, pole if end else block)
		g.put(-2, 1, z, st(stairs, "east"))
		g.put(2, 1, z, st(stairs, "west"))
		g.put(-1, 2, z, st(stairs, "east"))
		g.put(1, 2, z, st(stairs, "west"))
		g.put(0, 2, z, block)
		g.put(0, 3, z, slab + "[type=bottom,waterlogged=false]")
	# The back is closed in the same canvas; the front is open.
	for (x, y) in ((-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
		g.put(x, y, 4, canvas_block)
	# Inside: a bedroll down the middle, a barrel of supplies, a lantern hanging from the ridge.
	g.put(0, 0, 1, "minecraft:red_bed[facing=south,part=foot,occupied=false]")
	g.put(0, 0, 2, "minecraft:red_bed[facing=south,part=head,occupied=false]", ATTACHED)
	g.put(-1, 0, 3, "minecraft:barrel[facing=up,open=false]")
	g.put(0, 1, 2, "minecraft:lantern[hanging=true,waterlogged=false]", ATTACHED)
	# The fire, with spruce benches facing it (backs away from the fire), and supplies with a lantern.
	g.put(0, 0, -3, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]", ATTACHED)
	g.put(-2, 0, -3, st(frame, "west"))
	g.put(2, 0, -3, st(frame, "east"))
	g.put(0, 0, -5, st(frame, "north"))
	g.put(2, 0, -1, "minecraft:barrel[facing=up,open=false]")
	g.put(2, 1, -1, "minecraft:lantern[hanging=false,waterlogged=false]", ATTACHED)
	return g


if __name__ == "__main__":
	write("hollow_ruin", "Hollow Ruin", hollow_ruin())
	write("founders_tent", "Founder's Tent", founders_tent())
