# Thinking Guides

> **Purpose**: Expand your thinking to catch things you might not have considered.

---

## Why Thinking Guides?

**Most bugs and tech debt come from "didn't think of that"**, not from lack of skill:

- Didn't think about what happens at layer boundaries → cross-layer bugs
- Didn't think about code patterns repeating → duplicated code everywhere
- Didn't think about edge cases → runtime errors
- Didn't think about future maintainers → unreadable code

These guides help you **ask the right questions before coding**.

---

## Available Guides

| Guide | Purpose | When to Use |
|-------|---------|-------------|
| [Commit Message](./commit-message.md) | `type(scope):` + 中文摘要/正文 | Phase 3.4 起草 / 执行 commit |
| [Code Reuse Thinking Guide](./code-reuse-thinking-guide.md) | Identify patterns and reduce duplication | When you notice repeated patterns |
| [Cross-Layer Thinking Guide](./cross-layer-thinking-guide.md) | Think through data flow across layers | Features spanning multiple layers |

PHP 对照源码（code-spec）：[php-upstream.md](../backend/php-upstream.md) → **https://github.com/wyx2685/v2board**

v2node 对照上游（code-spec）：[v2node-upstream.md](../backend/v2node-upstream.md) → **https://github.com/wyx2685/v2node**（部署/安装仍用 Jireh012）

---

## Quick Reference: Thinking Triggers

### When Committing

- [ ] Message = `type(scope): 中文摘要` + 空行 + 中文正文 → [commit-message.md](./commit-message.md)
- [ ] 勿用纯中文无前缀，或英文摘要覆盖本仓库约定

### When Aligning with PHP (“对照原版”)

- [ ] Use **only** [wyx2685/v2board](https://github.com/wyx2685/v2board) `master` as the PHP reference → [php-upstream.md](../backend/php-upstream.md)
- [ ] Do **not** default to `v2board/v2board` or other forks unless the user explicitly changes Trellis

### When Aligning / Merging v2node (“对照更新”)

- [ ] Compare against **only** [wyx2685/v2node](https://github.com/wyx2685/v2node) `main` → [v2node-upstream.md](../backend/v2node-upstream.md)
- [ ] Do **not** use upstream Releases for this panel’s install; deploy stays [Jireh012/v2node](https://github.com/Jireh012/v2node) → [server-node.md](../backend/server-node.md)

### When to Think About Cross-Layer Issues

- [ ] Feature touches 3+ layers (API, Service, Component, Database)
- [ ] Data format changes between layers
- [ ] Multiple consumers need the same data
- [ ] You're not sure where to put some logic
- [ ] You are adding an event kind, JSONL record, RPC payload, or config field
- [ ] UI / command code starts casting raw payload fields directly
- [ ] Reading PHP-written JSON columns into typed Java lists (`group_id`, etc.) → [database-guidelines.md](../backend/database-guidelines.md)
- [ ] Persisting job status in DB while locking in memory → [external-subscribe.md](../backend/external-subscribe.md)
- [ ] Building user `subscribe_url` or marking panel/external node names → [subscribe-delivery.md](../backend/subscribe-delivery.md)
- [ ] Panel anti-probe / domain switch ops → repo [`docs/ops-panel-anti-block.md`](../../../docs/ops-panel-anti-block.md) + save-time `subscribe_path` rules in subscribe-delivery
- [ ] Public passport flags (`email_verify` / `safe_mode_enable`) or SMTP from admin config → [public-site-config.md](../backend/public-site-config.md) + [passport-email.md](../backend/passport-email.md) (UI: sibling `v2board-ui` `site-brand.md`)
- [ ] Telegram webhook / bind / ticket notify → [telegram.md](../backend/telegram.md); public `telegram_discuss_link` stays on SM4 public config (never bot token)
- [ ] Changing public config / panel SM4 / action aliases / payment notify prefix → [public-site-config.md](../backend/public-site-config.md) + [panel-api-sm4.md](../backend/panel-api-sm4.md); `SM4_KEY` ↔ `VITE_SM4_KEY`; catalog new actions; payment stays plaintext `{prefix}/{method}/{uuid}`
- [ ] Changing node ApiPrefix / query `e` / one-click install → [server-node.md](../backend/server-node.md) (UI has no node key; install needs Jireh012 **Release** + API `--api-host`)

→ Read [Cross-Layer Thinking Guide](./cross-layer-thinking-guide.md)

### When to Think About Code Reuse

- [ ] You're writing similar code to something that exists
- [ ] You see the same pattern repeated 3+ times
- [ ] You're adding a new field to multiple places
- [ ] **You're modifying any constant or config**
- [ ] **You're creating a new utility/helper function** ← Search first!
- [ ] Two files read the same untyped payload field with local casts
- [ ] Multiple branches update the same derived state from `kind` / `action`

→ Read [Code Reuse Thinking Guide](./code-reuse-thinking-guide.md)

### When Verifying AI Cross-Review Results

- [ ] Reviewer claims "user input can be malicious" → Check the actual data source (internal manifest? user config? external API?)
- [ ] Reviewer flags "missing validation" → Is the data from a trusted internal source?
- [ ] Reviewer says "behavior change" → Read the code comments — is it intentional design?
- [ ] Reviewer identifies a "bug" in test → Mentally delete the feature being tested — does the test still pass? If yes → tautological test

**Common AI reviewer false-positive patterns**:
1. **Trust boundary confusion**: Treating internal data (bundled JSON manifests) as untrusted external input
2. **Ignoring design comments**: Flagging intentional behavior documented in code comments as bugs
3. **Variable misreading**: Not tracing a variable to its actual definition (e.g., Map keyed by path vs name)

**Verification rule**: Every CRITICAL/WARNING finding must be verified against the actual code before prioritizing. Budget ~35% false-positive rate for AI reviews.

---

## Pre-Modification Rule (CRITICAL)

> **Before changing ANY value, ALWAYS search first!**

```bash
# Search for the value you're about to change
grep -r "value_to_change" .
```

This single habit prevents most "forgot to update X" bugs.

---

## How to Use This Directory

1. **Before coding**: Skim the relevant thinking guide
2. **During coding**: If something feels repetitive or complex, check the guides
3. **After bugs**: Add new insights to the relevant guide (learn from mistakes)

---

## Contributing

Found a new "didn't think of that" moment? Add it to the relevant guide.

---

**Core Principle**: 30 minutes of thinking saves 3 hours of debugging.
