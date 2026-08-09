# Design: 第三方订阅名称过滤

## Storage

`v2_external_subscribe_source` 新增列：

```sql
`name_filters` json DEFAULT NULL COMMENT '[{pattern,replacement,regex}]'
```

手写 DDL 追加到 `src/main/resources/db/v2_external_subscribe.sql`（及一份 `ALTER` 片段注释或同文件附注），线上手工执行。

实体 `ExternalSubscribeSource.nameFilters`：`String`（JSON 文本）或 `List` + JacksonTypeHandler；与现有 PHP JSON 列风格对齐即可（本表 Java 独占，可用 TypeHandler）。

API 契约（snake_case）：

```json
"name_filters": [
  { "pattern": "abc", "replacement": "", "regex": false },
  { "pattern": "linux\\.do", "replacement": "LD", "regex": true }
]
```

- `fetch` / `save` 读写该字段。
- 空数组 / null 表示无规则。

## Apply pipeline

```
parse → (optional) apply name filters to CanonicalExternalNode
      → probe → upsert (name / outbound.tag / share_uri already filtered)
```

Helper：`ExternalNameFilter.apply(String name, List<Rule> rules)`  
在 `upsertNode` 前对 `canonical.name` 应用；再 `applyDisplayName` 到 outbound + share_uri（复用 `ExternalNodeIdentity.applyDisplayName` 思路或小工具）。

顺序：规则数组顺序；每条 `replaceAll`（字面量先 `Pattern.quote`）。

过滤后若 name 变为空白：回退为原名或 `protocol-server-port`（design 定：**回退原名**，避免空 tag）。

## Validation (save)

- pattern 必填非 blank。
- `regex==true`：`Pattern.compile(pattern)`，失败 → `BusinessException(500, "第 N 条过滤规则正则无效: …")`。
- replacement 允许 null→存 `""`。
- 规则条数上限建议 50（防滥用）。

## Frontend (`v2board-ui`)

编辑弹窗增加「名称过滤」列表：

- 每行：pattern 输入、replacement 输入（placeholder「可留空=删除」）、「正则」checkbox、删除。
- 「添加规则」按钮。
- `openAdd`/`openEdit`/`doSave` / API 类型同步 `name_filters`。

## Compatibility

- 旧源无列 / null：行为不变。
- 与逻辑键去重：名称过滤不影响 fingerprint；同名编号仍在下发侧对过滤后名称生效。

## Rollback

去掉列读写与 UI；同步不再调用 filter。DB 列可保留。
