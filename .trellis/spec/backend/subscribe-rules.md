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
| Sync product note | Prefer ACL4SSR Online Full NoAuto INI (default URL); server expands `.list` / HTTP `rule-providers` into **inline** templates (seed shell kept). Response may include `stripped_remote`, `used_seed_fallback`, `sync_hint` (`已从 Online/raw 内联本地化…`). Success path should **not** whole-seed-fallback solely because upstream was Online. |

**Classpath seeds (`full`)**

| format | resource |
|--------|----------|
| clash | `rules/default.clash.yaml` |
| stash | `rules/default.stash.yaml` (optional; else clash) |
| surge | `rules/default.surge.conf` |
| surfboard | `rules/default.surfboard.conf` |
| singbox | `rules/default.sing-box.json` |
| quantumultx | `rules/default.quantumultx.conf` |
| loon | `rules/default.loon.conf` |

**Classpath seeds (`simple` / `nodes`)**: `rules/{simple\|nodes}.clash.yaml` (stash shares clash), `*.surge.conf`, `*.surfboard.conf`, `*.quantumultx.conf`, `*.loon.conf`, `*.sing-box.json`.

> **Cache gotcha**: Only **DB custom** templates are written to Redis. Classpath seeds must **not** be cached (TTL 24h would pin stale seeds across jar deploys).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Unknown format | `BusinessException(500, "不支持的规则格式：…")` |
| Empty content on save | `BusinessException(500, "规则内容不能为空")` / controller `content 不能为空` |
| Sync URL empty | Use stored `source_url`, else default Online INI `ACL4SSR_Online_Full_NoAuto.ini` |
| Any ruleset `.list` / provider fetch fails | `BusinessException(500, "拉取规则列表失败：{url}: …")`; no persist |
| Fetch upstream fails | `BusinessException(500, "拉取上游规则失败：…")` |
| After sanitize still has remote rule deps and seed also poisoned | `BusinessException(500, …拒绝保存)` |
| Table missing / select throws | Log warn; resolve falls through to classpath seed |
| Unknown `?rule=` value | Treated as `full` (`normalizeProfile`) |

### 5. Good / Base / Bad Cases

- **Good**: Admin saves clash YAML → Redis invalidated → next Clash subscribe uses custom content immediately.
- **Base**: Empty table → all clients use classpath ACL4SSR seeds (no remote rule-providers / rule_set).
- **Bad**: Builder reads classpath only and ignores DB after admin edit; or Redis-caches classpath seeds so deploy never picks new defaults.

### 6. Tests Required

- Unit: `RuleTemplateServiceTest` — Redis prefer / DB cache / classpath fallback / stash→clash / save sanitize+invalidate / restore / sync / simple+nodes ignore DB / `normalizeProfile`.
- Unit: Builders accept `buildFromContent` with resolved template.

### 7. Wrong vs Correct

#### Wrong

```java
String yaml = loadClasspath("rules/default.clash.yaml"); // ignores admin custom
cacheService.set(key, classpathSeed, 24, HOURS);       // pins stale seed after jar update
```

#### Correct

```java
String template = ruleTemplateService.resolve("clash");
return ClashMetaBuilder.buildFromContent(servers, uuid, appName, template);
// resolve: cache only DB content; classpath seed returned without SET
```

---

## Scenario: Rule profile selection (`full` / `simple` / `nodes`)

### 1. Scope / Trigger

- Trigger: Subscribe clients need lighter or nodes-only rules without admin editing every format.
- Cross-layer: `GET {subscribe_path}?token=&flag=&rule=` → `ClientController` → `RuleTemplateService` ThreadLocal → builders; default from DB `subscribe.rule_profile`.

### 2. Signatures

```java
// ClientController.subscribe — read rule from query only (see subscribe-delivery route reflection)
String rule = request.getParameter("rule");
String profile = hasText(rule) ? rule : configService.getSubscribeRuleProfile();
RuleTemplateService.bindRequestProfile(profile);
try { ... handlers ... response.setHeader("subscription-rule-profile", currentRequestProfile()); }
finally { RuleTemplateService.clearRequestProfile(); }

// ConfigService
String getSubscribeRuleProfile(); // subscribe.rule_profile → normalizeProfile; default "full"

// RuleTemplateService
static void bindRequestProfile(String profile);
static void clearRequestProfile();
static String currentRequestProfile();
static String normalizeProfile(String profile); // null/blank/unknown → full; "node" → nodes
```

### 3. Contracts

| Piece | Behavior |
|-------|----------|
| Priority | query `rule` > DB/yml `subscribe.rule_profile` > `full` |
| `full` | Redis → DB → `rules/default.*` (admin editable) |
| `simple` / `nodes` | Classpath only; admin DB row ignored |
| Response header | `subscription-rule-profile: full\|simple\|nodes` |
| Aliases | `node` → `nodes`; case-insensitive |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Missing profile seed file | `BusinessException(500, "未找到规则档位 …")` |
| Invalid profile string | Silently `full` |
| Admin edits while users use `?rule=simple` | Users keep classpath simple; full DB custom unaffected |

