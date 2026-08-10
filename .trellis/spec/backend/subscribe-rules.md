# Subscribe Rule Templates

> Executable contracts for ACL4SSR-style client rule templates: storage, Redis cache, sanitize pipeline, and builder resolve order.

---

## Scenario: Resolve template (Redis → DB → classpath)

### 1. Scope / Trigger

- Trigger: Protocol handlers (`clash` / `meta` / `verge` / `nyanpasu` / `stash` / `surge` / `surfboard` / `sing-box` / `qx` / `loon`) build subscribe output and must load a rule template.
- Cross-layer: Admin UI → `AdminSubscribeRuleController` → `RuleTemplateService` → MySQL `v2_subscribe_rule_template` + Redis → Builders.

### 2. Signatures

**RuleTemplateService** (`com.v2board.api.service.RuleTemplateService`):

```java
String resolve(String format);                    // Redis → DB → classpath seed (full); or profile seed
Map<String, Object> fetch(String format);         // admin view (content + meta)
Map<String, Object> save(String format, String content, String sourceUrl, String updateSource);
Map<String, Object> sync(String format, String url);
Map<String, Object> restore(String format);       // delete DB row + invalidate cache
static void bindRequestProfile(String profile);   // full|simple|nodes for current request
static void clearRequestProfile();
```

**Formats** (PK `format`): `clash` | `stash` | `surge` | `surfboard` | `singbox` | `quantumultx` | `loon`.

Aliases normalized at resolve: `meta`/`verge`/`nyanpasu` → `clash`; `sing-box` → `singbox`.

**Rule profiles** (`?rule=` or DB `subscribe.rule_profile`):

| Profile | Resolve |
|---------|---------|
| `full` (default) | Redis → DB → `rules/default.*` |
| `simple` | classpath only `rules/simple.*` |
| `nodes` | classpath only `rules/nodes.*` |

Admin save/sync edits **full** only. Response header: `subscription-rule-profile`.

### 3. Contracts

| Piece | Behavior |
|-------|----------|
| Redis key | `{prefix}subscribe:rule:{format}` (TTL 24h) |
| Resolve order (`full`) | Redis hit → DB row content → classpath `rules/default.*` |
| Stash | Prefer `stash` row / `default.stash.yaml`; else fall back to clash resolve |
| Sing-box old (`flag=sing`) | Always classpath `default.sing-box.old.json` (admin custom covers ≥1.12 only) |
| Write path | Sanitize → upsert DB → `DEL` cache → re-set cache with new content |
| Restore | `DELETE` by format → invalidate → next resolve uses seed |
| Sync product note | Target must be localized full templates; Online rule-providers are stripped / seed-fallback; response may include `stripped_remote`, `used_seed_fallback`, `sync_hint` |

**Classpath seeds**

| format | resource |
|--------|----------|
| clash | `rules/default.clash.yaml` |
| stash | `rules/default.stash.yaml` (optional; else clash) |
| surge | `rules/default.surge.conf` |
| surfboard | `rules/default.surfboard.conf` |
| singbox | `rules/default.sing-box.json` |
| quantumultx | `rules/default.quantumultx.conf` |
| loon | `rules/default.loon.conf` |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Unknown format | `BusinessException(500, "不支持的规则格式：…")` |
| Empty content on save | `BusinessException(500, "规则内容不能为空")` / controller `content 不能为空` |
| Sync URL empty and no stored `source_url` | `BusinessException(500, "同步 URL 不能为空")` |
| Fetch upstream fails | `BusinessException(500, "拉取上游规则失败：…")` |
| After sanitize still has remote rule deps and seed also poisoned | `BusinessException(500, …拒绝保存)` |
| Table missing / select throws | Log warn; resolve falls through to classpath seed |

### 5. Good / Base / Bad Cases

- **Good**: Admin saves clash YAML → Redis invalidated → next Clash subscribe uses custom content immediately.
- **Base**: Empty table → all clients use classpath ACL4SSR seeds (no remote rule-providers / rule_set).
- **Bad**: Builder reads classpath only and ignores DB after admin edit.

### 6. Tests Required

- Unit: `RuleTemplateServiceTest` — Redis prefer / DB cache / classpath fallback / stash→clash / save sanitize+invalidate / restore / sync.
- Unit: Builders accept `buildFromContent` with resolved template.

### 7. Wrong vs Correct

#### Wrong

```java
String yaml = loadClasspath("rules/default.clash.yaml"); // ignores admin custom
```

#### Correct

```java
String template = ruleTemplateService.resolve("clash");
return ClashMetaBuilder.buildFromContent(servers, uuid, appName, template);
```

---

## Scenario: SanitizePipeline (rewrite B)

### 1. Scope / Trigger

- Trigger: Admin **save** or **sync** of a rule template.
- Goal: Client subscribe output must not require fetching remote rule lists (`rule-providers`, remote `rule_set`, `RULE-SET,https://…`, GitHub raw / jsDelivr / ghproxy mirrors).

