# PHP Upstream Reference

> Canonical PHP V2Board source for compatibility work on this Java API (and sibling `v2board-ui`).

---

## Convention: Upstream repository

**What**: Treat **[wyx2685/v2board](https://github.com/wyx2685/v2board)** (`master`) as the only default PHP reference when aligning behavior, config keys, API contracts, Redis/PHP serialize formats, or admin system-config semantics.

**Why**: This Java panel coexists with / migrates from that fork (V2bX / v2node-oriented). Using `v2board/v2board` or other forks causes silent drift (missing keys, different event IDs, UniProxy shapes).

**Do**:
- Before implementing or auditing a PHP-compatible feature, open the matching files under that repo (`app/Http/Controllers`, `app/Services`, `config`, `app/Jobs`, routes).
- Prefer clone/sparse checkout locally for diffs; cite paths relative to that tree in task PRDs (`PHP: AuthController::login`).
- Keep intentional typos / legacy key names from that tree (e.g. `deposit_bounus`) unless product explicitly renames with a migration plan.

**Don't**:
- Default to `https://github.com/v2board/v2board` or undocumented third-party forks.
- Invent config keys or response fields “like PHP” without checking this upstream first.
- Assume admin UI copy in our Vue app matches PHP labels without verifying the PHP controller/config.

**Related**: [database-guidelines.md](./database-guidelines.md), [system-config.md](./system-config.md), [server-node.md](./server-node.md), [v2node-upstream.md](./v2node-upstream.md) (node daemon compare), sibling UI Trellis when present.

---

## Workflow checklist

When a task says “align with PHP” / “对照原版”:

1. Resolve the PHP entrypoint in **wyx2685/v2board** (controller method + any Service/Job).
2. Note DB columns, Redis key prefixes, nested `v2board` / system-config group keys, and error message strings.
3. Implement Java readers via `ConfigService` nested getters (never flat top-level guesses).
4. Add or extend unit tests that lock the PHP-compatible contract (units: cents, traffic bytes/GB, 0/1 toggles).
5. Document any **intentional** divergence in the task PRD and the relevant topic code-spec.

---

## Design Decision: Single canonical fork

**Context**: Multiple V2Board forks diverge on node API, invite, Telegram, and config layout.

**Decision**: Canonical reference = `https://github.com/wyx2685/v2board` @ `master`. Change only if the user explicitly designates a different upstream in Trellis.

---

## Aligned (2026-08)

| Feature | PHP | Java |
|---------|-----|------|
| Remote TLS cert | `V2nodeController::save` `cert_mode=remote` | `Helper.ensureRemoteTlsCertificate` |
| Subscribe `pcs=` | `Helper::build*Uri` | `Helper` + `GeneralHandler` (Clash YAML 不加) |
| Paytaro | `App\Payments\Paytaro` first in methods; keep MGate | `PaytaroDriver` + methods 置顶 |
| Cancel CAS | `UPDATE status=2 WHERE id=? AND status=0` | `OrderService.cancel` `LambdaUpdateWrapper` |