### 5. Good / Base / Bad Cases

- **Good**: `?rule=simple` → Clash YAML has ads/CN/GFW rules, no Netflix group rules; header `simple`.
- **Base**: No `rule` param → `full` ACL4SSR template.
- **Bad**: Add `@RequestParam String rule` on `subscribe` (breaks dynamic route).

### 6. Tests Required

- `RuleTemplateServiceTest.resolve_simpleAndNodesProfiles_useClasspathNotDb`
- `RuleTemplateServiceTest.normalizeProfile_aliasesAndFallback`
- Smoke: curl with `&rule=simple` → header + body contains `GEOSITE,gfw`

### 7. Wrong vs Correct

#### Wrong

```java
// Admin DB custom applied to every profile
return resolveDirect(fmt); // ignores ThreadLocal profile
```

#### Correct

```java
if (!PROFILE_FULL.equals(currentRequestProfile())) {
    return resolveProfileSeed(fmt, currentRequestProfile());
}
return resolveDirect(fmt);
```
---

## Scenario: SanitizePipeline (rewrite B)

### 1. Scope / Trigger

- Trigger: Admin **save** or **sync** of a rule template.
- Goal: Client subscribe output must not require fetching remote rule lists (`rule-providers`, remote `rule_set`, `RULE-SET,https://…`, GitHub raw / jsDelivr / ghproxy mirrors).

### 2. Signatures

**RuleTemplateSanitizer** (`com.v2board.api.service.RuleTemplateSanitizer`):

```java
record Result(String content, String warning, boolean strippedRemote, boolean usedSeedFallback) {}
static Result sanitize(String format, String content, String seedContent);
static boolean containsRemoteRuleDependency(String content);
```

**RuleTemplateService.save / sync** enrich admin response with:

| Field | Type | Meaning |
|-------|------|---------|
| `warning` | string? | Human-readable sanitize note |
| `stripped_remote` | boolean | Remote rule deps removed |
| `used_seed_fallback` | boolean | Fell back to classpath seed |
| `sync_hint` | string (sync only) | e.g. 已从 Online/raw 内联本地化… |

**Sync expand (before sanitize)** — `RuleTemplateService.prepareSyncContent`:

| Upstream | Behavior |
|----------|----------|
| ACL4SSR Online INI (`ruleset=` + `custom_proxy_group=`) | `Acl4ssrIniParser` → fetch all `.list` (fail-fast) → `Acl4ssrTemplateMaterializer` into format dialect (seed shell kept) |
| Clash/Stash YAML with HTTP `rule-providers` | `ClashRuleProviderExpander` inlines providers → optional merge seed shell |
| Other | Pass through to sanitize |

Default sync URL: `https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/config/ACL4SSR_Online_Full_NoAuto.ini`.

### 3. Contracts

| Format | Pipeline |
|--------|----------|
| clash / stash | Parse YAML → remove `rule-providers` → drop `RULE-SET,` / remote URLs in `rules` → strip http `proxy-providers` → if rules/groups unusable, fill from seed → re-check; else whole-seed fallback |
| surge / surfboard / quantumultx / loon | Line-strip remote RULE-SET / DOMAIN-SET URLs; if unusable → seed fallback |
| singbox | Reject / replace when `"type":"remote"` rule_set or GitHub-style rule URLs remain; prefer seed fallback over partial JSON surgery |

**Allowed remote URLs in seeds** (not rule lists): url-test / DoH / connectivity check / Surge `geoip-maxmind-url` (client geo DB). These are not remote *rule* dependencies.

**Health-check URL**: Clash Meta / Mihomo recommends **HTTPS** for `proxy-groups[].url` and provider `health-check.url`. Seeds and `Acl4ssrTemplateMaterializer` must use `https://www.gstatic.com/generate_204` (not `http://`). `RuleTemplateSanitizer.rewriteHealthCheckToHttps` / `normalizeHealthCheckUrls` also rewrite on sanitize, and `RuleTemplateService.resolve` rewrites again so legacy Redis/DB templates stop emitting HTTP without requiring admin restore.

**Sync fetch**: `RuleTemplateService.fetchUpstream` → `ExternalSubscribeSyncService.fetchWithDirectThenPreProxy`: try direct first; on failure auto-try up to 8 reachable third-party subscribe nodes via local sing-box HTTP pre-proxy (no manual node picker). Requires `sing-box` on the API host and at least one `reachable=1` external node.

