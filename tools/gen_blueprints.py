#!/usr/bin/env python3
"""Generates the three Hollowcrown starter blueprints (task M1-13).

Usage: python3 tools/gen_blueprints.py   (run from the repository root)

Coordinates: origin (0,0,0) is the ground block the player clicked, dy=0 is the ground layer.
The front of every building faces south (+z); the game turns it to face the player.
Each entry is [dx, dy, dz, "block state", "item a builder takes from the stockpile"].
Air is listed first (clearing), then blocks bottom up.
"""
import json
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "data", "riseofempires", "blueprints")

PLANKS, COBBLE, LOG = "minecraft:oak_planks", "minecraft:cobblestone", "minecraft:oak_log[axis=y]"
SOLID = {PLANKS, COBBLE, LOG, "minecraft:hay_block[axis=y]"}


class Plan:
	def __init__(self):
		self.cells = {}  # (x, y, z) -> (state, item)

	def put(self, x, y, z, state, item=None):
		if item is None:
			item = state.split("[")[0]
		self.cells[(x, y, z)] = (state, item)

	def free(self, x, y, z, state):
		self.put(x, y, z, state, "")

	def fence_connections(self):
		"""Fences are written with their connections, because builders place blocks one at a time."""
		for (x, y, z), (state, item) in list(self.cells.items()):
			if not state.startswith("minecraft:oak_fence["):
				continue
			sides = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
			props = []
			for name, (ox, oz) in sides.items():
				n = self.cells.get((x + ox, y, z + oz))
				ok = False
				if n:
					s = n[0]
					base = s.split("[")[0]
					if base == "minecraft:oak_fence" or s in SOLID or base in (PLANKS, COBBLE):
						ok = True
					elif base == "minecraft:oak_fence_gate":
						# a gate facing north or south opens its sides to the east and west
						facing_ns = "facing=north" in s or "facing=south" in s
						ok = facing_ns == (name in ("east", "west"))
				props.append(name + "=" + ("true" if ok else "false"))
			self.cells[(x, y, z)] = ("minecraft:oak_fence[" + ",".join(sorted(props)) + "]", item)

	def blocks(self, footprint, clear_height):
		self.fence_connections()
		out = []
		(x0, x1), (z0, z1) = footprint
		for dy in range(1, clear_height + 1):
			for x in range(x0, x1 + 1):
				for z in range(z0, z1 + 1):
					if (x, dy, z) not in self.cells:
						out.append([x, dy, z, "minecraft:air", ""])
		for (x, y, z) in sorted(self.cells, key=lambda c: (c[1], c[0], c[2])):
			state, item = self.cells[(x, y, z)]
			out.append([x, y, z, state, item])
		return out


def fence(p, x, y, z):
	p.put(x, y, z, "minecraft:oak_fence[east=false,north=false,south=false,west=false]", "minecraft:oak_fence")


