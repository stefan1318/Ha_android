#!/usr/bin/env python3
"""Fail CI when STEVORA Android string resources collide within a locale."""
from collections import defaultdict
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "app/src/main/res"
seen = defaultdict(list)
errors = []
for folder in sorted(root.glob("values*")):
    if not folder.is_dir():
        continue
    for xml in sorted(folder.glob("*.xml")):
        try:
            document = ET.parse(xml).getroot()
        except ET.ParseError as exc:
            errors.append(f"{xml}: invalid XML: {exc}")
            continue
        if document.tag != "resources":
            continue
        for child in document:
            if child.tag != "string":
                continue
            name = child.get("name")
            if name and name.startswith("stevora_"):
                seen[(folder.name, name)].append(xml.relative_to(root))
for (qualifier, name), locations in sorted(seen.items()):
    if len(locations) > 1:
        errors.append(f"Duplicate {qualifier}/{name}: " + ", ".join(map(str, locations)))
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"STEVORA resource validation passed: {len(seen)} localized names.")
