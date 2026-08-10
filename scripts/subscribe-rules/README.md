# Subscribe rule bake / check

Keep Surge / Surfboard / Loon / Quantumult X **inline** rules fresh without putting remote `RULE-SET` / `rule-providers` into user subscriptions.

## Commands

```bash
# Markers present + Clash GEOSITE allowlist
python3 scripts/subscribe-rules/bake_inline_rules.py --check
python3 scripts/subscribe-rules/check_clash_geosite.py

# Optional: presence check against a local geosite.dat
python3 scripts/subscribe-rules/check_clash_geosite.py --geosite-dat /path/to/geosite.dat

# Monthly refresh: pull ACL4SSR Clash lists → rewrite between
# # BEGIN-ACL4SSR-BAKE … # END-ACL4SSR-BAKE
python3 scripts/subscribe-rules/bake_inline_rules.py --bake
```

After `--bake`, review the diff, run `--check` + unit tests, then commit.

## Notes

- Clash / Stash keep `GEOSITE` / `GEOIP` (see `geosite-allowlist.txt`); do not convert Clash to thousands of DOMAIN lines.
- Sync URL in admin must point at **already-localized** full templates; Online Full with remote providers will be sanitized away.
- Manifest: `bake-manifest.json` (`base_url` + list → policy mapping).