def farm():
	p = Plan()
	for x in range(-3, 4):
		for z in range(-3, 4):
			if x == 0:
				if z <= 1:
					p.free(0, 0, z, "minecraft:water")
				continue
			if (x, z) == (-3, 3):
				continue  # the composter stands here
			p.free(x, 0, z, "minecraft:farmland[moisture=7]")
			p.put(x, 1, z, "minecraft:wheat[age=0]", "minecraft:wheat_seeds")
	for i in range(-4, 5):
		for (x, z) in ((i, -4), (i, 4), (-4, i), (4, i)):
			if (x, z) == (0, 4):
				continue
			fence(p, x, 1, z)
	p.put(0, 1, 4, "minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
	for (x, z) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
		p.put(x, 2, z, "minecraft:lantern[hanging=false]")
	p.put(-3, 1, 3, "minecraft:composter[level=0]")
	return p.blocks(((-4, 4), (-4, 4)), 3)


# Roofs. Every starter building uses one roof language, after common Minecraft roof guides: a gable
# at about 45 degrees, stairs whose high side points up the slope, a one-block overhang at the eaves
# and at the gable ends, a capped ridge, and wooden verges against a stone slope. Walls are four
# courses: a cobblestone footing, two of plank infill, and an oak beam under the eaves.
ROOF = "minecraft:stone_brick_stairs"
ROOF_CAP = "minecraft:stone_brick_slab[type=bottom]"
RIDGE = "minecraft:stone_bricks"
TRIM = "minecraft:oak_stairs"


def stair(block, facing, half="bottom"):
	return block + "[facing=" + facing + ",half=" + half + ",shape=straight]"


def gable(p, x0, x1, zc, half, eave_y, walls_z):
	"""A gable roof over walls x0..x1, ridge along x at z = zc, eaves at z = zc +- half, height eave_y.

	Rows rise one block per block inward. The roof runs one block past the gable walls (verges, in oak),
	the ridge is a stone brick course capped with a slab, and the gable walls are closed under the roof.
	walls_z: the z values where the gable walls stand (they are filled up to the roof).
	"""
	for x in range(x0 - 1, x1 + 2):
		verge = x in (x0 - 1, x1 + 1)
		for dz in range(-half, half + 1):
			z = zc + dz
			if dz == 0:
				y = eave_y + half - 1
				p.put(x, y, z, RIDGE if not verge else "minecraft:oak_planks")
				p.put(x, y + 1, z, ROOF_CAP if not verge else "minecraft:oak_slab[type=bottom]")
				continue
			y = eave_y + half - abs(dz)
			facing = "south" if dz < 0 else "north"  # the high side points up the slope, toward the ridge
			p.put(x, y, z, stair(TRIM if verge else ROOF, facing), TRIM if verge else ROOF)
	# Close the gable ends under the roof: a timber tie beam across the first course, planks above.
	for x in (x0, x1):
		for dz in range(-(half - 1), half):
			z = zc + dz
			if z not in walls_z:
				continue
			top = eave_y + half - 1 if dz == 0 else eave_y + half - abs(dz)
			for y in range(eave_y + 1, top):
				p.put(x, y, z, "minecraft:oak_log[axis=z]" if y == eave_y + 1 else PLANKS,
						"minecraft:oak_log" if y == eave_y + 1 else None)


def stable():
	p = Plan()
	for x in range(-3, 4):
		for z in range(-3, 1):
			p.put(x, 0, z, COBBLE)
	# walls: back (z=-3) and sides (x=+-3), cobblestone below, oak above, oak posts at the corners
	wall = [(x, -3) for x in range(-3, 4)] + [(s, z) for s in (-3, 3) for z in range(-2, 1)]
	for (x, z) in wall:
		post = x in (-3, 3) and z in (-3, 0)
		for dy in (1, 2, 3, 4):
			if post:
				p.put(x, dy, z, LOG)
			elif dy == 4:
				beam = "minecraft:oak_log[axis=x]" if z == -3 else "minecraft:oak_log[axis=z]"
				p.put(x, dy, z, beam, "minecraft:oak_log")
			else:
				p.put(x, dy, z, COBBLE if dy == 1 else PLANKS)
	# a header beam over the open front, and a tie beam inside to hang the lantern from
	for x in range(-2, 3):
		p.put(x, 4, 0, "minecraft:oak_log[axis=x]", "minecraft:oak_log")
	for z in (-2, -1):
		p.put(0, 4, z, "minecraft:oak_log[axis=z]", "minecraft:oak_log")
	# gable roof over the stalls, ridge along x, its front eave sheltering the pens
	gable(p, -3, 3, -1, 3, 4, walls_z=range(-3, 1))
	for x in (-1, 1):
		for z in (-2, -1):
			fence(p, x, 1, z)
	p.put(-2, 1, -2, "minecraft:hay_block[axis=y]")
	p.put(2, 1, -2, "minecraft:hay_block[axis=y]")
	p.put(0, 1, -2, "minecraft:water_cauldron[level=3]", "minecraft:cauldron")
	p.put(0, 3, -1, "minecraft:lantern[hanging=true]")
	# front pens: a fence yard split in two, each half with its own gate
	for x in (-3, -1, 0, 1, 3):
		fence(p, x, 1, 3)
	for z in (1, 2):
		fence(p, -3, 1, z)
		fence(p, 3, 1, z)
		fence(p, 0, 1, z)
	for x in (-2, 2):
		p.put(x, 1, 3, "minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
		p.put(x, 1, 1, "minecraft:hay_block[axis=y]")
	return p.blocks(((-4, 4), (-4, 3)), 7)


def bunkhouse():
	p = Plan()
	for x in range(-4, 5):
		for z in range(-3, 4):
			edge = abs(x) == 4 or abs(z) == 3
			p.put(x, 0, z, COBBLE if edge else PLANKS)
	windows = {(-2, -3), (0, -3), (2, -3), (-4, 0), (4, 0), (-2, 3), (2, 3)}
	for x in range(-4, 5):
		for z in range(-3, 4):
			if not (abs(x) == 4 or abs(z) == 3):
				continue
			corner = abs(x) == 4 and abs(z) == 3
			for dy in (1, 2, 3, 4):
				if (x, z) == (0, 3) and dy <= 2:
					continue  # doorway
				if corner:
					p.put(x, dy, z, LOG)
				elif dy == 4:
					beam = "minecraft:oak_log[axis=x]" if abs(z) == 3 else "minecraft:oak_log[axis=z]"
					p.put(x, dy, z, beam, "minecraft:oak_log")
				elif dy == 2 and (x, z) in windows:
					ns_wall = abs(z) == 3
					sides = "east=true,north=false,south=false,west=true" if ns_wall else "east=false,north=true,south=true,west=false"
					p.put(x, dy, z, "minecraft:glass_pane[" + sides + ",waterlogged=false]")
				else:
					p.put(x, dy, z, COBBLE if dy == 1 else PLANKS)
	p.put(0, 1, 3, "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]", "minecraft:oak_door")
	p.put(0, 2, 3, "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]", "")
	# four white beds, heads against the north wall, feet toward the room
	for x in (-3, -1, 1, 3):
		p.put(x, 1, -2, "minecraft:white_bed[facing=north,part=head,occupied=false]", "minecraft:white_bed")
		p.put(x, 1, -1, "minecraft:white_bed[facing=north,part=foot,occupied=false]", "")
	# tie beams across the hall under an open roof; the lantern hangs from the middle one
	for x in (-2, 0, 2):
		for z in range(-2, 3):
			p.put(x, 4, z, "minecraft:oak_log[axis=z]", "minecraft:oak_log")
	p.put(0, 3, 0, "minecraft:lantern[hanging=true]")
	# gable roof, ridge along the long side, eaves one block past the walls all round
	gable(p, -4, 4, 0, 4, 4, walls_z=range(-3, 4))
	return p.blocks(((-5, 5), (-4, 4)), 8)


def main():
	os.makedirs(OUT, exist_ok=True)
	for name, fn in (("farm", farm), ("stable", stable), ("bunkhouse", bunkhouse)):
		blocks = fn()
		lines = ",\n".join("\t\t" + json.dumps(b, separators=(",", ":")) for b in blocks)
		text = '{\n\t"name": "%s",\n\t"blocks": [\n%s\n\t]\n}\n' % (name.capitalize(), lines)
		with open(os.path.join(OUT, name + ".json"), "w") as f:
			f.write(text)
		print(name, len(blocks), "blocks")


if __name__ == "__main__":
	main()
