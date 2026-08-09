# Implement: 第三方订阅逻辑键去重

## Checklist

1. [x] 在 `ExternalNodeIdentity` 抽出 `logicalKey` / 基于逻辑键的 `fingerprint`
2. [x] parse→dedupe 使用新 fingerprint；解析序第一条保留
3. [x] `listReachableAsServerMaps`：跨源 logicalKey 去重（`sort/id` 序第一条）
4. [x] 同名编号 + 同步 `name` / `clash_proxy.name` / `singbox_outbound.tag` / `share_uri`
5. [x] 单元测试：`ExternalNodeIdentityTest` + `ExternalSubscribeDedupeTest`（8 tests）
6. [x] `mvn -Dtest=ExternalNodeIdentityTest,ExternalSubscribeDedupeTest test` PASS（需 JDK 17）

## Validation

```bash
mvn -Dtest=ExternalSubscribeDedupeTest,ExternalSubscribeParser* test
# 若测试类命名不同，按实际类名调整
```

手动（可选）：触发 admin sync 后查 `COUNT(DISTINCT fingerprint)` 同源应 ≈ 逻辑节点数；用户订阅 external 条数应接近跨源去重后规模。

## Review gates

- [ ] 编号在 `⚠️ ` 之前
- [ ] Admin `listBySourceId` 未被误改折叠
- [ ] 不引入源优先级配置

## Rollback

Revert parser fingerprint + NodeService 去重/编号提交；再跑一次 sync-all。