### 2. Signatures

**RuleTemplateSanitizer** (`com.v2board.api.service.RuleTemplateSanitizer`):

```java
record Result(String content, String warning) {}
static Result sanitize(String format, String content, String seedContent);
static boolean containsRemoteRuleDependency(String content);
```

### 3. Contracts

| Format | Pipeline |
|--------|----------|
| clash / stash | Parse YAML → remove `rule-providers` → drop `RULE-SET,` / remote URLs in `rules` → strip http `proxy-providers` → if rules/groups unusable, fill from seed → re-check; else whole-seed fallback |
| surge / surfboard / quantumultx / loon | Line-strip remote RULE-SET / DOMAIN-SET URLs; if unusable → seed fallback |
| singbox | Reject / replace when `"type":"remote"` rule_set or GitHub-style rule URLs remain; prefer seed fallback over partial JSON surgery |

**Allowed remote URLs in seeds** (not rule lists): url-test / DoH / connectivity check / Surge `geoip-maxmind-url` (client geo DB). These are not remote *rule* dependencies.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Upstream has GitHub `rule-providers` | Stripped; rules filled from seed if needed; `warning` set |
| Manual paste still has remote rule URL after strip | Seed fallback or reject |
| Seed itself contains remote rule deps | Reject save |

### 5. Tests Required

- `RuleTemplateSanitizerTest` — strip providers + RULE-SET; fill from seed; reject poisoned seed; generic surge strip.

### 6. Wrong vs Correct

#### Wrong

```yaml
rule-providers:
  Ads:
    type: http
    url: https://raw.githubusercontent.com/.../BanAD.list
rules:
  - RULE-SET,Ads,REJECT
```

#### Correct

```yaml
# no rule-providers
rules:
  - GEOSITE,category-ads-all,🛑 广告拦截
  - MATCH,🐟 漏网之鱼
```

---

## Scenario: Builder merge semantics

### 1. Scope / Trigger

- Trigger: Merging user nodes into ACL4SSR proxy / outbound groups.

### 2. Contracts

| Group kind | Behavior |
|------------|----------|
| Empty proxies / outbounds (or tag `#…` in sing-box) | Fill with **all** node names |
| Contains regex filter (region / Netflix name patterns) | Fill matches only; empty region groups may be removed |
| Policy-only (references other groups / DIRECT / REJECT) | **Do not** append all nodes |

Shared helpers: `ClashMetaBuilder.mergeProxyGroup`, `SingboxBuilder.addProxies`, `ConfTemplatePlaceholders.applyProxyGroups` (Surge/Surfboard/QX/Loon placeholders `$proxy_group` / `$proxy_group_{hk,tw,…}`).

### 3. Client-local rules (R2)

Subscribe products must not require clients to download remote rule lists. Prefer Meta `GEOSITE`/`GEOIP`, Sing-box local geosite/geoip or inline `domain_suffix`, and inline DOMAIN rules for Surge/QX/Loon.

---

## Admin API

| Method | Path | Body / query |
|--------|------|----------------|
| GET | `/api/v1/admin/subscribe-rule/fetch` | `?format=` |
| POST | `/api/v1/admin/subscribe-rule/save` | `{ format, content, source_url? }` |
| POST | `/api/v1/admin/subscribe-rule/sync` | `{ format, url? \| source_url? }` |
| POST | `/api/v1/admin/subscribe-rule/restore` | `{ format }` |

Panel SM4 aliases registered in `PanelApiActionCatalog` (`subscribe-rule/*`).

UI: `v2board-ui` route `servers/subscribe-rules` (`AdminSubscribeRuleView`).

---

## Storage DDL

Manual apply: `src/main/resources/db/v2_subscribe_rule_template.sql`.

| Column | Notes |
|--------|-------|
| `format` | PK |
| `content` | LONGTEXT template body |
| `source_url` | last sync URL |
| `update_source` | `manual` / `sync` / `default` (response) |
| `updated_at` / `created_at` | unix seconds |

---

## Design Decision: Stash shares Clash by default

**Context**: Stash clients consume Clash Meta YAML.

**Decision**: Optional independent `stash` row; otherwise resolve falls back to clash content/seed so one edit covers both unless operators override stash.

---

## Maintenance: bake inline rules + Clash GEOSITE allowlist

- Markers `# BEGIN-ACL4SSR-BAKE` / `# END-ACL4SSR-BAKE` in Surge / Surfboard / Loon / Quantumult X **full** seeds.
- Scripts: `scripts/subscribe-rules/bake_inline_rules.py` (`--check` / `--bake`), `check_clash_geosite.py`, allowlist `rules/geosite-allowlist.txt`.
- Do **not** put remote rule-providers into subscribe output; refresh by baking into classpath seeds (or admin full template).
