#!/usr/bin/env python3
"""Merge assets/riseofempires/lang-fragments/*.json into assets/riseofempires/lang/en_us.json.

Each feature keeps its strings in a fragment so parallel work does not collide. Run this after adding
strings; it fails if two fragments define the same key differently.
"""
import glob, json, os, sys

root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "riseofempires")
merged = {}
for path in sorted(glob.glob(os.path.join(root, "lang-fragments", "*.json"))):
    with open(path, encoding="utf-8") as f:
        for k, v in json.load(f).items():
            if k in merged and merged[k] != v:
                sys.exit(f"{k} defined twice with different text ({path})")
            merged[k] = v
os.makedirs(os.path.join(root, "lang"), exist_ok=True)
with open(os.path.join(root, "lang", "en_us.json"), "w", encoding="utf-8") as f:
    json.dump(dict(sorted(merged.items())), f, indent="\t", ensure_ascii=False)
    f.write("\n")
print(f"{len(merged)} strings")
