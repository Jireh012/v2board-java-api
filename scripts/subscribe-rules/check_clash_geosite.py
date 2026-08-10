#!/usr/bin/env python3
"""Validate Clash seed GEOSITE tags against geosite-allowlist.txt (and optional geosite.dat)."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLASH = ROOT / "src/main/resources/rules/default.clash.yaml"
ALLOWLIST = ROOT / "src/main/resources/rules/geosite-allowlist.txt"

GEOSITE_RE = re.compile(r"^\s*-\s*GEOSITE,([^,\s]+)\s*,", re.MULTILINE)


def load_allowlist(path: Path) -> set[str]:
    tags: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        tags.add(s)
    return tags


def clash_geosite_tags(path: Path) -> list[str]:
    body = path.read_text(encoding="utf-8")
    return GEOSITE_RE.findall(body)


def geosite_dat_has_tag(dat: Path, tag: str) -> bool | None:
    """Best-effort: search UTF-8 needle in geosite.dat. None if file missing."""
    if not dat.is_file():
        return None
    data = dat.read_bytes()
    needle = tag.encode("utf-8")
    return needle in data


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--geosite-dat",
        type=Path,
        default=None,
        help="Optional path to geosite.dat for presence check",
    )
    args = ap.parse_args()

    if not CLASH.is_file() or not ALLOWLIST.is_file():
        print("FAIL: missing clash seed or allowlist", file=sys.stderr)
        return 1

    allow = load_allowlist(ALLOWLIST)
    used = clash_geosite_tags(CLASH)
    used_set = set(used)
    errors: list[str] = []

    if "category-ad" in used_set and "category-ads-all" not in str(used):
        errors.append("forbidden tag category-ad (use category-ads-all)")

    missing_allow = sorted(used_set - allow)
    if missing_allow:
        errors.append(f"GEOSITE tags not in allowlist: {missing_allow}")

    unused = sorted(allow - used_set)
    if unused:
        print(f"WARN: allowlist tags unused in clash seed: {unused}")

    if args.geosite_dat:
        for tag in sorted(used_set):
            present = geosite_dat_has_tag(args.geosite_dat, tag)
            if present is False:
                errors.append(f"tag not found in geosite.dat: {tag}")
            elif present is None:
                errors.append(f"geosite.dat missing: {args.geosite_dat}")
                break

    print(f"clash GEOSITE count={len(used)} unique={len(used_set)}")
    if errors:
        for e in errors:
            print(f"FAIL: {e}", file=sys.stderr)
        return 1
    print("OK: Clash GEOSITE tags match allowlist")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