**Product**: Online INI / GitHub raw lists are **allowed as sync sources**; the server must expand them into fully inlined templates before persist. Sanitize remains the final gate so subscribe output never requires clients to fetch remote rule lists. Manual paste that still has remote deps after strip may seed-fallback or reject.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Sync Online INI + all lists OK | Inlined template persisted; `used_seed_fallback` false; may still set `sync_hint` |
| Upstream Clash YAML with HTTP `rule-providers` | Expanded inline then sanitize; not treated as “forbidden Online” |
| Manual paste still has remote rule URL after strip | Seed fallback or reject |
| Seed itself contains remote rule deps | Reject save |
| List/provider HTTP fails mid-sync | 500; no persist |

### 5. Good / Base / Bad Cases

- **Good**: Sync default Online INI for `clash` → DB content has many `DOMAIN-SUFFIX` lines, no `rule-providers` / `raw.githubusercontent.com`.
- **Base**: Empty URL → default Online INI; seed shell DNS/`[General]` retained.
- **Bad**: Strip Online without fetching lists and silently seed-fallback (old behavior).

### 6. Tests Required

- `RuleTemplateSanitizerTest` — strip providers + RULE-SET; fill from seed; reject poisoned seed; generic surge strip.
- `Acl4ssrIniParserTest` / `Acl4ssrTemplateMaterializerTest` / `ClashRuleProviderExpanderTest`.
- `RuleTemplateServiceTest.sync_*` — Online INI mock multi-URL; list failure no persist; `sync_hint` mentions 内联本地化.

### 7. Wrong vs Correct

#### Wrong

```yaml
# persist Online YAML without expanding providers
rule-providers:
  Ads:
    type: http
    url: https://raw.githubusercontent.com/.../BanAD.list
rules:
  - RULE-SET,Ads,REJECT
```

#### Correct

```yaml
# no rule-providers — lists inlined server-side
rules:
  - DOMAIN-SUFFIX,adservice.google.com,🛑 广告拦截
  - MATCH,🐟 漏网之鱼
```

> **GEOSITE gotcha** (classpath seeds / manual GEOSITE edits): Use tags present in Meta default Loyalsoldier `geosite.dat`. Forbidden example: `category-ad` (use `category-ads-all`). Allowlist: `rules/geosite-allowlist.txt` + `scripts/subscribe-rules/check_clash_geosite.py`. Online sync path uses classical DOMAIN/IP rules, not GEOSITE approximation.

> **INI `custom_proxy_group` gotcha**: For `url-test` / `fallback` / `load-balance`, Subconverter appends trailing `http(s)://…` (test URL) then numeric `interval` / `tolerance`. `Acl4ssrIniParser` must strip those tails before treating remaining tokens as group members — otherwise proxies lists get polluted with URLs and numbers. Covered by `Acl4ssrIniParserTest`.
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

### 1. Scope / Trigger

- Trigger: Surge/QX/Loon/Surfboard inline DOMAIN lists drift from ACL4SSR; Clash GEOSITE tags may not exist in client geodata.

### 2. Signatures

```bash
python3 scripts/subscribe-rules/bake_inline_rules.py --check   # markers non-empty
python3 scripts/subscribe-rules/bake_inline_rules.py --bake    # fetch ACL4SSR lists → rewrite markers
python3 scripts/subscribe-rules/check_clash_geosite.py [--geosite-dat PATH]
```

Manifest: `scripts/subscribe-rules/bake-manifest.json`. Markers: `# BEGIN-ACL4SSR-BAKE` … `# END-ACL4SSR-BAKE` in **full** conf seeds only.

### 3. Contracts

| Piece | Behavior |
|-------|----------|
| Clash / Stash | Keep `GEOSITE`/`GEOIP`; do not bake thousands of DOMAIN into Clash |
| Conf clients | Refresh only inside bake markers; `FINAL` / `$subs_domain` stay outside |
| Subscribe output | Never reintroduce remote `rule-providers` / `RULE-SET,https://` |

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Markers missing / section &lt; 20 lines | `--check` exit 1 |
| Clash GEOSITE not in allowlist | `check_clash_geosite.py` exit 1 |
| Fetch ACL4SSR fails during `--bake` | exit 1; leave files unchanged for that run |

### 5. Good / Base / Bad Cases

- **Good**: Monthly `--bake` + review diff + `--check` + commit.
- **Base**: `--check` green on CI/local without network.
- **Bad**: Point subscribe templates at GitHub raw lists to “auto update”.

### 6. Tests Required

- Script smoke: `--check` and `check_clash_geosite.py` exit 0 in repo.
- After Clash GEOSITE edit: update `geosite-allowlist.txt` in same change.

### 7. Wrong vs Correct

#### Wrong

```yaml
# clash seed
- GEOSITE,category-ad,🛑 广告拦截
```

#### Correct

```yaml
- GEOSITE,category-ads-all,🛑 广告拦截
# + allowlist entry category-ads-all
```
