"""Add missing imports after package split (same-package refs that crossed roles)."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
src_roots = [root / "src/main/java", root / "src/test/java"]

# class -> package from PACKAGE_MAP migration
CLASS_PKG = {}
for src_root in src_roots:
    for path in src_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        pkg_m = re.search(r"^package\s+([\w.]+);", text, re.M)
        cls_m = re.search(
            r"^(?:public\s+)?(?:final\s+)?(?:class|record|enum|interface)\s+(\w+)",
            text,
            re.M,
        )
        if pkg_m and cls_m:
            CLASS_PKG[cls_m.group(1)] = pkg_m.group(1)

# Prefer longer names first when scanning
names = sorted(CLASS_PKG.keys(), key=len, reverse=True)
# Avoid matching import lines / package / strings clumsily: use word boundaries
pattern = re.compile(r"\b(" + "|".join(map(re.escape, names)) + r")\b")

for src_root in src_roots:
    for path in src_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        pkg_m = re.search(r"^package\s+([\w.]+);", text, re.M)
        if not pkg_m:
            continue
        pkg = pkg_m.group(1)
        existing = set(re.findall(r"^import\s+([\w.]+);", text, re.M))
        needed = set()
        # skip comments roughly by scanning code after package
        body = text
        for m in pattern.finditer(body):
            cls = m.group(1)
            target = CLASS_PKG[cls]
            if target == pkg:
                continue
            fqcn = f"{target}.{cls}"
            if fqcn not in existing and f"import {fqcn};" not in text:
                # don't import self file's class
                if path.stem == cls:
                    continue
                needed.add(fqcn)
        if not needed:
            continue
        # insert after package and existing imports
        lines = text.splitlines(keepends=True)
        insert_at = 0
        for i, line in enumerate(lines):
            if line.startswith("package "):
                insert_at = i + 1
            elif line.startswith("import "):
                insert_at = i + 1
            elif line.strip() == "" and insert_at > 0:
                # keep going through blank lines after imports
                if any(l.startswith("import ") for l in lines[:i]):
                    insert_at = i
                    break
            elif insert_at > 0 and not line.startswith("import ") and line.strip():
                break
        # find last import
        last_import = -1
        for i, line in enumerate(lines):
            if line.startswith("import "):
                last_import = i
        if last_import >= 0:
            insert_at = last_import + 1
        else:
            # after package line + blank
            for i, line in enumerate(lines):
                if line.startswith("package "):
                    insert_at = i + 1
                    if insert_at < len(lines) and lines[insert_at].strip() == "":
                        insert_at += 1
                    break
        new_imports = [f"import {fq};\n" for fq in sorted(needed)]
        # ensure blank line after imports block
        lines[insert_at:insert_at] = new_imports
        path.write_text("".join(lines), encoding="utf-8")
        print("ADDED", path.relative_to(root), sorted(needed))

print("DONE")
