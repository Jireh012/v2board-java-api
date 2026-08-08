# Design: 面板抗探测与防封锁

## Architecture / Boundaries

| 层 | 改动 |
|----|------|
| Docs | 新建 `docs/ops-panel-anti-block.md`；README 链入 |
| API `ConfigService.save` | 校验 `site.subscribe_path`（对齐 `validateSecurePathInSaveBody` 模式） |
| API 支付 | `PaymentService` / `WechatPayNativeDriver` 默认商品名走 `getAppName()` |
| UI admin | `AdminSystemConfigView` 订阅路径 `desc` / placeholder 提示 |
| UI shell | `index.html` + `public/` 中性 favicon |
| Spec | 补 `subscribe-delivery.md` 校验规则；可选一句链到 ops 文档 |

不改：`SubscribeRouteRegistrar` 热更新机制（已满足 AC3 核心）；`/api/v1` 路由树；公开 SM4 字段集。

## Contracts

### subscribe_path 校验

- 输入：save body `site.subscribe_path`（若 key 存在）。
- 空 / 仅空白 → 允许写入空或规范化空，运行时 `getSubscribePath()` 回落默认。
- 非空：
  - normalize：trim，保证以 `/` 开头，去掉尾部多余 `/`（根 `/` 除外——根路径应拒绝）。
  - regex：`^/[A-Za-z0-9._~/-]+$`，且不含 `..`。
  - 长度上限（建议 ≤ 128）。
  - 不得 equalsIgnoreCase 或前缀撞车保留列表（见 PRD）。
- 失败：`BusinessException(500, "订阅路径不合法：…")`（风格对齐 secure_path）。

### 支付默认文案

```
fallback = configService.getAppName() + " - 订阅"
```

微信 driver 需能拿到 appName：构造时注入 `ConfigService`，或由 `PaymentService` 传入 subject/body（优先少改驱动接口：在调用处传入已解析名称）。

### 前端壳

- `<title>`：中性短词（如站点占位「Panel」或空格策略——选 **「Panel」**，避免空白标题怪异）。
- favicon：`/favicon.svg` 几何单色图标，删对 `vite.svg` 的依赖（可保留文件但不引用）。

## Data flow

```
Admin save site.subscribe_path
  → ConfigService.validateSubscribePathInSaveBody
  → persist JSON
  → SubscribeRouteRegistrar.refresh()  // existing
  → old mapping unregistered
```

```
Pay create
  → product_name empty?
  → getAppName() + " - 订阅"
  → gateway API
```

## Compatibility

- 空 `subscribe_path` 与现网默认一致。
- 已自定义路径的部署：仅在下次保存时触发更严校验；若已存非法值，运行时仍按现状工作，直到管理员再保存（可选：启动时不强制迁移）。
- PHP 面板同库：字段名仍为 `site.subscribe_path`。

## Trade-offs

| 选项 | 取舍 |
|------|------|
| 禁止保存默认路径 | 更强抗扫描，但升级/误触保存可能强迫改路径 → **不禁止，仅 UI 劝阻** |
| 改全量 API 前缀 | 抗扫描更强 → **Out of scope (C)** |
| 运维写进 `.trellis/spec` | 偏编码规范；抗封锁属部署 → **放 `docs/`** |

## Rollback

- 回滚代码即可；文档可留。
- 若校验过严误伤合法路径：放宽 regex / 保留列表后发补丁；DB 中路径不因校验失败被清空。
