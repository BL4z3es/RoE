#!/usr/bin/env python3
"""Writes the item model, item definition and recipe json for the three blueprint items (task M1-13)."""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
RECIPES = {"farm": "minecraft:wheat", "stable": "minecraft:leather", "bunkhouse": "minecraft:white_wool"}


def write(path, obj):
	full = os.path.join(ROOT, path)
	os.makedirs(os.path.dirname(full), exist_ok=True)
	with open(full, "w") as f:
		f.write(json.dumps(obj, indent="\t") + "\n")


for name, ingredient in RECIPES.items():
	item = "%s_blueprint" % name
	write("assets/riseofempires/items/%s.json" % item,
		{"model": {"type": "minecraft:model", "model": "riseofempires:item/" + item}})
	write("assets/riseofempires/models/item/%s.json" % item,
		{"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/paper"}})
	write("data/riseofempires/recipe/%s.json" % item,
		{"type": "minecraft:crafting_shapeless", "category": "misc",
			"ingredients": ["minecraft:paper", ingredient], "result": {"id": "riseofempires:" + item}})
