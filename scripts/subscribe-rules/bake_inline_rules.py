#!/usr/bin/env python3
"""Bake ACL4SSR Clash lists into Surge/Surfboard/Loon/QX local rule markers.

Usage:
  python3 scripts/subscribe-rules/bake_inline_rules.py --check
  python3 scripts/subscribe-rules/bake_inline_rules.py --bake
  python3 scripts/subscribe-rules/check_clash_geosite.py
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = Path(__file__).resolve().parent / "bake-manifest.json"
BEGIN = "# BEGIN-ACL4SSR-BAKE"
END = "# END-ACL4SSR-BAKE"


def load_manifest() -> dict:
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def check_markers(manifest: dict) -> int:
    errors = 0
    for t in manifest["targets"]:
        path = ROOT / t["path"]
        if not path.is_file():
            print(f"FAIL: missing {path}", file=sys.stderr)
            errors += 1
            continue
        body = path.read_text(encoding="utf-8")
        if BEGIN not in body or END not in body:
            print(f"FAIL: markers missing in {path}", file=sys.stderr)
            errors += 1
            continue
        i0 = body.index(BEGIN) + len(BEGIN)
        i1 = body.index(END)
        mid = body[i0:i1].strip()
        lines = [ln for ln in mid.splitlines() if ln.strip() and not ln.strip().startswith("#")]
        if len(lines) < 20:
            print(f"FAIL: baked section too small in {path} ({len(lines)} lines)", file=sys.stderr)
            errors += 1
            continue
        print(f"OK markers {path.name}: {len(lines)} rule lines")
    return 1 if errors else 0


def fetch(url: str, timeout: float = 30.0) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "v2board-rule-bake/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_acl_list(text: str) -> list[tuple[str, str]]:
    """Return list of (kind, value) where kind in domain-suffix|domain|domain-keyword|ip-cidr."""
    out: list[tuple[str, str]] = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("//"):
            continue
        upper = line.upper()
        if upper.startswith("DOMAIN-SUFFIX,"):
            out.append(("domain-suffix", line.split(",", 1)[1].strip().split(",")[0].strip()))
        elif upper.startswith("DOMAIN-KEYWORD,"):
            out.append(("domain-keyword", line.split(",", 1)[1].strip().split(",")[0].strip()))
        elif upper.startswith("DOMAIN,"):
            out.append(("domain", line.split(",", 1)[1].strip().split(",")[0].strip()))
        elif upper.startswith("IP-CIDR,"):
            out.append(("ip-cidr", line.split(",", 1)[1].strip().split(",")[0].strip()))
        elif upper.startswith("IP-CIDR6,"):
            out.append(("ip-cidr6", line.split(",", 1)[1].strip().split(",")[0].strip()))
        elif line.startswith("."):
            out.append(("domain-suffix", line.lstrip(".")))
        elif "/" in line and any(c.isdigit() for c in line.split("/", 1)[0]):
            out.append(("ip-cidr", line.split(",")[0].strip()))
        else:
            # bare domain
            host = line.split(",")[0].strip()
            if host and " " not in host:
                out.append(("domain-suffix", host.lstrip(".")))
    return out


def to_surge(kind: str, value: str, policy: str) -> str | None:
    if kind == "domain-suffix":
        return f"DOMAIN-SUFFIX,{value},{policy}"
    if kind == "domain":
        return f"DOMAIN,{value},{policy}"
    if kind == "domain-keyword":
        return f"DOMAIN-KEYWORD,{value},{policy}"
    if kind == "ip-cidr":
        return f"IP-CIDR,{value},{policy}"
    if kind == "ip-cidr6":
        return f"IP-CIDR6,{value},{policy}"
    return None


def to_qx(kind: str, value: str, policy: str) -> str | None:
    if kind == "domain-suffix":
        return f"host-suffix, {value}, {policy}"
    if kind == "domain":
        return f"host, {value}, {policy}"
    if kind == "domain-keyword":
        return f"host-keyword, {value}, {policy}"
    if kind == "ip-cidr":
        return f"ip-cidr, {value}, {policy}"
    if kind == "ip-cidr6":
        return f"ip6-cidr, {value}, {policy}"
    return None


def replace_markers(body: str, baked: str) -> str:
    if BEGIN not in body or END not in body:
        raise ValueError("markers missing")
    head, rest = body.split(BEGIN, 1)
    _, tail = rest.split(END, 1)
    return f"{head}{BEGIN}\n{baked.rstrip()}\n{END}{tail}"


def bake(manifest: dict, max_per_list: int) -> int:
    base = manifest["base_url"].rstrip("/")
    collected_surge: list[str] = [
        "# Auto-baked from ACL4SSR via scripts/subscribe-rules/bake_inline_rules.py",
        "# Do not hand-edit inside markers; re-run --bake to refresh.",
    ]
    collected_qx: list[str] = list(collected_surge)

    for item in manifest["lists"]:
        url = f"{base}/{item['file']}"
        print(f"fetch {url}")
        try:
            text = fetch(url)
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            print(f"FAIL: fetch {item['file']}: {e}", file=sys.stderr)
            return 1
        entries = parse_acl_list(text)
        if max_per_list > 0:
            entries = entries[:max_per_list]
        collected_surge.append(f"# --- {item['file']} ({len(entries)}) ---")
        collected_qx.append(f"# --- {item['file']} ({len(entries)}) ---")
        for kind, value in entries:
            s = to_surge(kind, value, item["surge_policy"])
            q = to_qx(kind, value, item["qx_policy"])
            if s:
                collected_surge.append(s)
            if q:
                collected_qx.append(q)

    collected_surge.extend(manifest.get("footer_surge") or [])
    collected_qx.extend(manifest.get("footer_qx") or [])
    surge_body = "\n".join(collected_surge) + "\n"
    qx_body = "\n".join(collected_qx) + "\n"

    for t in manifest["targets"]:
        path = ROOT / t["path"]
        dialect = t["dialect"]
        body = path.read_text(encoding="utf-8")
        baked = qx_body if dialect == "quantumultx" else surge_body
        path.write_text(replace_markers(body, baked), encoding="utf-8")
        print(f"wrote {path} ({len(baked.splitlines())} lines)")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--check", action="store_true", help="Validate BEGIN/END markers only")
    g.add_argument("--bake", action="store_true", help="Fetch ACL4SSR lists and rewrite marker sections")
    ap.add_argument(
        "--max-per-list",
        type=int,
        default=0,
        help="Cap entries per list (0 = unlimited). Use e.g. 200 for dry runs.",
    )
    args = ap.parse_args()
    manifest = load_manifest()
    if args.check:
        return check_markers(manifest)
    return bake(manifest, args.max_per_list)


if __name__ == "__main__":
    raise SystemExit(main())
