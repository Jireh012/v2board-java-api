# v2node Upstream Reference

> Panel-side pointer: where to compare node daemon changes. **Canonical node Trellis lives in the v2node repo.**

---

## Canonical locations

| Concern | Location |
|---------|----------|
| Node Trellis specs (full) | Sibling workspace `v2node/.trellis/spec/node/` · remote [Jireh012/v2node](https://github.com/Jireh012/v2node) |
| Upstream compare/merge URL | **https://github.com/wyx2685/v2node** (`main`) |
| Deploy/install URL | **https://github.com/Jireh012/v2node** (`main` + Releases) |
| Panel wire (this API) | [server-node.md](./server-node.md) |

Relative GitHub: when working only in this API repo, open [wyx2685/v2node](https://github.com/wyx2685/v2node) for对照更新; implement/merge in the v2node workspace and follow that repo’s Trellis.

---

## Convention (panel agents)

**What**: “对照更新 v2node” → use [wyx2685/v2node](https://github.com/wyx2685/v2node) `main` as the diff baseline; runtime install stays Jireh012.

**Do**:
- Prefer reading/editing specs under the **v2node** repo Trellis (`node/upstream.md`, `node/panel-wire.md`, `node/install-release.md`).
- Keep panel obfuscation contracts in [server-node.md](./server-node.md); do not duplicate long node-only matrices here.

**Don't**:
- Point panel `install_command` or ops docs at wyx2685 Releases.
- Treat another v2node fork as the compare baseline without updating Trellis in **both** repos.

---

## Design Decision: Spec ownership

**Context**: Node and panel are separate git repos; both need the upstream URL.

**Decision**: Full node coding specs + install/release contracts live in **v2node** `.trellis/spec/node/`. This file is a short pointer for panel-side checklists and guides.
