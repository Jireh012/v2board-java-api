# Implement: 第三方订阅名称过滤

## Checklist

### Backend (`v2board-java-api`)

1. [x] DDL：`name_filters` JSON 列 + 更新 `v2_external_subscribe.sql`（线上已 ALTER）
2. [x] Model / save / fetch 读写 `name_filters`；保存校验正则与空 pattern
3. [x] `ExternalNameFilter` + `ExternalNameFilterTest`
4. [x] `ExternalSubscribeSyncService`：parse 后应用过滤并同步 tag/share_uri
5. [x] 更新 `.trellis/spec/backend/external-subscribe.md`

### Frontend (`v2board-ui`)

6. [x] `externalSubscribe.ts` 类型与 save body
7. [x] `AdminExternalSubscribeView` 编辑弹窗规则编辑器
8. [ ] 手动点验：保存非法正则被拒；同步后节点名变化

## Validation

```bash
JAVA_HOME=.../corretto-17 mvn -Dtest=ExternalNameFilterTest test
```

## Review gates

- [x] 过滤不影响 logicalKey / fingerprint
- [x] replacement 空串可保存
- [x] 保存拒绝非法正则
